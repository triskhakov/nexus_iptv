package com.nexus.iptv.core.player.di

import com.nexus.iptv.core.player.contract.VideoPlayerEngine
import com.nexus.iptv.core.player.media3.Media3VideoPlayerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    @Singleton
    abstract fun bindVideoPlayerEngine(impl: Media3VideoPlayerEngine): VideoPlayerEngine
}
