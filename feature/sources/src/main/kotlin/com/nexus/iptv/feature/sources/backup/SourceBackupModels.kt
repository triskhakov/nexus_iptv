package com.nexus.iptv.feature.sources.backup

import kotlinx.serialization.Serializable

@Serializable
data class NexusBackupPayload(
    val formatVersion: Int = 1,
    val createdAtEpochMs: Long,
    val sources: List<BackupSourceItem>
)

@Serializable
data class BackupSourceItem(
    val name: String,
    val sourceType: String,
    val endpointUrl: String,
    val username: String? = null,
    val plainPasswordForVault: String? = null,
    val epgUrl: String? = null,
    val epgTimeshiftMinutes: Int = 0,
    val userAgent: String? = null,
    val autoUpdateIntervalHours: Int = 12,
    val priorityOrder: Int = 0
)
