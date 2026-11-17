package com.molido.vpn.importer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.molido.vpn.data.EncryptedConfigStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Refreshes configured and curated feeds without replacing the last-good snapshot
 * unless at least one source returns a valid, non-empty profile set.
 */
class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = EncryptedConfigStore(applicationContext)
        val reports = mutableListOf<SubscriptionFetchReport>()
        var failures = 0

        for ((index, url) in store.getSubscriptions().filterNot { it in store.getDisabledSubscriptions() }.withIndex()) {
            try {
                reports += SubscriptionManager.fetchSubscriptionReport(url, "manual-$index")
            } catch (_: Exception) {
                failures++
            }
        }

        for (source in SubscriptionSourceCatalog.sources.filter { it.enabledByDefault }) {
            try {
                reports += SubscriptionManager.fetchSubscriptionReport(source.url, source.id)
            } catch (_: Exception) {
                failures++
            }
        }

        val configs = reports.flatMap { report ->
            report.configs.map { config ->
                JSONObject()
                    .put("engineId", config.engineId)
                    .put("name", config.name)
                    .put("payload", config.payload)
                    .put("sourceId", report.sourceId ?: JSONObject.NULL)
            }
        }.distinctBy { it.optString("payload") }

        if (configs.isEmpty()) {
            // Keep the previous encrypted snapshot intact when every feed fails.
            return if (failures > 0) Result.retry() else Result.success()
        }

        return try {
            val sourceJson = JSONArray().apply {
                reports.forEach { report ->
                    put(
                        JSONObject()
                            .put("sourceId", report.sourceId ?: JSONObject.NULL)
                            .put("fetchedBytes", report.fetchedBytes)
                            .put("skippedLines", report.skippedLines)
                            .put("configCount", report.configs.size)
                            .put("fetchedAtEpochMs", report.fetchedAtEpochMs)
                    )
                }
            }
            val snapshot = JSONObject()
                .put("schemaVersion", 1)
                .put("updatedAtEpochMs", System.currentTimeMillis())
                .put("sources", sourceJson)
                .put("configs", JSONArray(configs))
                .toString()
            store.saveSubscriptionSnapshot(snapshot)
            if (failures > 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object SubscriptionRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "molido-subscription-refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
