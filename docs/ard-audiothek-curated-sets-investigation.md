# ARD Audiothek Curated Sets Investigation

## Approach

This investigation aimed to identify curated content sets in the ARD Audiothek GraphQL API beyond the "what's new" and "what's popular" sections already implemented in AntennaPod.

### Methodology

1. **Examined existing implementation** - Analyzed current AudiothekSection classes to understand API usage patterns
2. **API endpoint analysis** - Investigated the `/homescreen` GraphQL endpoint to discover available curated sets
3. **Content enumeration** - Queried the API to retrieve current content for each identified curated set
4. **Playout API discovery** - Found a separate REST API serving the correct "Heute wichtig" content
5. **Documentation** - Tracked findings and progress throughout the investigation

## Current Implementation Analysis

AntennaPod currently implements several ARD Audiothek sections:

### Existing Sections
- **AudiothekSection** - Main section with featured and most played content
- **AudiothekLiveSection** - Live content section
- **AudiothekStageSection** - Stage content section
- **AudiothekChartsSection** - Charts/popular content section
- **AudiothekFeaturedSection** - Featured content section (uses legacy REST API)
- **AudiothekHotSection** - Hot/trending content section (uses GraphQL `Stage` type)
- **AudiothekHeuteWichtigSection** - Today's important news episodes (uses ARD Playout API)

### API Usage
Sections use two different APIs:
- Legacy REST API: `https://api.ardaudiothek.de/homescreen` (AudiothekFeaturedSection, AudiothekChartsSection)
- GraphQL API: `https://api.ardaudiothek.de/graphql` (AudiothekHotSection, AudiothekHeuteWichtigSection)
- ARD Playout API: `https://api.ard.de/playout-api/v1/` (AudiothekHeuteWichtigSection — **correct source**)

---

## ARD Audiothek GraphQL API

### Homescreen Sections (GraphQL)

Query: `{ homescreen { sections { __typename id title type ... } } }`

The homescreen returns exactly **8 sections** (the `limit` parameter does not expose additional sections):

| # | Type | Title | Type Value | Notes |
|---|------|-------|------------|-------|
| 0 | `Stage` | — | `STAGE` | Hot section — already implemented |
| 1 | `SophoraWidget` | — | `NAVIGATION` | Editorial category navigation (17 categories) |
| 2 | `SophoraWidget` | "Der Playbutton für deinen Tag" | `banner` | Banner/promo |
| 3 | `RecommendationSection` | "Podcasts-Charts" | `most_played` | Charts — already implemented |
| 4 | `SophoraWidget` | "Immer gut informiert: News-Podcasts in der ARD Audiothek" | `featured_programset` | 12 ProgramSets |
| 5 | `SophoraWidget` | "Exklusiv und vorab in der ARD Audiothek" | `featured_programset` | 11 ProgramSets |
| 6 | `SophoraWidget` | "Unsere Lieblinge: Empfehlungen der Redaktion" | `featured_programset` | 10 ProgramSets |
| 7 | `SophoraWidget` | "Fußball live hören" | `featured_programset` | 5 ProgramSets |

**Important**: Section IDs use the `entdecken-100:` prefix. The "Heute wichtig" widget (`entdecken-108:`) is NOT in the GraphQL homescreen response — it comes from a separate API (see below).

### Editorial Categories (GraphQL)

Navigation category IDs (from NAVIGATION widget):

| Label | editorialCategory ID | Title |
|-------|---------------------|-------|
| Comedy | 42914694 | Comedy & Satire |
| True Crime | 63764892 | True Crime |
| Doku | 42914710 | Doku & Reportage |
| Sportschau | 42914734 | Sportschau |
| Wissen | 42914742 | Wissen |
| **Für Kinder** | **42914714** | **Für Kinder** |
| Hörspiel | 42914712 | Hörspiel |
| Hörbuch | 42914713 | Hörbuch |
| Leben & Liebe | 63927336 | Leben & Liebe |
| **Politik** | **51850530** | **Politik & Hintergrund** |
| Geschichte | 42914743 | Geschichte |
| Gesellschaft | 42914720 | Gesellschaft |
| Religion & Philosophie | 42914732 | Religion & Philosophie |
| Retro | 74928578 | Retro |
| Musik entdecken | 42914724 | Musik entdecken |
| Kultur | 42914736 | Kultur |
| Wirtschaft | 42914740 | Wirtschaft |

### GraphQL Schema Notes

- `homescreen` and `editorialCategory` both return a `Board` type (API was updated)
- Section types: `Stage`, `SophoraWidget`, `RecommendationSection`, `ProgramSetSection`
- `SophoraWidget` sections have both `nodes` (typed) and `teasers` (with `content` Teaser interface) fields
- Feed URL for programsets: `https://api.ardaudiothek.de/programsets/{id}` — accepts both numeric Sophora IDs and URN-style core IDs (e.g. `urn:ard:show:aa82d94affcdfbc0`)

---

## ARD Playout API (Key Discovery)

**Base URL**: `https://api.ard.de/playout-api/v1/`

This is a separate REST API used by the ardsounds.de website frontend. It serves the **complete home page widget structure** including "Heute wichtig", which is NOT available via the GraphQL homescreen API.

### Homepage Widgets

**Endpoint**: `GET https://api.ard.de/playout-api/v1/pages?canonicalWebURL=/`

Returns the full ARD Sounds homepage with **13 widgets**:

| # | Widget ID | Title |
|---|-----------|-------|
| 0 | `urn:ard:playout-widget:4e45725723c910f8` | — (Stage/Hero) |
| 1 | `urn:ard:playout-widget:031c209bf88e1c33` | — |
| 2 | `urn:ard:playout-widget:2d0b38a8d998b683` | — |
| 3 | `urn:ard:playout-widget:c44c8a6245bb1302` | Podcast Charts |
| 4 | `urn:ard:playout-widget:201e53552f402867` | Gefährliche Nähe |
| 5 | `urn:ard:playout-widget:4090209c0e6a0010` | Meine Sender |
| **6** | **`urn:ard:playout-widget:d226f72219e4d437`** | **Heute wichtig** ✅ |
| 7 | `urn:ard:playout-widget:43da15997c0b588c` | Entdecke ARD Sounds |
| 8 | `urn:ard:playout-widget:ef7d85dae11c6150` | Twelve Months of Romance \| Hörbuch |
| 9 | `urn:ard:playout-widget:9cee4f8dfc063b2b` | Neu dabei |
| 10 | `urn:ard:playout-widget:8a6f840cabadd85f` | Hörspaß für Kinder |
| 11 | `urn:ard:playout-widget:ca7415e0a7e32e3a` | Exklusiv und vorab bei uns |
| 12 | `urn:ard:playout-widget:3d6fa68759dc0b00` | Verpasse keine Highlights |

### "Heute wichtig" Widget Structure

```json
{
  "id": "urn:ard:playout-widget:d226f72219e4d437",
  "widgetType": "swiperTeaserWidget",
  "title": "Heute wichtig",
  "teasers": [
    {
      "title": "Baustart für Pipeline: Künstlicher See statt Tagebau",
      "assetId": "urn:ard:episode:37b35560a5775104",
      "showId": "urn:ard:show:aa82d94affcdfbc0",
      "showTitle": "WDR 2 Das Thema",
      "image": {
        "templateURL": "https://api.ardmediathek.de/image-service/images/...?w={width}",
        "aspectRatio": "ar1X1"
      },
      "duration": 154,
      "firstPublicationDate": "2026-03-17T15:40:41Z",
      "assetDownloadURL": "https://wdrmedien-a.akamaihd.net/..."
    },
    ...
  ]
}
```

### Implementation in AudiothekHeuteWichtigSection

1. Fetch `https://api.ard.de/playout-api/v1/pages?canonicalWebURL=/`
2. Find widget with `title == "Heute wichtig"` in the `widgets` array
3. For each teaser:
   - `title` = teaser.title
   - `imageUrl` = teaser.image.templateURL (replace `{width}` with `400`)
   - `feedUrl` = `https://api.ardaudiothek.de/programsets/` + teaser.showId
4. The `/programsets/` REST endpoint accepts URN-style `showId` directly (no ID lookup needed)

### Other Available Pages

| Canonical URL | Title | Notes |
|---------------|-------|-------|
| `/` | ARD Sounds Startseite | Main homepage — 13 widgets |
| `/podcasts/` | Podcasts | Used in web app navigation |

---

## Previously Investigated (Legacy REST API)

The old `https://api.ardaudiothek.de/homescreen` REST API returned:

| Set Name | API Key | Title | Status |
|----------|---------|-------|--------|
| Featured Programs | `mt:featuredProgramSets` | "Einfach mehr wissen" | AudiothekFeaturedSection (legacy) |
| Popular Charts | `mt:mostPlayed` | "Podcasts-Charts" | AudiothekChartsSection (legacy) |
| Stage Content | `mt:stageItems` | "Stage" | Often empty |
| Live Events | `mt:items` | Live events | Often empty |

This API is still used by `AudiothekFeaturedSection` and `AudiothekChartsSection` but is considered legacy. The GraphQL and Playout APIs are preferred.

---

*Investigation updated March 17, 2026*
*APIs: https://api.ardaudiothek.de/graphql | https://api.ard.de/playout-api/v1/*
