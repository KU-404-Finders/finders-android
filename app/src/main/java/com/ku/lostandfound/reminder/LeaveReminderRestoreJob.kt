package com.ku.lostandfound.reminder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class LeaveReminderRestoreJob : JobService() {
    private var restore: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        restore = CoroutineScope(Dispatchers.Main).launch {
            try {
                LeaveReminderController.get(applicationContext).restoreAfterBoot()
            } finally {
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        restore?.cancel()
        Log.i(LeaveReminderController.LOG_TAG, "restore stopped by scheduler")
        return false
    }

    companion object {
        private const val JOB_ID = 682_001

        internal fun enqueue(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, LeaveReminderRestoreJob::class.java)).build()
            val result = context.getSystemService(JobScheduler::class.java).schedule(job)
            Log.i(LeaveReminderController.LOG_TAG, "restore scheduled=${result == JobScheduler.RESULT_SUCCESS}")
        }
    }
}
