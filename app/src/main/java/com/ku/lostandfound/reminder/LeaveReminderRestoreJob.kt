package com.ku.lostandfound.reminder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Restores geofence monitoring after boot, update, or a monitoring error. One job ID per app:
 * `jobFinished(params, true)` / `onStopJob() == true` let JobScheduler apply its own exponential
 * backoff, so recovery never needs the app to be reopened and never runs in parallel.
 */
class LeaveReminderRestoreJob : JobService() {
    private var restore: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val owner = params.extras.getString(OWNER)
        restore = CoroutineScope(Dispatchers.Main).launch {
            val retry = try {
                LeaveReminderController.get(applicationContext).restore(owner)
            } catch (e: CancellationException) {
                throw e // Stopped by the scheduler: onStopJob decides about rescheduling.
            } catch (e: Exception) {
                Log.w(LeaveReminderController.LOG_TAG, "restore failed", e)
                LeaveReminderController.get(applicationContext).needsRecovery(owner)
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        restore?.cancel()
        val reschedule = LeaveReminderController.get(applicationContext).needsRecovery(params.extras.getString(OWNER))
        Log.i(LeaveReminderController.LOG_TAG, "restore stopped by scheduler reschedule=$reschedule")
        return reschedule
    }

    companion object {
        private const val JOB_ID = 682_001
        private const val OWNER = "owner"
        private const val INITIAL_BACKOFF_MS = 60_000L

        /** [replace] = false keeps an already pending job (and its backoff) for the same owner. */
        internal fun enqueue(context: Context, owner: String?, replace: Boolean = true) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val pending = scheduler.getPendingJob(JOB_ID)
            if (!replace && pending != null && pending.extras.getString(OWNER) == owner) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, LeaveReminderRestoreJob::class.java))
                .setExtras(PersistableBundle().apply { putString(OWNER, owner) })
                .setBackoffCriteria(INITIAL_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            val result = scheduler.schedule(job)
            Log.i(LeaveReminderController.LOG_TAG, "restore scheduled=${result == JobScheduler.RESULT_SUCCESS}")
        }

        /** Called when the feature is turned off or the account changes. */
        internal fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
