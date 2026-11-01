# AMC Theater Experience for Cloud 7

An installable `.adinst` addon that requests a sequential cinema-style preroll playlist.

## Sequence

1. 2026 AMC Theaters Pre-roll
2–9. Up to eight automatically detected upcoming-movie trailers
10. 2026 AMC Theaters Feature Presentation
11. Dolby at AMC intro
12. 2026 AMC Theaters Feature Presentation — Enjoy the Show

The package contains no AMC, Dolby, studio, or trailer videos. Configure bumper URLs for media you own or are authorized to use.

## Automatic trailer detection

The addon uses the TMDB API to:

1. Query upcoming movie releases for the selected region and release window.
2. Fetch each candidate movie's video metadata.
3. Select YouTube videos whose type is `Trailer`, preferring official entries and the configured language.
4. Sort candidates by upcoming release date, then popularity, and select up to eight unique trailers.
5. Cache the discovered list in memory for six hours to avoid repeatedly querying TMDB for every episode.

The addon uses YouTube video IDs returned by TMDB. Cloud 7's full-build preroll player resolves YouTube watch URLs through its existing in-app trailer resolver before playback. Builds without that resolver will skip YouTube clips; direct MP4/HLS URLs in the fallback feed remain supported.

### Settings

- **TMDB API key**: required for automatic discovery. Obtain your own API key from TMDB.
- **Release region**: defaults to `US`, appropriate for the AMC-style release lineup.
- **Trailer language**: defaults to `en-US`.
- **Release window**: defaults to 90 days, bounded to 7–180 days.
- **Trailer discovery mode**: `tmdb` (default) attempts automatic discovery first; `feed` uses only the custom feed.
- **Fallback feed URL**: optional JSON feed, used if TMDB is unavailable or no suitable trailers are found.

### Fallback feed format

```json
{
  "trailers": [
    { "title": "Movie title — Official Trailer", "url": "https://media.example/trailer.mp4" },
    { "title": "Another movie — Official Trailer", "url": "https://media.example/trailer.m3u8" }
  ]
}
```

YouTube watch-page URLs in a custom feed are also supported by the Cloud 7 full-build preroll player, which resolves them through the in-app trailer resolver. Other builds should use direct-playable MP4/HLS URLs.

## Attribution and limitations

This product uses the TMDB API but is not endorsed or certified by TMDB. TMDB supplies release and video metadata; it does not host or grant rights to the trailer media. YouTube playback depends on the configured Cloud 7 build's in-app resolver and the availability of the video. Some videos may be region-restricted, removed, age-restricted, or unavailable for extraction. The addon skips unavailable trailers and continues the sequence.

AMC and Dolby names are used to describe the requested cinema-style sequence. This is not an official AMC or Dolby product.
