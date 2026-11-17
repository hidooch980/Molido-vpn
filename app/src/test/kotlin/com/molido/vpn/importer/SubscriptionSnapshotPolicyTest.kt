package com.molido.vpn.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSnapshotPolicyTest {
    @Test
    fun retainsLastGoodProfilesWhenSourceFails() {
        assertTrue(
            SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                sourceId = "ss44-gold",
                successfulSourceIds = setOf("community-vless"),
                activeCustomSourceIds = emptySet()
            )
        )
    }

    @Test
    fun replacesOldProfilesOnlyAfterSameSourceSucceeds() {
        assertFalse(
            SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                sourceId = "ss44-gold",
                successfulSourceIds = setOf("ss44-gold"),
                activeCustomSourceIds = emptySet()
            )
        )
    }

    @Test
    fun removedCustomFeedsAreDroppedButDisabledExistingFeedsCanBeRetained() {
        assertFalse(
            SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                sourceId = "custom-removed",
                successfulSourceIds = emptySet(),
                activeCustomSourceIds = emptySet()
            )
        )
        assertTrue(
            SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                sourceId = "custom-disabled",
                successfulSourceIds = emptySet(),
                activeCustomSourceIds = setOf("custom-disabled")
            )
        )
    }

    @Test
    fun legacyUnattributedProfilesAreRetainedUntilReplacement() {
        assertTrue(
            SubscriptionSnapshotPolicy.shouldRetainPreviousConfig(
                sourceId = null,
                successfulSourceIds = emptySet(),
                activeCustomSourceIds = emptySet()
            )
        )
    }
}
