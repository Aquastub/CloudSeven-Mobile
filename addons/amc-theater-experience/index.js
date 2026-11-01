'use strict';

const MAX_TRAILERS = 8;
const MAX_URL_LENGTH = 4096;
const TMDB_BASE = 'https://api.themoviedb.org/3';
const TRAILER_CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const trailerCache = new Map();

function cleanHttpUrl(value) {
  if (typeof value !== 'string') return null;
  const url = value.trim();
  if (!url || url.length > MAX_URL_LENGTH) return null;
  if (!/^https?:\/\//i.test(url)) return null;
  return url;
}

function makeClip(url, title, metadata) {
  const safeUrl = cleanHttpUrl(url);
  if (!safeUrl) return null;
  const clip = { url: safeUrl };
  if (typeof title === 'string' && title.trim()) clip.title = title.trim().slice(0, 100);
  if (metadata && typeof metadata === 'object') {
    if (typeof metadata.movieId === 'number') clip.movieId = metadata.movieId;
    if (typeof metadata.releaseDate === 'string') clip.releaseDate = metadata.releaseDate;
    if (typeof metadata.source === 'string') clip.source = metadata.source;
  }
  return clip;
}

function youtubeWatchUrl(key) {
  if (typeof key !== 'string' || !/^[A-Za-z0-9_-]{11}$/.test(key.trim())) return null;
  return 'https://www.youtube.com/watch?v=' + key.trim();
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function addDaysIso(days) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

async function tmdbGet(path, apiKey, params) {
  const query = Object.assign({ api_key: apiKey }, params || {});
  const queryString = Object.keys(query)
    .filter(function (key) { return query[key] !== undefined && query[key] !== null && String(query[key]).length > 0; })
    .map(function (key) { return encodeURIComponent(key) + '=' + encodeURIComponent(String(query[key])); })
    .join('&');
  const response = await fetch(TMDB_BASE + path + '?' + queryString, {
    method: 'GET',
    headers: { 'Accept': 'application/json' },
  });
  if (!response.ok) throw new Error('TMDB returned HTTP ' + response.status + ' for ' + path);
  return response.json();
}

function chooseTrailer(videos, preferredLanguage) {
  const entries = videos && Array.isArray(videos.results) ? videos.results : [];
  const candidates = entries.filter(function (video) {
    return video && video.site === 'YouTube' && video.type === 'Trailer' && youtubeWatchUrl(video.key);
  });
  candidates.sort(function (a, b) {
    const score = function (video) {
      let value = 0;
      if (video.official === true) value += 100;
      if (video.iso_639_1 === preferredLanguage) value += 30;
      if (video.iso_639_1 === 'en') value += 15;
      if (video.size >= 1080) value += 10;
      else if (video.size >= 720) value += 5;
      if (/official/i.test(video.name || '')) value += 3;
      return value;
    };
    return score(b) - score(a);
  });
  return candidates[0] || null;
}

async function mapWithConcurrency(items, limit, mapper) {
  const results = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async function () {
    while (true) {
      const index = cursor++;
      if (index >= items.length) return;
      try { results[index] = await mapper(items[index], index); }
      catch (_) { results[index] = null; }
    }
  });
  await Promise.all(workers);
  return results;
}

async function detectUpcomingTrailers(settings) {
  const apiKey = typeof settings.tmdbApiKey === 'string' ? settings.tmdbApiKey.trim() : '';
  if (!apiKey) return [];

  const region = (settings.releaseRegion || 'US').trim().toUpperCase().slice(0, 2);
  const language = (settings.trailerLanguage || 'en-US').trim().slice(0, 10);
  const preferredLanguage = language.split('-')[0].toLowerCase();
  const maxDays = Math.max(7, Math.min(180, Number(settings.releaseWindowDays) || 90));
  const cacheKey = [apiKey, region, language, maxDays].join('|');
  const cached = trailerCache.get(cacheKey);
  if (cached && Date.now() - cached.at < TRAILER_CACHE_TTL_MS) return cached.trailers.slice();

  const today = todayIso();
  const until = addDaysIso(maxDays);
  const upcoming = await tmdbGet('/movie/upcoming', apiKey, {
    region: region,
    language: language,
    page: 1,
  });
  const movies = (Array.isArray(upcoming.results) ? upcoming.results : [])
    .filter(function (movie) {
      return movie && Number(movie.id) > 0 && typeof movie.release_date === 'string' &&
        movie.release_date >= today && movie.release_date <= until;
    })
    .sort(function (a, b) {
      if (a.release_date !== b.release_date) return a.release_date.localeCompare(b.release_date);
      return (Number(b.popularity) || 0) - (Number(a.popularity) || 0);
    })
    .slice(0, 36);

  const detected = await mapWithConcurrency(movies, 4, async function (movie) {
    const videos = await tmdbGet('/movie/' + Number(movie.id) + '/videos', apiKey, { language: language });
    const trailer = chooseTrailer(videos, preferredLanguage);
    if (!trailer) return null;
    const url = youtubeWatchUrl(trailer.key);
    if (!url) return null;
    return makeClip(url, (movie.title || movie.original_title || 'Upcoming movie') + ' — ' + (trailer.name || 'Official Trailer'), {
      movieId: Number(movie.id),
      releaseDate: movie.release_date,
      source: 'TMDB/YouTube',
    });
  });

  const unique = [];
  const seen = new Set();
  detected.forEach(function (clip) {
    if (!clip || seen.has(clip.url)) return;
    seen.add(clip.url);
    unique.push(clip);
  });
  const result = unique.slice(0, MAX_TRAILERS);
  trailerCache.set(cacheKey, { at: Date.now(), trailers: result });
  return result.slice();
}

async function loadCustomTrailerFeed(feedUrl) {
  const url = cleanHttpUrl(feedUrl);
  if (!url) return [];
  const response = await fetch(url, {
    method: 'GET',
    headers: { 'Accept': 'application/json' },
  });
  if (!response.ok) throw new Error('Trailer feed returned HTTP ' + response.status);
  const payload = await response.json();
  const entries = Array.isArray(payload)
    ? payload
    : (payload && Array.isArray(payload.trailers) ? payload.trailers : []);
  return entries.slice(0, MAX_TRAILERS).map(function (entry) {
    if (typeof entry === 'string') return makeClip(entry, 'Upcoming movie trailer', { source: 'Custom feed' });
    if (!entry || typeof entry !== 'object') return null;
    return makeClip(entry.url || entry.videoUrl, entry.title || entry.movieTitle || 'Upcoming movie trailer', {
      releaseDate: entry.releaseDate,
      source: 'Custom feed',
    });
  }).filter(Boolean);
}

async function loadTrailers(settings) {
  const mode = (settings.trailerDetectionMode || 'tmdb').toLowerCase();
  if (mode !== 'feed') {
    try {
      const detected = await detectUpcomingTrailers(settings);
      if (detected.length) return detected;
    } catch (error) {
      console.error('AMC automatic trailer detection failed:', error && error.message ? error.message : error);
    }
  }
  try {
    return await loadCustomTrailerFeed(settings.trailerFeedUrl);
  } catch (error) {
    console.error('AMC custom trailer feed unavailable:', error && error.message ? error.message : error);
    return [];
  }
}

async function handle(request) {
  if (!request || request.resource !== 'preroll') return {};
  const settings = request.settings || {};
  const clips = [];
  const add = function (url, title) {
    const clip = makeClip(url, title);
    if (clip) clips.push(clip);
  };

  add(settings.preRollUrl, '2026 AMC Theaters Pre-roll');

  const trailers = await loadTrailers(settings);
  trailers.slice(0, MAX_TRAILERS).forEach(function (clip, index) {
    clips.push({
      url: clip.url,
      title: clip.title || ('Upcoming trailer ' + (index + 1)),
    });
  });

  add(settings.featurePresentationUrl, '2026 AMC Theaters Feature Presentation');
  add(settings.dolbyIntroUrl, 'Dolby at AMC');
  add(settings.enjoyShowUrl, '2026 AMC Theaters Feature Presentation — Enjoy the Show');

  if (!clips.length) return {};
  return { clips: clips.slice(0, 12) };
}

module.exports = { handle: handle, detectUpcomingTrailers: detectUpcomingTrailers };
