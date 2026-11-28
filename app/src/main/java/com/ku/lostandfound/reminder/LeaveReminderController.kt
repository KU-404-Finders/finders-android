package com.ku.lostandfound.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.ku.lostandfound.MainActivity
import com.ku.lostandfound.R
import com.ku.lostandfound.data.CampusJsonRepository
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal enum class ReminderStatus(val label: String) {
    OFF("꺼짐"),
    SIGN_IN("로그인이 필요해요."),
    SELECT_BUILDINGS("알림을 받을 건물을 선택해 주세요."),
    PRECISE_LOCATION("정확한 위치 권한이 필요해요."),
    BACKGROUND_LOCATION("백그라운드 위치 권한이 필요해요."),
    LOCATION_DISABLED("기기의 위치 서비스가 꺼져 있어요."),
    NOTIFICATIONS("알림 권한과 예방 알림 채널을 허용해 주세요."),
    PLAY_SERVICES("Google Play 서비스에서 위치 감시를 사용할 수 없어요."),
    STARTING("건물 감시를 설정하고 있어요."),
    MONITORING("선택한 건물의 진입과 이탈을 감시 중이에요."),
    ERROR("건물 감시를 시작하지 못했어요. 위치 설정을 확인하고 다시 시도해 주세요."),
}

internal data class ReminderPreview(val weather: EntryWeather?, val sent: Boolean)

internal class LeaveReminderController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syncLock = Mutex()
    private val store = LeaveReminderStore(context)
    private val weather = CampusWeather()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var owner = TokenManager.reminderSession()
    private var presence = owner?.let(store::presence) ?: mutableMapOf()
    private var registration = store.registration()
    private var needsRegistration = store.pendingCallback() != null || (registration?.let {
        it.requestIds.keys != it.buildings || it.owner != owner
    } ?: true)
    private var revision = 0L
    private val invalidatedBuildings = mutableSetOf<Int>()
    private var adding: Pair<ReminderRegistration, RegistrationAttempt>? = null
    private var pendingRemoval = store.pendingRemoval()
    private val removalRequests = RemovalRequests(SystemClock::elapsedRealtime, ::removeIds)
    private val pendingEvents = mutableListOf<Intent>()

    val regions = runCatching {
        CampusJsonRepository(context).loadBuildings().mapNotNull(ReminderRegion::from)
    }.getOrDefault(emptyList())
    private val regionsById = regions.associateBy { it.id }

    private val mutableSettings = MutableStateFlow(store.settings())
    val settings = mutableSettings.asStateFlow()
    private val mutableStatus = MutableStateFlow(ReminderStatus.OFF)
    val status = mutableStatus.asStateFlow()

    init {
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "분실물 예방 알림", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "선택한 건물에서 나올 때 소지품을 확인하는 알림"
            enableVibration(true)
        })
        scope.launch {
            TokenManager.sessionChanges.drop(1).collect { reconcile() }
        }
    }

    fun refresh() {
        scope.launch { reconcile() }
    }

    fun update(next: LeaveReminderSettings) {
        val previous = settings.value
        if (previous == next) return
        mutableSettings.value = next
        store.save(next)
        val removed = previous.buildingIds - next.buildingIds
        if (previous.enabled != next.enabled || previous.buildingIds != next.buildingIds) revision++
        invalidatedBuildings += removed
        if (!next.enabled) {
            needsRegistration = true
            presence.clear()
            cancelNotifications()
            LeaveReminderRestoreJob.cancel(context)
            store.setRecoverySince(null)
        } else {
            presence.keys.removeAll(removed)
            removed.forEach { notifications.cancel(CHANNEL, NOTIFICATION_BASE + it) }
        }
        store.savePresence(owner, presence)
        if (previous.enabled != next.enabled || previous.buildingIds != next.buildingIds) refresh()
    }

    fun invalidateAfterBoot() {
        revision++
        invalidateRegistration()
    }

    /** Returns true when JobScheduler should retry later with backoff. */
    suspend fun restore(expected: String?): Boolean {
        restoring = true
        try {
            return restoreAttempts(expected)
        } finally {
            restoring = false
        }
    }

    // True while the restore job runs: it reschedules itself through jobFinished(), and scheduling
    // the same job ID from inside would stop the running job.
    private var restoring = false

    private suspend fun restoreAttempts(expected: String?): Boolean {
        for ((index, wait) in reminderRestoreDelays.withIndex()) {
            delay(wait)
            if (TokenManager.reminderSession() != expected) return false
            Log.i(LOG_TAG, "restore attempt=${index + 1} signedIn=${expected != null}")
            val retry = reconcile()
            Log.i(LOG_TAG, "restore status=${status.value.name} retry=$retry")
            if (!retry && !needsRecovery(expected)) {
                Log.i(LOG_TAG, "restore finished status=${status.value.name}")
                return false
            }
        }
        return needsRecovery(expected).also { Log.i(LOG_TAG, "restore deferred=$it") }
    }

    private val recoveryWindow = RecoveryWindow(store::recoverySince, store::setRecoverySince, System::currentTimeMillis)

    /** Boot and error recovery both decide here, so both share the 24h window from the first outage. */
    fun needsRecovery(expected: String?): Boolean = recoveryWindow.allows(
        reminderRecoveryNeeded(expected, TokenManager.reminderSession(), settings.value.enabled, status.value))

    private fun scheduleRecovery(active: String) {
        if (!restoring && needsRecovery(active)) LeaveReminderRestoreJob.enqueue(context, active, replace = false)
    }

    private fun markMonitoring() {
        mutableStatus.value = ReminderStatus.MONITORING
        store.setRecoverySince(null)
    }

    private fun updateOwner(): String? {
        val active = TokenManager.reminderSession()
        if (active != owner) {
            owner = active
            revision++
            presence.clear()
            store.savePresence(active, presence)
            cancelNotifications()
            needsRegistration = true
            LeaveReminderRestoreJob.cancel(context)
            store.setRecoverySince(null)
        }
        return active
    }

    private fun readiness(active: String?): ReminderStatus {
        val current = settings.value
        return when {
            !current.enabled -> ReminderStatus.OFF
            active == null -> ReminderStatus.SIGN_IN
            regions.isEmpty() -> ReminderStatus.ERROR
            current.buildingIds.none(regionsById::containsKey) -> ReminderStatus.SELECT_BUILDINGS
            !permitted(Manifest.permission.ACCESS_FINE_LOCATION) -> ReminderStatus.PRECISE_LOCATION
            Build.VERSION.SDK_INT >= 29 && !permitted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                ReminderStatus.BACKGROUND_LOCATION
            !LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)) ->
                ReminderStatus.LOCATION_DISABLED
            !canNotify() -> ReminderStatus.NOTIFICATIONS
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS ->
                ReminderStatus.PLAY_SERVICES
            else -> ReminderStatus.STARTING
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun reconcile(): Boolean = syncLock.withLock {
        val active = updateOwner()
        retryOrphanRemoval()
        // A previous process may have died between submitting and confirming an add.
        store.pendingCallback()?.let { interrupted ->
            removeCallback(interrupted)
            store.setPendingCallback(null)
            invalidateRegistration()
        }
        val ready = readiness(active)
        if (ready != ReminderStatus.STARTING || active == null) {
            invalidateRegistration()
            mutableStatus.value = ready
            // Turning location back on is not broadcast to manifest receivers (API 26+), so an enabled
            // reminder waiting for it relies on the bounded restore job instead of the next app launch.
            if (ready == ReminderStatus.LOCATION_DISABLED && active != null) scheduleRecovery(active)
            return@withLock shouldRetryPlayServices()
        }

        val selected = settings.value.buildingIds.intersect(regionsById.keys)
        if (needsRegistration || registration?.let { existingCallback(it.id) == null } == true) {
            invalidateRegistration()
        }
        val existing = registration
        val removed = existing?.buildings.orEmpty().filterTo(mutableSetOf()) {
            it !in selected || it in invalidatedBuildings || it in pendingRemoval
        }
        val retained = existing?.requestIds.orEmpty() - removed
        val newBuildings = selected - retained.keys
        if (removed.isEmpty() && newBuildings.isEmpty()) {
            markMonitoring()
            return@withLock false
        }

        val requestRevision = revision
        mutableStatus.value = ReminderStatus.STARTING
        if (removed.isNotEmpty()) {
            val remaining = checkNotNull(existing).copy(buildings = retained.keys, requestIds = retained)
            presence.keys.removeAll(removed)
            store.savePresence(active, presence)
            pendingRemoval = removed
            if (!store.beginRemoval(removed)) {
                mutableStatus.value = ReminderStatus.ERROR
                return@withLock true
            }
            val requestIds = removed.map(existing.requestIds::getValue)
            val removedResult = removalRequests.await(requestIds)
            if (!currentRequest(active, requestRevision)) {
                return@withLock requestRevision == revision && shouldRetryPlayServices()
            }
            if (removedResult != true || !store.finishRemoval(remaining)) {
                mutableStatus.value = ReminderStatus.ERROR
                return@withLock true
            }
            registration = remaining
            pendingRemoval = emptySet()
            removalRequests.forget(requestIds)
        }
        if (!currentRequest(active, requestRevision)) {
            return@withLock requestRevision == revision && shouldRetryPlayServices()
        }
        if (newBuildings.isEmpty()) {
            markMonitoring()
            return@withLock false
        }

        val generation = UUID.randomUUID().toString()
        val target = ReminderRegistration(active, existing?.id ?: generation, newBuildings,
            newBuildings.associateWith { "${regionsById.getValue(it).requestId}:$generation" })
        if (!store.setPendingCallback(target.id)) {
            mutableStatus.value = ReminderStatus.ERROR
            return@withLock false
        }
        val intent = callback(target.id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(newBuildings.map { id ->
                val region = regionsById.getValue(id)
                Geofence.Builder()
                    .setRequestId(target.requestIds.getValue(id))
                    .setCircularRegion(region.latitude, region.longitude, region.radius.toFloat())
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }).build()

        var accepted = false
        val attempt = RegistrationAttempt {
            if (existing == null) removeCallback(intent)
            else discardFences(target.requestIds.values)
        }
        adding = target to attempt
        try {
            try {
                LocationServices.getGeofencingClient(context).addGeofences(request, intent)
                    .addOnCompleteListener { task ->
                        val code = if (task.isSuccessful) CommonStatusCodes.SUCCESS
                            else (task.exception as? ApiException)?.statusCode ?: CommonStatusCodes.ERROR
                        attempt.complete(code)
                    }
            } catch (_: SecurityException) {
                attempt.complete(GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION)
            } catch (_: Exception) {
                attempt.complete(CommonStatusCodes.ERROR)
            }
            val code = attempt.await {
                Log.i(LOG_TAG, "registration pending after timeout")
            }
            Log.i(LOG_TAG, "registration result=${code ?: "timed_out"}")
            if (!currentRequest(active, requestRevision)) {
                return@withLock requestRevision == revision && shouldRetryPlayServices()
            }
            if (code != CommonStatusCodes.SUCCESS) {
                if (existing != null && (code == null || attempt.monitoringLost)) invalidateRegistration()
                mutableStatus.value = ReminderStatus.ERROR
                return@withLock code == null || code in setOf(CommonStatusCodes.ERROR,
                    CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.TIMEOUT,
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE, GeofenceStatusCodes.GEOFENCE_REQUEST_TOO_FREQUENT)
            }

            TokenManager.withReminderSession(active) {
                val confirmed = target.copy(buildings = selected, requestIds = retained + target.requestIds)
                registration = confirmed
                store.setRegistration(confirmed)
                needsRegistration = false
                invalidatedBuildings.removeAll(newBuildings)
                markMonitoring()
                accepted = true
            }
            false
        } finally {
            adding = null
            val replay = pendingEvents.toList()
            pendingEvents.clear()
            if (accepted) replay.forEach { event -> scope.launch { handle(event) } }
            else attempt.abandon()
            store.setPendingCallback(null)
        }
    }

    private fun shouldRetryPlayServices(): Boolean = status.value == ReminderStatus.PLAY_SERVICES &&
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) in
        setOf(ConnectionResult.SERVICE_UPDATING, ConnectionResult.INTERNAL_ERROR)

    private fun currentRequest(active: String, requestRevision: Long): Boolean {
        val latest = updateOwner()
        val ready = readiness(latest)
        if (active != latest || ready != ReminderStatus.STARTING) {
            invalidateRegistration()
            mutableStatus.value = ready
            return false
        }
        return revision == requestRevision
    }

    private fun invalidateRegistration() {
        val old = registration
        registration = null
        needsRegistration = true
        // Cancelling the platform callback removes its fences. Keep the durable ID until then.
        if (old != null) removeCallback(old.id)
        pendingRemoval = emptySet()
        removalRequests.clear()
        store.setRegistration(null)
        presence.clear()
        store.savePresence(owner, presence)
    }

    // An abandoned add shares the live callback, so only its own IDs may go. They stay
    // persisted until the platform confirms removal, including across process restarts.
    private fun discardFences(ids: Collection<String>) {
        store.addOrphanFences(ids)
        scope.launch { if (removeIds(ids.toList()).await()) store.removeOrphanFences(ids) }
    }

    private fun retryOrphanRemoval() {
        val ids = store.orphanFences()
        if (ids.isNotEmpty()) scope.launch { if (removeIds(ids.toList()).await()) store.removeOrphanFences(ids) }
    }

    @SuppressLint("MissingPermission")
    private fun removeIds(ids: List<String>): CompletableDeferred<Boolean> {
        val result = CompletableDeferred<Boolean>()
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(ids) }
            .onSuccess { task -> task.addOnCompleteListener { result.complete(it.isSuccessful) } }
            .onFailure { result.complete(false) }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun removeCallback(id: String) {
        existingCallback(id)?.let(::removeCallback)
    }

    @SuppressLint("MissingPermission")
    private fun removeCallback(intent: PendingIntent) {
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(intent) }
        intent.cancel()
    }

    private fun existingCallback(id: String) = callback(id, PendingIntent.FLAG_NO_CREATE)

    private fun callback(id: String, flags: Int): PendingIntent? {
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 183, Intent(context, LeaveReminderReceiver::class.java)
            .setAction(LeaveReminderReceiver.TRANSITION)
            .setData("finders-reminder://geofence/$id".toUri()), flags or mutable)
    }

    suspend fun handle(intent: Intent) {
        val active = updateOwner() ?: return
        val callbackId = intent.data?.lastPathSegment ?: return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val inFlight = adding?.takeIf { (target, _) -> target.owner == active && target.id == callbackId }
        if (inFlight != null && event.hasError() && event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
            inFlight.second.monitoringUnavailable()
            return
        }
        val live = registration
        // Checked before readiness: GEOFENCE_NOT_AVAILABLE usually arrives because location just went off.
        if (event.hasError() && live != null && live.owner == active && live.id == callbackId && !needsRegistration) {
            Log.i(LOG_TAG, "monitoring error=${event.errorCode}")
            revision++
            needsRegistration = true
            presence.clear()
            store.savePresence(active, presence)
            mutableStatus.value = readiness(active).takeIf { it != ReminderStatus.STARTING } ?: ReminderStatus.ERROR
            scheduleRecovery(active)
            return
        }
        if (readiness(active) != ReminderStatus.STARTING) return
        if (inFlight != null) {
            val (target, attempt) = inFlight
            if (attempt.monitoringLost) return
            if (!event.hasError() &&
                event.triggeringGeofences.orEmpty().any { it.requestId in target.requestIds.values } && pendingEvents.size < 20
            ) pendingEvents += Intent(intent)
        }
        val record = registration ?: return
        if (record.owner != active || record.id != callbackId || needsRegistration || event.hasError()) return
        val now = System.currentTimeMillis()
        val at = event.triggeringLocation?.time ?: return
        if (at !in (now - MAX_STAY_MS)..now) return
        val current = settings.value
        val ids = event.triggeringGeofences.orEmpty()
            .mapNotNull { fence -> record.buildingFor(fence.requestId) }
            .filter { it in current.buildingIds && it !in invalidatedBuildings && it !in pendingRemoval }
            .distinct()
        val needWeather = mutableMapOf<Int, String>()
        for (id in ids) {
            val before = presence[id] ?: ReminderPresence(0)
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    val after = before.enter(at) { UUID.randomUUID().toString() }
                    presence[id] = after
                    val visit = after.visit
                    if (visit != null && visit.id != before.visit?.id && current.needsWeather) needWeather[id] = visit.id
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    val (after, completed) = before.exit(at, current.dwellMinutes)
                    presence[id] = after
                    if (completed != null) {
                        post(active, NOTIFICATION_BASE + id,
                            ReminderMessage.make(regionsById[id]?.name, current.items, completed.weather))
                    }
                }
            }
        }
        store.savePresence(active, presence)
        if (needWeather.isEmpty()) return

        // A single callback can enter several overlapping buildings using the same fixed-coordinate observation.
        val observed = weather.lookup(at)
        if (active != TokenManager.reminderSession() || owner != active || !settings.value.enabled ||
            readiness(active) != ReminderStatus.STARTING || registration?.id != record.id || needsRegistration
        ) return
        for ((id, visit) in needWeather) {
            val latest = presence[id] ?: continue
            presence[id] = latest.withWeather(visit, observed)
        }
        store.savePresence(active, presence)
    }

    suspend fun preview(lookupWeather: suspend () -> EntryWeather = { weather.lookup() }): ReminderPreview {
        val active = TokenManager.reminderSession() ?: return ReminderPreview(null, false)
        if (!canNotify()) return ReminderPreview(null, false)
        val snapshot = settings.value
        if (!snapshot.enabled) return ReminderPreview(null, false)
        val sampled = if (snapshot.needsWeather) lookupWeather() else null
        val building = snapshot.buildingIds.firstNotNullOfOrNull { regionsById[it]?.name }
        val sent = settings.value.enabled && post(active, PREVIEW_ID,
            ReminderMessage.make(building, snapshot.items, sampled ?: EntryWeather.UNKNOWN))
        return ReminderPreview(sampled, sent)
    }

    private fun permitted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    internal fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || permitted(Manifest.permission.POST_NOTIFICATIONS)) &&
            notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    internal fun hasHighChannelImportance(): Boolean =
        (notifications.getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_NONE) >=
            NotificationManager.IMPORTANCE_HIGH

    private fun post(active: String, id: Int, message: ReminderMessage): Boolean =
        TokenManager.withReminderSession(active) {
            if (!canNotify()) return@withReminderSession false
            val launch = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val openApp = PendingIntent.getActivity(context, PREVIEW_ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val expanded = NotificationCompat.BigTextStyle().bigText(message.body)
            if (message.subtitle != null) expanded.setSummaryText("날씨 데이터 Open-Meteo")
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setSubText(message.subtitle)
                .setStyle(expanded)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            runCatching { notifications.notify(CHANNEL, id, notification) }.isSuccess
        } ?: false

    private fun cancelNotifications() {
        regions.forEach { notifications.cancel(CHANNEL, NOTIFICATION_BASE + it.id) }
        notifications.cancel(CHANNEL, PREVIEW_ID)
    }

    companion object {
        internal const val LOG_TAG = "LeaveReminder"
        internal const val CHANNEL = "finders_leave_reminders"
        private const val NOTIFICATION_BASE = 682_000
        private const val PREVIEW_ID = NOTIFICATION_BASE - 1
        // The singleton retains only the process's application context.
        @SuppressLint("StaticFieldLeak")
        private var instance: LeaveReminderController? = null

        // All entry points are on Main (activity, Compose, broadcast receiver).
        fun get(context: Context): LeaveReminderController = instance ?: run {
            val app = context.applicationContext
            TokenManager.init(app)
            LeaveReminderController(app).also { instance = it }
        }
    }
}

internal val reminderRestoreDelays = listOf(0L, 10_000L, 30_000L)

// Only states that can clear without the user opening the app are retried; missing
// permissions or a deliberate opt-out never schedule background work.
private val recoverableStatuses = setOf(ReminderStatus.LOCATION_DISABLED, ReminderStatus.PLAY_SERVICES,
    ReminderStatus.STARTING, ReminderStatus.ERROR)

internal fun reminderRecoveryNeeded(expected: String?, active: String?, enabled: Boolean, status: ReminderStatus) =
    expected != null && active == expected && enabled && status in recoverableStatuses

/** JobScheduler backoff caps at 5h; after a day the next app launch restarts monitoring instead. */
internal const val RECOVERY_WINDOW_MS = 24L * 60 * 60 * 1000

/**
 * Persisted start of the current outage. The first retry decision in a recoverable state records
 * it (also on the boot path, where nothing recorded it before); later decisions and new processes
 * keep it. A non-recoverable state ends the outage and clears it, so the next one starts fresh.
 */
internal class RecoveryWindow(
    private val load: () -> Long,
    private val save: (Long?) -> Unit,
    private val now: () -> Long,
) {
    fun allows(recoverable: Boolean): Boolean {
        if (!recoverable) {
            if (load() != 0L) save(null)
            return false
        }
        val since = load().takeIf { it != 0L } ?: now().also(save)
        return now() - since < RECOVERY_WINDOW_MS
    }
}

internal class RemovalRequests(
    private val elapsed: () -> Long,
    private val submit: (List<String>) -> Deferred<Boolean>,
) {
    private data class Request(val started: Long, val result: Deferred<Boolean>)
    private val byId = mutableMapOf<String, Request>()

    suspend fun await(ids: List<String>): Boolean? {
        val now = elapsed()
        val toSubmit = ids.distinct().filter { id ->
            val request = byId[id]
            request == null || (!request.result.isCompleted && now - request.started >= 12_000)
        }
        if (toSubmit.isNotEmpty()) {
            val request = Request(now, submit(toSubmit))
            toSubmit.forEach { byId[it] = request }
        }
        return withTimeoutOrNull(3_000) {
            val results = ids.map(byId::getValue).distinct().associateWith { it.result.await() }
            byId.entries.removeAll { results[it.value] == false }
            results.values.all { it }
        }
    }

    fun forget(ids: List<String>) { byId.keys.removeAll(ids.toSet()) }
    fun clear() { byId.clear() }
}

// The listener can outlive the wait and must still clean up its own registration.
internal class RegistrationAttempt(private val cleanup: () -> Unit) {
    private val completion = CompletableDeferred<Int>()
    private var abandoned = false
    var monitoringLost = false
        private set

    fun monitoringUnavailable() {
        monitoringLost = true
        completion.complete(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
    }

    fun complete(code: Int) {
        if (abandoned) {
            Log.i(LeaveReminderController.LOG_TAG, "late registration completion code=$code")
            if (code == CommonStatusCodes.SUCCESS) cleanup()
        }
        completion.complete(code)
    }

    fun abandon() {
        abandoned = true
        cleanup()
    }

    suspend fun await(onPending: () -> Unit): Int? {
        val code = withTimeoutOrNull(3_000) { completion.await() } ?: run {
            onPending()
            withTimeoutOrNull(9_000) { completion.await() }
        }
        return if (monitoringLost) GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE else code
    }
}

internal fun refreshLeaveReminders(context: Context) {
    LeaveReminderController.get(context).refresh()
}
