package com.vibemax

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Document
import java.net.URLEncoder

class VibemaxProvider : MainAPI() {
    override var mainUrl = "https://vibemax.to"
    override var name = "Vibemax"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        private const val TMDB_IMAGE_URL = "https://image.tmdb.org/t/p/w500"
        private const val TMDB_BACKDROP_URL = "https://image.tmdb.org/t/p/original"
        private const val EMBED_URL = "https://www.zxcstream.xyz"
    }

    // Main page sections
    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies",
        "$mainUrl/tv-shows" to "TV Shows",
        "$mainUrl/anime" to "Anime",
    )

    // ===================== Main Page =====================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val homePageList = mutableListOf<HomePageList>()
        
        // Parse the RSC payload to get show data
        val scriptData = extractRscPayload(document)
        
        if (scriptData.isNotEmpty()) {
            // Parse shows from RSC data
            val sections = parseMainPageSections(scriptData, request.name)
            homePageList.addAll(sections)
        }
        
        // Fallback: Parse from HTML if RSC parsing fails
        if (homePageList.isEmpty()) {
            val items = parseHtmlCards(document)
            if (items.isNotEmpty()) {
                homePageList.add(HomePageList(request.name, items))
            }
        }
        
        return newHomePageResponse(homePageList)
    }

    // ===================== Search =====================
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val document = app.get("$mainUrl/search?q=$encodedQuery").document
        
        val results = mutableListOf<SearchResponse>()
        
        // Try to parse RSC payload first
        val scriptData = extractRscPayload(document)
        if (scriptData.isNotEmpty()) {
            results.addAll(parseSearchResults(scriptData))
        }
        
        // Fallback to HTML parsing
        if (results.isEmpty()) {
            results.addAll(parseHtmlCards(document))
        }
        
        return results
    }

    // ===================== Load Details =====================
    override suspend fun load(url: String): LoadResponse {
        // URL format: /watch/movie/{id} or /watch/tv/{id}
        val isMovie = url.contains("/movie/") || url.contains("type=movie")
        val tmdbId = extractTmdbId(url)
        
        // Fetch page to get metadata
        val watchUrl = if (isMovie) {
            "$mainUrl/watch/movie/$tmdbId"
        } else {
            "$mainUrl/watch/tv/$tmdbId"
        }
        
        val document = app.get(watchUrl).document
        val scriptData = extractRscPayload(document)
        
        // Try to get details from TMDB via the site's data
        val title = extractTitle(scriptData, document) ?: "Unknown"
        val posterPath = extractPosterPath(scriptData)
        val backdropPath = extractBackdropPath(scriptData)
        val overview = extractOverview(scriptData) ?: ""
        val year = extractYear(scriptData)
        val rating = extractRating(scriptData)
        
        val posterUrl = if (posterPath != null) "$TMDB_IMAGE_URL$posterPath" else null
        val backdropUrl = if (backdropPath != null) "$TMDB_BACKDROP_URL$backdropPath" else null
        
        return if (isMovie) {
            newMovieLoadResponse(
                title,
                "$mainUrl/watch/movie/$tmdbId",
                TvType.Movie,
                "$tmdbId|movie"
            ) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl
                this.plot = overview
                this.year = year
                this.rating = rating?.times(10)?.toInt()
            }
        } else {
            // For TV shows, try to fetch episode data
            val episodes = fetchEpisodes(tmdbId)
            
            newTvSeriesLoadResponse(
                title,
                "$mainUrl/watch/tv/$tmdbId",
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl
                this.plot = overview
                this.year = year
                this.rating = rating?.times(10)?.toInt()
            }
        }
    }

    // ===================== Load Links =====================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Data format: "tmdbId|type" or "tmdbId|type|season|episode"
        val parts = data.split("|")
        val tmdbId = parts[0]
        val type = parts.getOrNull(1) ?: "movie"
        val season = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val episode = parts.getOrNull(3)?.toIntOrNull() ?: 1
        
        val embedUrl = if (type == "movie") {
            "$EMBED_URL/player/movie/$tmdbId"
        } else {
            "$EMBED_URL/player/tv/$tmdbId/$season/$episode"
        }
        
        // Try multiple servers
        for (server in 0..2) {
            try {
                val serverUrl = "$embedUrl?autoplay=true&server=$server"
                
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} - Server ${server + 1}",
                        url = serverUrl,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = false,
                        headers = mapOf(
                            "Referer" to mainUrl,
                            "Origin" to mainUrl
                        )
                    )
                )
                
                // Also try to extract actual video URL from embed
                extractVideoFromEmbed(serverUrl, server, callback)
                
            } catch (e: Exception) {
                // Continue to next server
            }
        }
        
        return true
    }

    // ===================== Helper Functions =====================
    
    private fun extractRscPayload(document: Document): String {
        val scripts = document.select("script")
        val payload = StringBuilder()
        
        for (script in scripts) {
            val content = script.data()
            if (content.contains("self.__next_f.push")) {
                // Extract JSON data from RSC payload
                val matches = Regex("""self\.__next_f\.push\(\[1,"(.+?)"\]\)""").findAll(content)
                for (match in matches) {
                    payload.append(match.groupValues[1])
                }
            }
        }
        
        return payload.toString()
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    }

    private fun parseMainPageSections(scriptData: String, pageName: String): List<HomePageList> {
        val sections = mutableListOf<HomePageList>()
        
        try {
            // Find show arrays in the RSC data
            val showPattern = Regex(""""shows":\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            val titlePattern = Regex(""""title":\s*"([^"]+)"""")
            
            // Extract individual show objects
            val itemPattern = Regex("""\{[^{}]*"id":\s*\d+[^{}]*"media_type"[^{}]*\}""")
            val items = itemPattern.findAll(scriptData).map { it.value }.toList()
            
            if (items.isNotEmpty()) {
                val searchResponses = items.mapNotNull { parseShowObject(it) }.distinctBy { it.url }
                if (searchResponses.isNotEmpty()) {
                    sections.add(HomePageList(pageName, searchResponses.take(20)))
                }
            }
        } catch (e: Exception) {
            // Parsing failed, return empty
        }
        
        return sections
    }

    private fun parseSearchResults(scriptData: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        
        try {
            // Find all show/movie objects
            val itemPattern = Regex("""\{[^{}]*"id":\s*(\d+)[^{}]*"media_type":\s*"(movie|tv)"[^{}]*\}""")
            val items = itemPattern.findAll(scriptData)
            
            for (match in items) {
                parseShowObject(match.value)?.let { results.add(it) }
            }
        } catch (e: Exception) {
            // Parsing failed
        }
        
        return results.distinctBy { it.url }
    }

    private fun parseShowObject(json: String): SearchResponse? {
        return try {
            val id = Regex(""""id":\s*(\d+)""").find(json)?.groupValues?.get(1) ?: return null
            val mediaType = Regex(""""media_type":\s*"(movie|tv)"""").find(json)?.groupValues?.get(1) ?: "movie"
            
            // Get title (movies use "title", TV shows use "name")
            val title = if (mediaType == "movie") {
                Regex(""""title":\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            } else {
                Regex(""""name":\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            } ?: return null
            
            val posterPath = Regex(""""poster_path":\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            val posterUrl = if (posterPath != null) "$TMDB_IMAGE_URL$posterPath" else null
            
            // Get year
            val dateField = if (mediaType == "movie") "release_date" else "first_air_date"
            val dateStr = Regex(""""$dateField":\s*"(\d{4})""").find(json)?.groupValues?.get(1)
            val year = dateStr?.toIntOrNull()
            
            val url = "$mainUrl/watch/$mediaType/$id"
            
            if (mediaType == "movie") {
                newMovieSearchResponse(title, url, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
            } else {
                newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHtmlCards(document: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        
        // Select all card elements
        val cards = document.select("div[role=button][aria-label]")
        
        for (card in cards) {
            try {
                val title = card.attr("aria-label").ifEmpty { continue }
                
                // Get link
                val link = card.selectFirst("a[href^='/movie/'], a[href^='/tv/'], a[href^='/watch/']")
                    ?: continue
                val href = link.attr("href")
                
                // Determine type
                val isMovie = href.contains("/movie/")
                val type = if (isMovie) TvType.Movie else TvType.TvSeries
                
                // Get poster
                val img = card.selectFirst("img[src*='image.tmdb.org'], img[data-nimg]")
                val posterUrl = img?.attr("src")?.takeIf { it.isNotEmpty() }
                
                // Get year from the card
                val yearText = card.selectFirst("span.text-white\\/60, span:contains(20)")?.text()
                val year = yearText?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
                
                // Extract TMDB ID from URL
                val tmdbId = Regex("""(\d+)""").find(href)?.groupValues?.get(1) ?: continue
                val watchUrl = "$mainUrl/watch/${if (isMovie) "movie" else "tv"}/$tmdbId"
                
                val searchResponse = if (isMovie) {
                    newMovieSearchResponse(title, watchUrl, type) {
                        this.posterUrl = posterUrl
                        this.year = year
                    }
                } else {
                    newTvSeriesSearchResponse(title, watchUrl, type) {
                        this.posterUrl = posterUrl
                        this.year = year
                    }
                }
                
                results.add(searchResponse)
            } catch (e: Exception) {
                continue
            }
        }
        
        return results.distinctBy { it.url }
    }

    private fun extractTmdbId(url: String): String {
        return Regex("""/(?:movie|tv|watch/movie|watch/tv)/(\d+)""").find(url)?.groupValues?.get(1)
            ?: Regex("""(\d+)""").find(url)?.groupValues?.get(1)
            ?: ""
    }

    private fun extractTitle(scriptData: String, document: Document): String? {
        // Try RSC data first
        val titleFromData = Regex(""""title":\s*"([^"]+)"""").find(scriptData)?.groupValues?.get(1)
            ?: Regex(""""name":\s*"([^"]+)"""").find(scriptData)?.groupValues?.get(1)
        
        if (titleFromData != null) return titleFromData
        
        // Fallback to HTML
        return document.selectFirst("title")?.text()?.replace(" - Vibemax", "")?.trim()
    }

    private fun extractPosterPath(scriptData: String): String? {
        return Regex(""""poster_path":\s*"([^"]+)"""").find(scriptData)?.groupValues?.get(1)
    }

    private fun extractBackdropPath(scriptData: String): String? {
        return Regex(""""backdrop_path":\s*"([^"]+)"""").find(scriptData)?.groupValues?.get(1)
    }

    private fun extractOverview(scriptData: String): String? {
        return Regex(""""overview":\s*"([^"]+)"""").find(scriptData)?.groupValues?.get(1)
    }

    private fun extractYear(scriptData: String): Int? {
        val date = Regex(""""release_date":\s*"(\d{4})""").find(scriptData)?.groupValues?.get(1)
            ?: Regex(""""first_air_date":\s*"(\d{4})""").find(scriptData)?.groupValues?.get(1)
        return date?.toIntOrNull()
    }

    private fun extractRating(scriptData: String): Double? {
        return Regex(""""vote_average":\s*([\d.]+)""").find(scriptData)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private suspend fun fetchEpisodes(tmdbId: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        // Try to get episodes from the TV show page
        // Since vibemax uses TMDB data, we'll create a basic episode structure
        // The actual episode data would come from the site's API
        
        try {
            val watchUrl = "$mainUrl/watch/tv/$tmdbId"
            val document = app.get(watchUrl).document
            val scriptData = extractRscPayload(document)
            
            // Try to find season/episode info
            val numSeasons = Regex(""""number_of_seasons":\s*(\d+)""").find(scriptData)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val numEpisodes = Regex(""""number_of_episodes":\s*(\d+)""").find(scriptData)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 10
            
            // Create episodes for each season
            // For a more accurate implementation, you'd need to fetch season details
            for (season in 1..numSeasons.coerceAtMost(10)) {
                val episodesPerSeason = if (numSeasons > 1) {
                    (numEpisodes / numSeasons).coerceIn(1, 30)
                } else {
                    numEpisodes.coerceIn(1, 30)
                }
                
                for (ep in 1..episodesPerSeason) {
                    episodes.add(
                        Episode(
                            data = "$tmdbId|tv|$season|$ep",
                            name = "Episode $ep",
                            season = season,
                            episode = ep
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback: create basic episode structure
            for (ep in 1..10) {
                episodes.add(
                    Episode(
                        data = "$tmdbId|tv|1|$ep",
                        name = "Episode $ep",
                        season = 1,
                        episode = ep
                    )
                )
            }
        }
        
        return episodes
    }

    private suspend fun extractVideoFromEmbed(
        embedUrl: String,
        serverIndex: Int,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(
                embedUrl,
                headers = mapOf(
                    "Referer" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )
            
            val html = response.text
            
            // Try to find video sources in the embed page
            // Look for m3u8 or mp4 URLs
            val m3u8Pattern = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
            val mp4Pattern = Regex("""(https?://[^\s"']+\.mp4[^\s"']*)""")
            
            m3u8Pattern.findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1].replace("\\", "")
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} - Server ${serverIndex + 1} (HLS)",
                        url = videoUrl,
                        referer = EMBED_URL,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            }
            
            mp4Pattern.findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1].replace("\\", "")
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${this.name} - Server ${serverIndex + 1} (MP4)",
                        url = videoUrl,
                        referer = EMBED_URL,
                        quality = Qualities.Unknown.value,
                        isM3u8 = false
                    )
                )
            }
            
            // Look for file/source configurations in JavaScript
            val sourcePattern = Regex("""["']?(?:file|src|source)["']?\s*[:=]\s*["']([^"']+)["']""")
            sourcePattern.findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4")) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} - Server ${serverIndex + 1}",
                            url = videoUrl,
                            referer = EMBED_URL,
                            quality = Qualities.Unknown.value,
                            isM3u8 = videoUrl.contains(".m3u8")
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            // Failed to extract from embed
        }
    }
}
