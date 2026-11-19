package com.nexus.iptv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.ChannelFtsEntity
import com.nexus.iptv.core.database.entity.DvrRecordingEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.SeriesEpisodeEntity
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.database.entity.VodItemEntity

@Database(
    entities = [
        SourceEntity::class,
        ChannelEntity::class,
        ChannelFtsEntity::class,
        EpgProgramEntity::class,
        VodItemEntity::class,
        SeriesEpisodeEntity::class,
        DvrRecordingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun nexusDao(): NexusDao
}
