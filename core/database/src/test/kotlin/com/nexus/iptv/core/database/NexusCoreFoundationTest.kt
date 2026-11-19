package com.nexus.iptv.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexus.iptv.core.common.security.UrlRedactor
import com.nexus.iptv.core.database.dao.NexusDao
import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.SourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NexusCoreFoundationTest {
    private lateinit var database: NexusDatabase
    private lateinit var dao: NexusDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.nexusDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `UrlRedactor hides Xtream passwords and M3U query tokens`() {
        val xtreamUrl = "http://portal.example.com:8080/live/my_user_login/SecretPass99!/1042.ts"
        val m3uUrl = "https://iptv.example.com/playlist.m3u8?username=admin&password=MySecret123&token=abcdef"

        val redactedXtream = UrlRedactor.redact(xtreamUrl)
        val redactedM3u = UrlRedactor.redact(m3uUrl)

        assertFalse(redactedXtream.contains("SecretPass99!"))
        assertFalse(redactedXtream.contains("my_user_login"))
        assertTrue(redactedXtream.contains("***USER***/***PASS***"))
        assertFalse(redactedM3u.contains("MySecret123"))
        assertFalse(redactedM3u.contains("abcdef"))
    }
}
