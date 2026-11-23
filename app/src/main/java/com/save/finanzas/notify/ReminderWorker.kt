package com.save.finanzas.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.save.finanzas.data.Store
import com.save.finanzas.domain.migrated

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = Store(applicationContext.filesDir).load().migrated()
        Notifier.run(applicationContext, data)
        return Result.success()
    }
}
