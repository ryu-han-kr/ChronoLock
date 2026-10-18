package com.chronolock.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

/** Android may defer this job for battery restrictions; opening History also refreshes immediately. */
@AndroidEntryPoint
class HistoryJobService : JobService() {
    @Inject lateinit var history: HistoryRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val retry = try { history.collect(); false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val JOB_ID = 301
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) == null) {
                scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, HistoryJobService::class.java))
                    .setPeriodic(6 * 60 * 60 * 1000L).setPersisted(true).build())
            }
        }
    }
}
