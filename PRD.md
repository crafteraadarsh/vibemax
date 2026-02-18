# VibemaxProvider - CloudStream Extension PRD

## Overview

This document outlines the implementation plan for creating a CloudStream extension for **vibemax.to**, a streaming site for movies and TV shows.

## Site Analysis Summary

| Feature | Details |
|---------|---------|
| **Site URL** | https://vibemax.to |
| **Site Type** | Next.js app using TMDB data |
| **Content Types** | Movies & TV Shows |
| **Video Source** | `zxcstream.xyz` iframe player |
| **Search** | URL-based: `/search?q={query}` |
| **Data Source** | TMDB API (server-side rendered) |

## URL Patterns

| Type | Pattern | Example |
|------|---------|---------|
| **Homepage** | `/` | - |
| **Movies Page** | `/movies` | - |
| **TV Shows Page** | `/tv-shows` | - |
| **Anime Page** | `/anime` | - |
| **Search** | `/search?q={query}` | `/search?q=stranger` |
| **Movie Watch** | `/watch/movie/{tmdb_id}` | `/watch/movie/1236153` |
| **TV Watch** | `/watch/tv/{tmdb_id}` | `/watch/tv/66732` |
| **Movie Stream** | `zxcstream.xyz/player/movie/{id}` | `/player/movie/1236153` |
| **TV Stream** | `zxcstream.xyz/player/tv/{id}/{season}/{episode}` | `/player/tv/66732/1/1` |

## Content Card Structure (HTML Selectors)

### Movie/TV Card
```css
/* Card container */
div.group\/card.relative.cursor-pointer.px-1

/* Link to detail */
a[href^="/movie/"] or a[href^="/tv/"]

/* Poster image */
img[data-nimg="fill"]

/* Title */
p.text-sm.font-semibold.text-white.line-clamp-2

/* Year */
span.text-xs.text-white\/60

/* Rating/Match */
span.text-xs.font-semibold.text-green-400
```

### Image URLs
- **Poster**: `https://image.tmdb.org/t/p/w500/{poster_path}`
- **Backdrop**: `https://image.tmdb.org/t/p/original/{backdrop_path}`

## Data Extraction Strategy

### RSC Payload Parsing
The site uses Next.js React Server Components. Data is embedded in `<script>` tags:
```javascript
self.__next_f.push([1, "JSON_DATA_HERE"])
```

### Movie/TV Object Structure (from TMDB)
```json
{
  "id": 1236153,
  "title": "Movie Title",           // Movies use "title"
  "name": "TV Show Name",           // TV shows use "name"
  "media_type": "movie",            // or "tv"
  "poster_path": "/path.jpg",
  "backdrop_path": "/path.jpg",
  "overview": "Description...",
  "vote_average": 7.5,
  "release_date": "2024-01-01",     // Movies
  "first_air_date": "2024-01-01",   // TV shows
  "genre_ids": [28, 12]
}
```

## Stream Extraction

### Watch Page Structure
The watch pages embed an iframe:
```html
<iframe src="https://www.zxcstream.xyz/player/movie/{id}?autoplay=true&server=0" />
```

### Server Support
Multiple servers available via `server` parameter:
- `?server=0` - Primary server
- `?server=1` - Backup server 1
- `?server=2` - Backup server 2

## Extension Architecture

### Directory Structure
```
vibemax/
├── .github/
│   └── workflows/
│       └── build.yml
├── VibemaxProvider/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/vibemax/
│       ├── VibemaxPlugin.kt
│       └── VibemaxProvider.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── gradle/wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
```

### VibemaxProvider.kt - Key Methods

| Method | Purpose |
|--------|---------|
| `getMainPage()` | Returns homepage categories (Trending, Popular, etc.) |
| `search(query)` | Searches content via `/search?q={query}` |
| `load(url)` | Loads movie/TV details, episodes for TV shows |
| `loadLinks()` | Extracts stream URLs from `zxcstream.xyz` |

## Features Implementation

### 1. Main Page Categories
- Trending Now
- Netflix Movies/TV Shows
- Popular
- Comedy Movies
- Action Movies
- Romance Movies
- Scary Movies

### 2. Search
- Full-text search
- Returns both movies and TV shows
- Distinguishes by `media_type` field

### 3. Movie Support
- Title, year, rating, poster, backdrop
- Overview/description
- Genre tags
- Direct play link

### 4. TV Series Support
- Season listing
- Episode listing per season
- Episode titles and descriptions
- Season/episode navigation

### 5. Multiple Servers
- Server 0 (Primary)
- Server 1 (Backup)
- Server 2 (Backup)

### 6. Download Support
- Stream URLs compatible with download managers
- Direct video links when available

## Technical Notes

### Dependencies
- CloudStream3 API
- Jsoup for HTML parsing
- OkHttp for network requests

### Supported Types
```kotlin
override val supportedTypes = setOf(
    TvType.Movie,
    TvType.TvSeries
)
```

### Language
```kotlin
override var lang = "en"
```

## Build & Deploy

### Local Build
```bash
# Windows
.\gradlew.bat VibemaxProvider:make

# Linux/Mac
./gradlew VibemaxProvider:make
```

### Deploy to Device
```bash
# Windows
.\gradlew.bat VibemaxProvider:deployWithAdb

# Linux/Mac
./gradlew VibemaxProvider:deployWithAdb
```

### GitHub Actions
Automatic builds on push via `.github/workflows/build.yml`

## Repository Distribution

### repo.json
```json
{
    "name": "Vibemax Repository",
    "description": "CloudStream extension for vibemax.to",
    "manifestVersion": 1,
    "pluginLists": [
        "https://raw.githubusercontent.com/crafteraadarsh/vibemax/builds/plugins.json"
    ]
}
```

Users can add the repository using:
```
https://raw.githubusercontent.com/crafteraadarsh/vibemax/main/repo.json
```

## Version History

| Version | Changes |
|---------|---------|
| 1 | Initial release with movies, TV shows, search, multiple servers |

## Author

- **GitHub**: crafteraadarsh
- **Repository**: https://github.com/crafteraadarsh/vibemax
