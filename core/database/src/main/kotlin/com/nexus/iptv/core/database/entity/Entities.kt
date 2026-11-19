package com.nexus.iptv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_sources", indices = [Index(value = ["is_enabled"]), Index(value = ["priority_order"])])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "endpoint_url") val endpointUrl: String,
    @ColumnInfo(name = "username") val username: String? = null,
    @ColumnInfo(name = "encrypted_password") val encryptedPassword: String? = null,
    @ColumnInfo(name = "epg_url") val epgUrl: String? = null,
    @ColumnInfo(name = "epg_timeshift_minutes") val epgTimeshiftMinutes: Int = 0,
    @ColumnInfo(name = "user_agent") val userAgent: String? = null,
    @ColumnInfo(name = "auto_update_interval_hours") val autoUpdateIntervalHours: Int = 12,
    @ColumnInfo(name = "last_updated_epoch_ms") val lastUpdatedEpochMs: Long = 0L,
    @ColumnInfo(name = "last_sync_status") val lastSyncStatus: String = "IDLE",
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_pin_protected") val isPinProtected: Boolean = false,
    @ColumnInfo(name = "priority_order") val priorityOrder: Int = 0
)

@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["source_id", "category_name"]), Index(value = ["epg_channel_id"]),
        Index(value = ["unified_group_key"]), Index(value = ["is_favorite", "custom_sort_order"]),
        Index(value = ["last_watched_epoch_ms"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "channel_number") val channelNumber: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "logo_url") val logoUrl: String? = null,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "epg_channel_id") val epgChannelId: String? = null,
    @ColumnInfo(name = "manual_epg_channel_id") val manualEpgChannelId: String? = null,
    @ColumnInfo(name = "catchup_type") val catchupType: String? = null,
    @ColumnInfo(name = "catchup_days") val catchupDays: Int = 0,
    @ColumnInfo(name = "catchup_source_template") val catchupSourceTemplate: String? = null,
    @ColumnInfo(name = "unified_group_key") val unifiedGroupKey: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,
    @ColumnInfo(name = "custom_sort_order") val customSortOrder: Int = 0,
    @ColumnInfo(name = "last_watched_epoch_ms") val lastWatchedEpochMs: Long = 0L,
    @ColumnInfo(name = "health_score") val healthScore: Int = 100,
    @ColumnInfo(name = "avg_startup_time_ms") val avgStartupTimeMs: Long = 0L,
    @ColumnInfo(name = "failure_count") val failureCount: Int = 0
)

@Fts4(contentEntity = ChannelEntity::class)
@Entity(tableName = "channels_fts")
data class ChannelFtsEntity(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "category_name") val categoryName: String
)

@Entity(tableName = "epg_programs", indices = [Index(value = ["epg_channel_id", "start_epoch_ms", "end_epoch_ms"]), Index(value = ["end_epoch_ms"])])
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "epg_channel_id") val epgChannelId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "start_epoch_ms") val startEpochMs: Long,
    @ColumnInfo(name = "end_epoch_ms") val endEpochMs: Long,
    @ColumnInfo(name = "has_reminder") val hasReminder: Boolean = false
)

@Entity(
    tableName = "vod_items",
    foreignKeys = [ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["source_id", "content_type", "category_name"]), Index(value = ["is_favorite"]), Index(value = ["last_watched_epoch_ms"])]
)
data class VodItemEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "external_stream_id") val externalStreamId: Long = 0L,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "genre") val genre: String? = null,
    @ColumnInfo(name = "release_year") val releaseYear: Int? = null,
    @ColumnInfo(name = "rating") val rating: Float = 0f,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String? = null,
    @ColumnInfo(name = "stream_url") val streamUrl: String? = null,
    @ColumnInfo(name = "container_extension") val containerExtension: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long = 0L,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "last_watched_epoch_ms") val lastWatchedEpochMs: Long = 0L
)

@Entity(
    tableName = "vod_series_episodes",
    foreignKeys = [ForeignKey(entity = VodItemEntity::class, parentColumns = ["id"], childColumns = ["series_vod_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["series_vod_id", "season_number", "episode_number"])]
)
data class SeriesEpisodeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "series_vod_id") val seriesVodId: Long,
    @ColumnInfo(name = "season_number") val seasonNumber: Int,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "overview") val overview: String? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long = 0L,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "last_watched_epoch_ms") val lastWatchedEpochMs: Long = 0L
)

@Entity(tableName = "dvr_recordings", indices = [Index(value = ["status", "scheduled_start_epoch_ms"]), Index(value = ["channel_id"])])
data class DvrRecordingEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "channel_id") val channelId: Long,
    @ColumnInfo(name = "channel_name") val channelName: String,
    @ColumnInfo(name = "program_title") val programTitle: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "output_file_path") val outputFilePath: String,
    @ColumnInfo(name = "scheduled_start_epoch_ms") val scheduledStartEpochMs: Long,
    @ColumnInfo(name = "scheduled_end_epoch_ms") val scheduledEndEpochMs: Long,
    @ColumnInfo(name = "recorded_bytes") val recordedBytes: Long = 0L,
    @ColumnInfo(name = "status") val status: String = "SCHEDULED",
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false
)
