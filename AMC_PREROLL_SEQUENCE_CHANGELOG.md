# Cloud 7 AMC preroll sequence integration

## Player change

The local addon preroll contract now accepts either the legacy single-clip response:

```json
{ "url": "https://media.example/clip.mp4" }
```

or a sequence response:

```json
{
  "clips": [
    { "title": "Opening bumper", "url": "https://media.example/opening.mp4" },
    { "title": "Trailer", "url": "https://media.example/trailer.mp4" }
  ]
}
```

The player presents each clip in order. The viewer can skip the current clip; playback advances to the next clip. The episode is released only after the sequence ends or every clip fails/times out. Existing single-clip addons remain supported.

## AMC addon

`addons/amc-theater-experience/` contains the installable addon source. It builds a queue in this order:

1. Pre-roll
2–9. Up to eight trailers from a configured JSON feed
10. Feature Presentation
11. Dolby at AMC
12. Enjoy the Show

The addon contains no copyrighted AMC, Dolby, studio, or trailer video. Users configure direct-playable media URLs they have permission to use.

## Automatic trailer discovery update

The AMC addon now supports TMDB-powered upcoming-movie discovery:

- Queries TMDB `/movie/upcoming` for the configured release region and date window.
- Fetches each candidate movie's `/videos` metadata with bounded concurrency.
- Selects YouTube videos of type `Trailer`, preferring official videos and the configured language.
- Orders candidates by release date and popularity, removes duplicate YouTube IDs, and caches the list in memory for six hours.
- Uses a custom JSON feed as fallback, or exclusively when discovery mode is `feed`.

The preroll overlay now recognizes YouTube watch URLs and resolves them through the existing `TrailerPlaybackResolver` used by Cloud 7's in-app trailer feature. Builds whose resolver implementation is unavailable will skip those clips; direct MP4/HLS clips continue to work.

TMDB API access requires a user-supplied API key in the addon settings. TMDB provides metadata and YouTube video IDs; it does not host or grant rights to trailer media. Android compilation was not verified in this environment because Gradle distribution download is unavailable.
