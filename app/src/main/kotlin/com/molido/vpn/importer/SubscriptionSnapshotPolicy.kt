package com.molido.vpn.importer

/**
 * Keeps last-known-good profiles for feeds that failed or were disabled.
 * A source is replaced only after that same source fetches and parses successfully.
 */
object SubscriptionSnapshotPolicy {
    fun shouldRetainPreviousConfig(
        sourceId: String?,
        successfulSourceIds: Set<String>,
        activeCustomSourceIds: Set<String>
    ): Boolean {
        if (sourceId in successfulSourceIds) return false
        if (sourceId == null) return true
        return !sourceId.startsWith("custom-") || sourceId in activeCustomSourceIds
    }
}
