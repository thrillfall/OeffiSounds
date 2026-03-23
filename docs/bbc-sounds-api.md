# BBC Sounds API

Notes on the public BBC RMS API as used for podcast search and playback.

## Search

**Endpoint:** `GET https://rms.api.bbc.co.uk/v2/experience/inline/search?q={encoded_query}&limit=24`

No authentication required. The `/my/` variant of this path (`/v2/my/experience/...`) requires a BBC account session and returns 401.

### Response structure

The top-level `data` array contains up to three **modules**, not items directly:

```json
{
  "data": [
    { "id": "category_search",   "title": "Categories", "data": [ ...category items... ] },
    { "id": "container_search",  "title": "Shows",      "data": [ ...show items... ] },
    { "id": "playable_search",   "title": "Episodes",   "data": [ ...episode items... ] }
  ]
}
```

### Show items (`container_search`)

Each item in the Shows module represents a BBC radio show or series. **Not all results have a podcast RSS feed** — the API returns any radio programme, including local radio, school radio, and archive series that BBC has not released as podcasts. See [RSS feed availability](#rss-feed-availability) below.

| Field | Description |
|---|---|
| `id` | BBC PID (e.g. `p02nq0gn`) — used to build the RSS feed URL |
| `titles.primary` | Show title |
| `titles.secondary` | Usually `null` at series level |
| `network.short_title` | Broadcaster name (e.g. `"World Service"`, `"Radio 4"`) — used as author |
| `image_url` | Image URL with `{recipe}` placeholder (replace with e.g. `512x512`) |

RSS feed URL for a show: `https://podcasts.files.bbci.co.uk/{id}.rss`

### Episode items (`playable_search`)

Each item in the Episodes module represents a single broadcast episode:

| Field | Description |
|---|---|
| `id` | Episode PID |
| `titles.primary` | Show name |
| `titles.secondary` | Episode title |
| `container.id` | Parent show PID (use this to get the RSS feed) |
| `duration.value` | Duration in seconds |
| `image_url` | Episode-specific image with `{recipe}` placeholder |

We only use the `container_search` module (shows) for search results, since subscribing to the RSS feed is more useful than linking to individual episodes.

### Alternative: `programmes/search/container`

`GET https://rms.api.bbc.co.uk/v2/programmes/search/container?q={encoded_query}`

A dedicated show-search endpoint that returns items directly (no module wrapper). Returns the same results in the same order as `container_search` above — verified to be identical for all tested queries. It does not filter to podcast-only results either.

### RSS feed availability

The BBC search API returns all radio shows regardless of whether a podcast RSS feed exists. In practice a significant share of results (e.g. 8/10 for the query "Garden") return HTTP 404 from `podcasts.files.bbci.co.uk`. This affects:

- Local / regional radio (BBC Humberside, BBC Devon, BBC Nottingham, …)
- World Service shows not distributed as podcasts
- Archive series (Radio 4 Extra, …)
- School Radio

**There is no field in the search response that indicates podcast availability.** The `download` field on individual episodes (accessible via `GET /v2/programmes/playable?container={pid}`) is `null` for streaming-only shows and `"drm"` for shows with a podcast feed, but fetching this per search result is expensive.

The app therefore HEAD-checks `podcasts.files.bbci.co.uk/{pid}.rss` for each result before including it in the list, and silently drops any that return a non-2xx status.

---

## RSS Feeds

Feed URL pattern: `https://podcasts.files.bbci.co.uk/{pid}.rss`

The feeds are standard RSS/Atom with `<itunes:*>` extensions. Key caveat: **all URLs inside the feed use `http://`**, not `https://`, including:

- `<itunes:image>` — artwork at `http://ichef.bbci.co.uk/images/ic/3000x3000/{img_id}.jpg`
- `<enclosure url="...">` — media files at `http://open.live.bbc.co.uk/mediaselector/...`

These are handled transparently by the app's OkHttp interceptor (see below).

---

## Media playback

Enclosure URLs point to the **BBC Media Selector**, a redirect service:

```
http://open.live.bbc.co.uk/mediaselector/6/redir/version/2.0/mediaset/audio-nondrm-download-rss/proto/http/vpid/{episode_pid}.mp3
```

The `proto/` path segment controls the protocol of the **redirect destination**:

| Path segment | Redirect destination |
|---|---|
| `proto/http` | `http://bbc.pdn.tritondigital.com/...` → blocked on Android 9+ |
| `proto/https` | `https://bbc.pdn.tritondigital.com/...` → works |

The full HTTPS redirect chain with `proto/https`:
1. `https://open.live.bbc.co.uk/mediaselector/...` → 302
2. `https://bbc.pdn.tritondigital.com/ak/...` → 302
3. `https://bbc.pdn.tritondigital.com/v1/download/...` → 302
4. `https://bbc.pdn.tritondigital.com/v1/variant/...` → 200 `audio/mpeg`

### App-side fix

`AntennapodHttpClient` includes an application interceptor that rewrites all `http://` requests to `*.bbc.co.uk` and `*.bbci.co.uk` hosts:

- `http://` → `https://`
- `/proto/http/` → `/proto/https/` in the URL path

This ensures the media selector issues HTTPS redirects, making the entire chain HTTPS-clean without requiring any network security config exemptions.
