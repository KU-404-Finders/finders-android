package com.ku.lostandfound.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LeaveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != TRANSITION && action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (action != TRANSITION) {
            Log.i(LeaveReminderController.LOG_TAG, "received $action")
            LeaveReminderController.get(context).invalidateAfterBoot()
            LeaveReminderRestoreJob.enqueue(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeoutOrNull(9_000) {
                    val controller = LeaveReminderController.get(context)
                    controller.handle(intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        internal const val TRANSITION = "com.ku.lostandfound.LEAVE_REMINDER_TRANSITION"
    }
}
