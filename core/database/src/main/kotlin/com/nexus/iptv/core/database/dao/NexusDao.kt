package com.nexus.iptv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.SeriesEpisodeEntity
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NexusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSource(source: SourceEntity): Long
    @Update suspend fun updateSource(source: SourceEntity)
    @Query("DELETE FROM iptv_sources WHERE id = :sourceId") suspend fun deleteSourceById(sourceId: Long)
    @Query("SELECT * FROM iptv_sources WHERE id = :sourceId LIMIT 1") suspend fun getSourceById(sourceId: Long): SourceEntity?
    @Query("SELECT * FROM iptv_sources WHERE is_enabled = 1 ORDER BY priority_order ASC, id ASC") suspend fun getEnabledSources(): List<SourceEntity>
    @Query("SELECT * FROM iptv_sources ORDER BY priority_order ASC, id ASC") fun observeSources(): Flow<List<SourceEntity>>
    @Query("UPDATE iptv_sources SET last_updated_epoch_ms = :timestampMs, last_sync_status = :status, epg_url = COALESCE(:discoveredEpgUrl, epg_url) WHERE id = :sourceId")
    suspend fun updateSourceSyncStatus(sourceId: Long, timestampMs: Long, status: String, discoveredEpgUrl: String? = null)
    @Query("UPDATE iptv_sources SET epg_timeshift_minutes = :shiftMinutes WHERE id = :sourceId") suspend fun updateSourceEpgTimeshift(sourceId: Long, shiftMinutes: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChannelsBatch(channels: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE source_id = :sourceId") suspend fun deleteChannelsBySource(sourceId: Long)
    @Transaction suspend fun replaceChannelsForSource(sourceId: Long, channels: List<ChannelEntity>) {
        deleteChannelsBySource(sourceId)
        channels.chunked(500).forEach { insertChannelsBatch(it) }
    }
    @Query("SELECT COUNT(*) FROM channels WHERE source_id = :sourceId") suspend fun getChannelCountForSource(sourceId: Long): Int
    @Query("SELECT * FROM channels WHERE is_hidden = 0 AND (:sourceId IS NULL OR source_id = :sourceId) AND (:category IS NULL OR category_name = :category) ORDER BY is_favorite DESC, custom_sort_order ASC, channel_number ASC")
    fun getChannelsPagingSource(sourceId: Long?, category: String?): PagingSource<Int, ChannelEntity>
    @Query("SELECT * FROM channels WHERE is_hidden = 0 AND (:sourceId IS NULL OR source_id = :sourceId) AND (:category IS NULL OR category_name = :category) ORDER BY is_favorite DESC, custom_sort_order ASC, channel_number ASC")
    fun observeVisibleChannels(sourceId: Long?, category: String?): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE source_id = :sourceId AND is_hidden = 0") suspend fun getChannelsBySource(sourceId: Long): List<ChannelEntity>
    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1") suspend fun getChannelById(channelId: Long): ChannelEntity?
    @Query("SELECT * FROM channels WHERE channel_number = :number AND is_hidden = 0 ORDER BY is_favorite DESC, health_score DESC LIMIT 1") suspend fun getChannelByNumber(number: Int): ChannelEntity?
    @Query("UPDATE channels SET is_favorite = :isFavorite WHERE id = :channelId") suspend fun setChannelFavorite(channelId: Long, isFavorite: Boolean)
    @Query("UPDATE channels SET is_hidden = :isHidden WHERE id = :channelId") suspend fun setChannelHidden(channelId: Long, isHidden: Boolean)
    @Query("UPDATE channels SET is_locked = :isLocked WHERE id = :channelId") suspend fun setChannelLocked(channelId: Long, isLocked: Boolean)
    @Query("UPDATE channels SET custom_sort_order = :sortOrder WHERE id = :channelId") suspend fun setChannelCustomSortOrder(channelId: Long, sortOrder: Int)
    @Query("UPDATE channels SET last_watched_epoch_ms = :timestampMs WHERE id = :channelId") suspend fun recordChannelWatchTimestamp(channelId: Long, timestampMs: Long)
    @Query("UPDATE channels SET manual_epg_channel_id = :manualEpgId WHERE id = :channelId") suspend fun setChannelManualEpgId(channelId: Long, manualEpgId: String?)
    @Query("UPDATE channels SET epg_channel_id = :autoEpgId WHERE id = :channelId AND manual_epg_channel_id IS NULL") suspend fun setChannelAutoMatchedEpgId(channelId: Long, autoEpgId: String)
    @Query("SELECT * FROM channels WHERE is_favorite = 1 AND is_hidden = 0 ORDER BY custom_sort_order ASC LIMIT :limit") fun observeFavoriteChannels(limit: Int = 20): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE last_watched_epoch_ms > 0 AND is_hidden = 0 ORDER BY last_watched_epoch_ms DESC LIMIT :limit") fun observeRecentChannels(limit: Int = 20): Flow<List<ChannelEntity>>
    @Query("SELECT DISTINCT category_name FROM channels WHERE is_hidden = 0 ORDER BY category_name ASC LIMIT :limit") fun observePopularCategories(limit: Int = 64): Flow<List<String>>
    @Query("SELECT channels.* FROM channels JOIN channels_fts ON channels.rowid = channels_fts.rowid WHERE channels_fts MATCH :ftsQuery AND channels.is_hidden = 0 ORDER BY channels.is_favorite DESC, channels.health_score DESC LIMIT :limit")
    suspend fun searchChannelsFts(ftsQuery: String, limit: Int = 100): List<ChannelEntity>
    @Query("SELECT * FROM channels WHERE unified_group_key = :unifiedKey AND id != :excludeChannelId AND is_hidden = 0 ORDER BY health_score DESC, avg_startup_time_ms ASC")
    suspend fun findAlternativeStreams(unifiedKey: String, excludeChannelId: Long): List<ChannelEntity>
    @Query("UPDATE channels SET health_score = :newHealthScore, avg_startup_time_ms = :avgStartupMs, failure_count = :failureCount WHERE id = :channelId")
    suspend fun updateChannelHealthMetrics(channelId: Long, newHealthScore: Int, avgStartupMs: Long, failureCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEpgBatch(programs: List<EpgProgramEntity>)
    @Query("DELETE FROM epg_programs WHERE source_id = :sourceId") suspend fun deleteEpgBySource(sourceId: Long)
    @Query("DELETE FROM epg_programs WHERE end_epoch_ms < :expiredBeforeEpochMs") suspend fun deleteExpiredEpgPrograms(expiredBeforeEpochMs: Long)
    @Query("SELECT * FROM epg_programs WHERE epg_channel_id = :epgChannelId AND end_epoch_ms > :nowEpochMs ORDER BY start_epoch_ms ASC LIMIT 2")
    suspend fun getCurrentAndNextPrograms(epgChannelId: String, nowEpochMs: Long): List<EpgProgramEntity>
    @Query("SELECT * FROM epg_programs WHERE epg_channel_id = :epgChannelId AND end_epoch_ms > :windowStartMs AND start_epoch_ms < :windowEndMs ORDER BY start_epoch_ms ASC")
    suspend fun getProgramsForChannelInWindow(epgChannelId: String, windowStartMs: Long, windowEndMs: Long): List<EpgProgramEntity>
    @Query("SELECT * FROM epg_programs WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND end_epoch_ms > :nowEpochMs ORDER BY start_epoch_ms ASC LIMIT :limit")
    suspend fun searchEpgPrograms(query: String, nowEpochMs: Long, limit: Int = 100): List<EpgProgramEntity>
    @Query("UPDATE epg_programs SET has_reminder = :hasReminder WHERE id = :programId") suspend fun setProgramReminder(programId: Long, hasReminder: Boolean)
    @Query("SELECT * FROM epg_programs WHERE has_reminder = 1 AND start_epoch_ms >= :nowEpochMs ORDER BY start_epoch_ms ASC") fun observeScheduledReminders(nowEpochMs: Long): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVodItemsBatch(items: List<VodItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSeriesEpisodesBatch(episodes: List<SeriesEpisodeEntity>)
    @Query("DELETE FROM vod_items WHERE source_id = :sourceId") suspend fun deleteVodBySource(sourceId: Long)
    @Query("SELECT * FROM vod_items WHERE (:contentType IS NULL OR content_type = :contentType) AND (:category IS NULL OR category_name = :category) ORDER BY is_favorite DESC, rating DESC, title ASC")
    fun observeVodItems(contentType: String?, category: String?): Flow<List<VodItemEntity>>
    @Query("SELECT DISTINCT category_name FROM vod_items WHERE (:contentType IS NULL OR content_type = :contentType) ORDER BY category_name ASC")
    fun observeVodCategories(contentType: String?): Flow<List<String>>
    @Query("SELECT * FROM vod_items WHERE last_position_ms > 0 AND last_watched_epoch_ms > 0 ORDER BY last_watched_epoch_ms DESC LIMIT :limit")
    fun observeContinueWatchingVod(limit: Int = 20): Flow<List<VodItemEntity>>
    @Query("SELECT * FROM vod_series_episodes WHERE series_vod_id = :seriesVodId ORDER BY season_number ASC, episode_number ASC")
    fun observeSeriesEpisodes(seriesVodId: Long): Flow<List<SeriesEpisodeEntity>>
    @Query("UPDATE vod_items SET is_favorite = :isFavorite WHERE id = :vodId") suspend fun setVodFavorite(vodId: Long, isFavorite: Boolean)
    @Query("UPDATE vod_items SET last_position_ms = :positionMs, duration_ms = :durationMs, last_watched_epoch_ms = :watchedEpochMs WHERE id = :vodId")
    suspend fun updateVodPlaybackProgress(vodId: Long, positionMs: Long, durationMs: Long, watchedEpochMs: Long)
    @Query("UPDATE vod_series_episodes SET last_position_ms = :positionMs, duration_ms = :durationMs, last_watched_epoch_ms = :watchedEpochMs WHERE id = :episodeId")
    suspend fun updateEpisodePlaybackProgress(episodeId: Long, positionMs: Long, durationMs: Long, watchedEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDvrRecording(recording: DvrRecordingEntity): Long
    @Query("SELECT * FROM dvr_recordings ORDER BY scheduled_start_epoch_ms DESC") fun observeDvrRecordings(): Flow<List<DvrRecordingEntity>>
    @Query("SELECT * FROM dvr_recordings WHERE id = :recordingId LIMIT 1") suspend fun getDvrRecordingById(recordingId: Long): DvrRecordingEntity?
    @Query("SELECT * FROM dvr_recordings WHERE status = 'COMPLETED' AND is_pinned = 0 ORDER BY scheduled_start_epoch_ms ASC") suspend fun getCompletedUnpinnedRecordingsOldestFirst(): List<DvrRecordingEntity>
    @Query("UPDATE dvr_recordings SET status = :status, recorded_bytes = :bytes, failure_reason = :failureReason WHERE id = :recordingId")
    suspend fun updateDvrRecordingStatus(recordingId: Long, status: String, bytes: Long, failureReason: String? = null)
    @Query("UPDATE dvr_recordings SET is_pinned = :isPinned WHERE id = :recordingId") suspend fun setDvrRecordingPinned(recordingId: Long, isPinned: Boolean)
    @Query("DELETE FROM dvr_recordings WHERE id = :recordingId") suspend fun deleteDvrRecordingById(recordingId: Long)
}
