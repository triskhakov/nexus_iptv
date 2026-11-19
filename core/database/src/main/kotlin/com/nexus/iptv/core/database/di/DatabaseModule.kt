package com.nexus.iptv.core.database.di

import android.content.Context
import androidx.room.Room
import com.nexus.iptv.core.database.NexusDatabase
import com.nexus.iptv.core.database.dao.NexusDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNexusDatabase(@ApplicationContext context: Context): NexusDatabase {
        return Room.databaseBuilder(
            context,
            NexusDatabase::class.java,
            "nexus_iptv_production_v3.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNexusDao(database: NexusDatabase): NexusDao = database.nexusDao()
}
