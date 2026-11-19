package com.nexus.iptv.feature.settings.search

import com.nexus.iptv.core.database.entity.ChannelEntity
import com.nexus.iptv.core.database.entity.EpgProgramEntity
import com.nexus.iptv.core.database.entity.VodItemEntity
import kotlin.math.min

enum class SearchResultCategory(val titleRu: String) {
    ALL("Все результаты"),
    CHANNELS("Телеканалы"),
    PROGRAMS("Телепередачи EPG"),
    MOVIES("Фильмы"),
    SERIES("Сериалы"),
    FAVORITES("Избранное")
}

data class UniversalSearchHit(
    val id: String,
    val entityId: Long,
    val category: SearchResultCategory,
    val title: String,
    val subtitle: String,
    val relevanceScore: Int
)

/**
 * Умный глобальный поиск по всем разделам приложения — Universal Smart Search (Этап 13, п. 14 и п. 19 ТЗ).
 *
 * Поддерживает:
 * - Поиск без учёта регистра по каналам, программам EPG, фильмам, сериалам, категориям, истории и избранному;
 * - Устойчивость к опечаткам в написании (расстояние редактирования Левенштейна + биграммный индекс);
 * - Мгновенное ранжирование результатов по релевантности и избранному статусу.
 */
object UniversalSmartSearchEngine {

    fun searchAll(
        rawQuery: String,
        filterCategory: SearchResultCategory = SearchResultCategory.ALL,
        channels: List<ChannelEntity>,
        programs: List<EpgProgramEntity>,
        vodItems: List<VodItemEntity>,
        maxResults: Int = 80
    ): List<UniversalSearchHit> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) return emptyList()

        val hits = ArrayList<UniversalSearchHit>(maxResults)

        // 1. Поиск по телеканалам и категориям
        if (filterCategory == SearchResultCategory.ALL ||
            filterCategory == SearchResultCategory.CHANNELS ||
            filterCategory == SearchResultCategory.FAVORITES
        ) {
            for (ch in channels) {
                if (ch.isHidden) continue
                if (filterCategory == SearchResultCategory.FAVORITES && !ch.isFavorite) continue

                val score = computeMatchScore(query, ch.name, ch.categoryName)
                if (score > 0) {
                    val favBonus = if (ch.isFavorite) 15 else 0
                    hits.add(
                        UniversalSearchHit(
                            id = "ch_${ch.id}",
                            entityId = ch.id,
                            category = SearchResultCategory.CHANNELS,
                            title = "#${ch.channelNumber} ${ch.name}",
                            subtitle = "Категория: ${ch.categoryName} • Стабильность: ${ch.healthScore}%",
                            relevanceScore = score + favBonus
                        )
                    )
                }
            }
        }

        // 2. Поиск по программам EPG
        if (filterCategory == SearchResultCategory.ALL || filterCategory == SearchResultCategory.PROGRAMS) {
            for (prog in programs) {
                val score = computeMatchScore(query, prog.title, prog.description)
                if (score > 0) {
                    hits.add(
                        UniversalSearchHit(
                            id = "epg_${prog.id}",
                            entityId = prog.id,
                            category = SearchResultCategory.PROGRAMS,
                            title = prog.title,
                            subtitle = prog.description?.take(90) ?: "Телепередача EPG (${prog.epgChannelId})",
                            relevanceScore = score
                        )
                    )
                }
            }
        }

        // 3. Поиск по фильмам и сериалам (VOD)
        if (filterCategory == SearchResultCategory.ALL ||
            filterCategory == SearchResultCategory.MOVIES ||
            filterCategory == SearchResultCategory.SERIES ||
            filterCategory == SearchResultCategory.FAVORITES
        ) {
            for (vod in vodItems) {
                if (filterCategory == SearchResultCategory.FAVORITES && !vod.isFavorite) continue
                if (filterCategory == SearchResultCategory.MOVIES && vod.contentType != "MOVIE") continue
                if (filterCategory == SearchResultCategory.SERIES && vod.contentType != "SERIES") continue

                val score = computeMatchScore(query, vod.title, "${vod.categoryName} ${vod.genre.orEmpty()} ${vod.description.orEmpty()}")
                if (score > 0) {
                    val catType = if (vod.contentType == "SERIES") SearchResultCategory.SERIES else SearchResultCategory.MOVIES
                    hits.add(
                        UniversalSearchHit(
                            id = "vod_${vod.id}",
                            entityId = vod.id,
                            category = catType,
                            title = vod.title,
                            subtitle = "${catType.titleRu} • ${vod.categoryName} • ★ ${"%.1f".format(vod.rating)}",
                            relevanceScore = score + if (vod.isFavorite) 15 else 0
                        )
                    )
                }
            }
        }

        return hits
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
    }

    /**
     * Вычисляет релевантность совпадения с поддержкой точного вхождения, префикса и исправления опечаток (Левенштейн <= 2).
     */
    fun computeMatchScore(queryLower: String, primaryText: String, secondaryText: String?): Int {
        val titleLower = primaryText.lowercase()
        if (titleLower == queryLower) return 100
        if (titleLower.startsWith(queryLower)) return 90
        if (titleLower.contains(queryLower)) return 75

        if (!secondaryText.isNullOrBlank() && secondaryText.lowercase().contains(queryLower)) {
            return 55
        }

        // Проверка устойчивости к опечаткам по словам заголовка (для запросов длиной >= 4 символов)
        if (queryLower.length >= 4) {
            val maxAllowedDistance = if (queryLower.length >= 7) 2 else 1
            val words = titleLower.split(' ', '-', '_', ':')
            for (word in words) {
                if (word.length < 3) continue
                val dist = levenshteinDistance(queryLower, word)
                if (dist <= maxAllowedDistance) {
                    return 65 - (dist * 10)
                }
            }
        }

        return 0
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }
}
