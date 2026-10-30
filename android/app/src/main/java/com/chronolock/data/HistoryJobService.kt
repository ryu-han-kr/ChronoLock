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
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var backups: BackupRepository
    @Inject lateinit var repository: LocalRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val retry = try {
                history.collect()
                runCatching { WeeklyReport.maybeNotify(applicationContext, history, preferences) }
                    .onFailure { android.util.Log.w("ChronoLock", "Weekly report failed", it) }
                // A removed folder or revoked access only skips this week's backup; Settings shows the last one written.
                runCatching { TimeBankStore.settle(repository, preferences, System.currentTimeMillis()) }
                    .onFailure { android.util.Log.w("ChronoLock", "Time bank not settled", it) }
                runCatching { backups.autoBackup(System.currentTimeMillis()) }
                    .onFailure { android.util.Log.w("ChronoLock", "Weekly backup failed", it) }
                false
            }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { android.util.Log.w("ChronoLock", "History collection failed; the job will retry", error); true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val JOB_ID = 301
        private const val PERIOD = 6 * 60 * 60 * 1000L
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            // Reschedule when an update changed the period, so existing installs pick up new settings.
            if (scheduler.getPendingJob(JOB_ID)?.intervalMillis != PERIOD) {
                scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, HistoryJobService::class.java))
                    .setPeriodic(PERIOD).setPersisted(true).build())
            }
        }
    }
}
