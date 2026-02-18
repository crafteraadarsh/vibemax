// VibemaxProvider build configuration
version = 1

cloudstream {
    description = "Watch movies & TV shows from VibeMax - Supports multiple servers and downloads"
    authors = listOf("aadarshsingh1421-oss")
    
    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    tvTypes = listOf("Movie", "TvSeries")
    
    language = "en"
    
    iconUrl = "https://vibemax.to/images/icon-192x192.png"
}
