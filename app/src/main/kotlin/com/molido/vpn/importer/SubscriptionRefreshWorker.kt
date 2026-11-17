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
import java.security.MessageDigest

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

        val configuredUrls = store.getSubscriptions()
        val disabledUrls = store.getDisabledSubscriptions()
        val enabledCustomUrls = configuredUrls.filterNot { it in disabledUrls }
        val activeCustomSourceIds = configuredUrls.map(::customSourceId).toSet()

        for (url in enabledCustomUrls) {
            try {
                reports += SubscriptionManager.fetchSubscriptionReport(url, customSourceId(url))
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

        val freshConfigs = reports.flatMap { report ->
            report.configs.map { config ->
                JSONObject()
                    .put("engineId", config.engineId)
                    .put("name", config.name)
                    .put("payload", config.payload)
                    .put("sourceId", report.sourceId ?: JSONObject.NULL)
            }
        }
        val successfulSourceIds = reports.mapNotNull { it.sourceId }.toSet()
        val previousSnapshot = store.getSubscriptionSnapshot()
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        val previousConfigs = previousSnapshot?.optJSONArray("configs") ?: JSONArray()
        val retainedLastGood = (0 until previousConfigs.length()).mapNotNull { index ->
            previousConfigs.optJSONObject(index)?.takeIf { old ->
                val sourceId = old.optString("sourceId").takeUnless { it.isBlank() || it == "null" }
                // Replace a source only after that same source fetched and parsed successfully.
                // Removed custom feeds are dropped; disabled or temporarily failing feeds retain
                // their last-known-good profiles without being fetched.
                SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                    sourceId = sourceId,
                    successfulSourceIds = successfulSourceIds,
                    activeCustomSourceIds = activeCustomSourceIds
                )
            }
        }
        val configs = (retainedLastGood + freshConfigs).distinctBy { it.optString("payload") }

        if (freshConfigs.isEmpty()) {
            // Keep the previous encrypted snapshot intact when every feed fails.
            return if (failures > 0) Result.retry() else Result.success()
        }

        return try {
            val sourceJson = JSONArray().apply {
                val previousSources = previousSnapshot?.optJSONArray("sources") ?: JSONArray()
                val freshIds = successfulSourceIds
                (0 until previousSources.length()).forEach { index ->
                    previousSources.optJSONObject(index)?.let { old ->
                        val oldId = old.optString("sourceId")
                        if (oldId !in freshIds &&
                            (!oldId.startsWith("custom-") || oldId in activeCustomSourceIds)
                        ) put(old)
                    }
                }
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

private fun customSourceId(url: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }
    return "custom-$digest"
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
