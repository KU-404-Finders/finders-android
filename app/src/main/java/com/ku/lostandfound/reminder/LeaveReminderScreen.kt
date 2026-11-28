package com.ku.lostandfound.reminder

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.notification.notificationChannelSettings
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private val ReminderGreen = Color(0xFF1B6425)
// iOS SettingsStyle.canvas / secondary.
private val ReminderBackground = Color(0xFFF2F2F7)
private val ReminderSecondary = Color(0xFF8E8E93)

@Composable
internal fun LeaveReminderScreen(
    onBack: () -> Unit,
    sendPreview: (suspend () -> ReminderPreview)? = null,
) {
    val context = LocalContext.current
    val controller = remember(context) { LeaveReminderController.get(context) }
    val settings by controller.settings.collectAsState()
    val status by controller.status.collectAsState()
    var pickingBuildings by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ReminderPreview?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val settingsListState = rememberLazyListState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionChange by remember { mutableIntStateOf(0) }
    fun refresh() {
        permissionChange++
        controller.refresh()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val precise = remember(permissionChange) { context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) }
    val background = remember(permissionChange) {
        Build.VERSION.SDK_INT < 29 || context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val notificationPermission = remember(permissionChange) {
        Build.VERSION.SDK_INT < 33 || context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
    val notificationsAllowed = remember(permissionChange) { controller.canNotify() }
    val highImportance = remember(permissionChange) { controller.hasHighChannelImportance() }
    var askedPrecise by rememberSaveable { mutableStateOf(false) }
    var askedBackground by rememberSaveable { mutableStateOf(false) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val enableNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val enableBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val enablePrecise = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val appSettings = {
        openSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }

    fun requestPrecise() {
        val canAsk = (context as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } == true
        if (askedPrecise && !canAsk) appSettings()
        else {
            askedPrecise = true
            enablePrecise.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun requestBackground() {
        if (Build.VERSION.SDK_INT < 29) return
        val canAsk = (context as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } == true
        if (Build.VERSION.SDK_INT == 29 && (!askedBackground || canAsk)) {
            askedBackground = true
            enableBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else appSettings()
    }

    val backgroundLabel = if (Build.VERSION.SDK_INT >= 30)
        context.packageManager.backgroundPermissionOptionLabel.toString() else "항상 허용"

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationPermission) {
            val canAsk = (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } == true
            if (!askedNotifications || canAsk) {
                askedNotifications = true
                enableNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = if (manager.areNotificationsEnabled()) {
            notificationChannelSettings(context, LeaveReminderController.CHANNEL)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        openSettings(context, intent)
    }

    fun addItem() {
        val latest = controller.settings.value
        val name = latest.acceptedName(newItem) ?: return
        controller.update(latest.copy(items = latest.items + ReminderItem(name, ReminderCondition.ALWAYS)))
        newItem = ""
        focusManager.clearFocus()
    }

    BackHandler(enabled = pickingBuildings) { pickingBuildings = false }
    Column(Modifier.fillMaxSize().background(ReminderBackground)) {
        BackTitleBar(
            title = if (pickingBuildings) "알림 건물" else "분실물 예방 알림",
            onBackClick = { if (pickingBuildings) pickingBuildings = false else onBack() },
        )
        if (pickingBuildings) {
            BuildingPicker(controller.regions, settings.buildingIds) { id ->
                val latest = controller.settings.value
                val selected = latest.buildingIds
                val next = when {
                    id in selected -> selected - id
                    selected.size < MAX_REMINDER_BUILDINGS -> selected + id
                    else -> selected
                }
                controller.update(latest.copy(buildingIds = next))
            }
        } else {
            LazyColumn(
                state = settingsListState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ReminderCard {
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = settings.enabled,
                                role = Role.Switch,
                                onValueChange = { enabled ->
                                    if (enabled) showDisclosure = true
                                    else controller.update(controller.settings.value.copy(enabled = false))
                                },
                            ).padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("예방 알림 사용", Modifier.weight(1f))
                            Switch(settings.enabled, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = ReminderGreen))
                        }
                    }
                    ReminderFooter("수업을 듣는 건물들을 지정하면, 그 건물에서 떠날 때 소지품을 챙겼는지 물어보는 알림을 전송합니다.")
                    if (!settings.enabled) ReminderFooter(ReminderStatus.OFF.label)
                }
                if (settings.enabled) {
                    item {
                        ReminderCard {
                            ReminderFooter(status.label)
                            if (!precise) TextButton(onClick = ::requestPrecise) { Text("정확한 위치 허용") }
                            if (precise && !background) {
                                ReminderFooter("앱을 닫아도 감지하려면 앱 위치 설정에서 ‘$backgroundLabel’을 선택해 주세요.")
                                TextButton(onClick = ::requestBackground) { Text("백그라운드 위치 허용") }
                            }
                            if (!notificationsAllowed) {
                                TextButton(onClick = ::requestNotifications) { Text("예방 알림 허용") }
                            } else {
                                if (!highImportance) {
                                    // An existing channel keeps the importance it was created with (older builds used DEFAULT).
                                    ReminderFooter("지금은 알림창에만 표시돼요. 화면 상단 팝업으로 받으려면 아래 ‘예방 알림 채널 설정’에서 ‘팝업으로 표시’(또는 중요도 ‘긴급’)를 켜 주세요.")
                                }
                                TextButton(onClick = ::requestNotifications) { Text("예방 알림 채널 설정") }
                            }
                            if (status == ReminderStatus.LOCATION_DISABLED) {
                                TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                                    Text("기기 위치 설정 열기")
                                }
                            }
                            if (status == ReminderStatus.ERROR) TextButton(onClick = ::refresh) { Text("감시 다시 시도") }
                        }
                    }
                    item {
                        ReminderCard {
                            val names = controller.regions.filter { it.id in settings.buildingIds }.map { it.name }
                            val summary = when (names.size) {
                                0 -> "선택 안 함"
                                1 -> names.first()
                                else -> "${names.first()} 외 ${names.size - 1}곳"
                            }
                            ReminderRow("알림 건물", summary, onClick = { pickingBuildings = true }, navigate = true)
                            ReminderDivider()
                            ReminderMenu("방문 판단 기준", settings.dwellMinutes, reminderDwellChoices,
                                label = { if (it == 60) "1시간 이상" else "${it}분 이상" },
                                choose = { controller.update(controller.settings.value.copy(dwellMinutes = it)) })
                        }
                    }
                    item {
                        ReminderCard {
                            settings.items.forEach { item ->
                                key(item.name) {
                                    DismissReminderItem(item.name, onDelete = {
                                        val latest = controller.settings.value
                                        controller.update(latest.copy(items = latest.items.filterNot { it.name == item.name }))
                                    }) {
                                        ReminderMenu(item.name, item.condition, ReminderCondition.entries, ReminderCondition::label) {
                                            val latest = controller.settings.value
                                            controller.update(latest.copy(items = latest.items.map { saved ->
                                                if (saved.name == item.name) saved.copy(condition = it) else saved
                                            }))
                                        }
                                    }
                                    ReminderDivider()
                                }
                            }
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = newItem,
                                    onValueChange = { if (it.codePointCount(0, it.length) <= MAX_REMINDER_NAME) newItem = it },
                                    singleLine = true,
                                    placeholder = { Text("물건 이름") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addItem() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(enabled = settings.acceptedName(newItem) != null, onClick = ::addItem) { Text("추가") }
                            }
                        }
                        ReminderFooter("‘비 올 때 받기’는 건물에 들어갈 때 비가 오는 경우예요. 항목을 왼쪽으로 밀면 삭제할 수 있어요. 최대 ${MAX_REMINDER_ITEMS}개, 이름은 ${MAX_REMINDER_NAME}자까지 입력할 수 있어요.")
                    }
                    item {
                        ReminderCard {
                            val building = controller.regions.firstOrNull { it.id in settings.buildingIds }?.name
                            val sample = ReminderMessage.make(building, settings.items, EntryWeather.RAIN)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(sample.title, style = MaterialTheme.typography.titleSmall)
                                sample.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(sample.body, style = MaterialTheme.typography.bodyMedium)
                            }
                            ReminderDivider()
                            TextButton(enabled = !sending && notificationsAllowed, onClick = {
                                focusManager.clearFocus()
                                sending = true
                                scope.launch {
                                    try {
                                        preview = sendPreview?.invoke() ?: controller.preview()
                                    } finally {
                                        sending = false
                                    }
                                }
                            }, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(if (sending) "날씨 확인 중…" else "테스트 알림 보내기")
                            }
                            preview?.let { result ->
                                val note = when (result.weather) {
                                    EntryWeather.RAIN -> "지금 캠퍼스에 비 또는 눈이 와요."
                                    EntryWeather.DRY -> "지금 캠퍼스에 비나 눈이 오지 않아요."
                                    EntryWeather.UNKNOWN -> "날씨를 확인하지 못했어요. ‘비 올 때 받기’ 물건은 제외했어요."
                                    null -> "날씨를 조회하지 않았어요."
                                }
                                ReminderFooter(if (result.sent) note else "테스트 알림을 발송하지 못했어요. 알림 설정을 확인해 주세요. $note")
                            }
                        }
                        TextButton(onClick = {
                            runCatching { uriHandler.openUri(CampusWeather.ATTRIBUTION_URL) }
                        }) { Text("날씨 정보 및 데이터 출처 · Open-Meteo") }
                    }
                }
                item {
                    Text(
                        "현재 위치와 체류 기록, 소지품 설정은 기기에서 처리돼요. 날씨 조회에는 건국대의 고정 좌표를 사용해요. " +
                            "건물 감시 범위가 겹치거나 GPS 오차와 백그라운드 처리로 감지가 늦어질 수 있어요. " +
                            "앱을 강제 종료했다면 다시 실행해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("분실물 예방 알림에 위치를 사용해요") },
            text = {
                Text("찾을건대는 앱을 닫았거나 사용하지 않을 때도 위치를 사용해 선택한 건물에서 나올 때 예방 알림을 보내요. " +
                    "위치는 기기에서 처리하고 체류 기록은 기기에 저장해요. Finders 서버로 전송하지 않아요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    controller.update(controller.settings.value.copy(enabled = true))
                    if (!precise) requestPrecise()
                }) { Text("동의하고 켜기") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ReminderCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ReminderFooter(text: String) {
    Text(
        text, Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        fontSize = 12.sp, lineHeight = 17.sp, color = ReminderSecondary,
    )
}

@Composable
private fun ReminderDivider() = HorizontalDivider(Modifier.padding(start = 16.dp))

@Composable
private fun ReminderRow(title: String, value: String, onClick: () -> Unit, navigate: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, Modifier.weight(1f))
        Text(
            value, Modifier.widthIn(max = 144.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (navigate) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissReminderItem(name: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val delete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == SwipeToDismissBoxValue.EndToStart) delete()
    }
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("$name 삭제") { delete(); true })
        },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("삭제", color = MaterialTheme.colorScheme.onError) }
        },
    ) {
        Box(Modifier.fillMaxWidth().background(Color.White)) { content() }
    }
}

@Composable
private fun <T> ReminderMenu(title: String, value: T, options: List<T>, label: (T) -> String, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ReminderRow(title, label(value), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = if (option == value) {
                        { Icon(Icons.Outlined.Check, contentDescription = "선택됨", tint = ReminderGreen) }
                    } else null,
                    onClick = { expanded = false; choose(option) },
                )
            }
        }
    }
}

@Composable
private fun BuildingPicker(regions: List<ReminderRegion>, selected: Set<Int>, toggle: (Int) -> Unit) {
    val ordered = remember(regions) {
        val collator = Collator.getInstance(Locale.KOREAN)
        regions.sortedWith { left, right -> collator.compare(left.name, right.name) }
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
        item {
            ReminderFooter("수업을 듣는 건물 · ${selected.size}/$MAX_REMINDER_BUILDINGS")
        }
        items(ordered, key = { it.id }) { building ->
            val checked = building.id in selected
            val permitted = checked || selected.size < MAX_REMINDER_BUILDINGS
            Row(
                Modifier.fillMaxWidth().background(Color.White)
                    .toggleable(checked, enabled = permitted, role = Role.Checkbox, onValueChange = { toggle(building.id) })
                    .heightIn(min = 48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    building.name, Modifier.weight(1f),
                    color = if (permitted) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = ReminderGreen)
            }
            if (building != ordered.last()) ReminderDivider()
        }
        item {
            ReminderFooter("최대 ${MAX_REMINDER_BUILDINGS}곳까지 고를 수 있어요. 가까이 붙어 있는 건물은 함께 감지될 수 있어요.")
        }
    }
}

private fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun openSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}
