# Changelog

All notable changes to Öffi Sounds are documented here.

## [2.10.2] - 2026-08-20

### Fixed
- The app no longer crashes on startup right after installing or updating. The release build's code shrinker stripped a constructor that WorkManager creates by reflection, and miscompiled the preferences class, so the app died before the first screen appeared

## [2.10.1] - 2026-08-20

### Fixed
- ARD Sounds and SRF Play: entries without any audio are no longer imported. They used to sit in the episode list as items that could only be marked as played, never played

## [2.10.0] - 2026-08-20

### Changed
- Rebased on AntennaPod 3.12.0 (previously 3.11.0), bringing upstream's playback service rewrite, home screen and database improvements. The app now requires Android 6 (was Android 5)

### Fixed
- ARD Sounds and SRF Play feeds could crash on Android 6 and 7 while parsing (used an API that only exists since Android 8)

## [2.9.0] - 2026-08-19

### Added
- Player: optional volume slider that can lower the playback volume below the smallest step Android offers (down to -30 dB in 2 dB steps). Enable it via Settings → Playback → "Volume slider in player"

## [2.8.4] - 2026-07-09

### Fixed
- ARD Sounds: Podcast-Charts on the home screen showed only a single tile with the rest black/flickering after ARD's legacy homescreen feed collapsed to one item. Fetch the charts from the ARD Sounds home board via GraphQL instead, and stop reloading (and re-flashing placeholders) on every feed update

## [2.8.3] - 2026-07-05

### Fixed
- Android Auto: restore the `com.google.android.gms.car.application` meta-data that was dropped during the rebrand, so the app is discovered and shown in the car again

## [2.8.2] - 2026-04-23

### Changed
- F-Droid listing: drop fork reference from summary tagline so the listing leads with the app's actual features

## [2.8.1] - 2026-04-20

### Fixed
- Fix infinite sync loop: record `lastRefreshAttempt` on every refresh attempt (not just on success), so feeds whose refresh path never wrote the timestamp (e.g. local feeds, cancelled downloads) no longer trigger a perpetual SyncService ↔ FeedUpdate loop

## [2.8.0] - 2026-04-20

### Improved
- ARD Sounds: full episode shownotes via GraphQL API (previously truncated to ~180 chars)

### Fixed
- ARD Sounds: episodes with null downloadUrl no longer show "Medienadresse: null"
- ARD Sounds: clicking show teasers on home screen no longer crashes
- Fastlane short descriptions shortened to ≤80 chars

## [2.7.5] - 2026-04-17

### Fixed
- Fix infinite sync loop: subscription sync no longer loops endlessly when non-subscribed feeds with keepUpdated exist in the database

## [2.7.1] - 2026-04-14

### Fixed
- Nextcloud sync: fix subscription upload failing with "No value for update_urls" (regression from SSO rewrite)

## [2.7.0] - 2026-04-15

### Added
- RTVE integration: search and discover podcasts from Spain's public broadcaster (opt-in via Settings → Search)
- Home screen: new "RTVE: Podcasts" section showing RTVE original podcasts

## [2.6.0] - 2026-04-14

### Added
- Deutschlandfunk integration: search and discover podcasts from Deutschlandfunk and Deutschlandfunk Kultur (opt-in via Settings → Search)
- Home screen: new "Deutschlandfunk: Beliebte Podcasts" section showing curated DLF podcasts

## [2.5.0] - 2026-04-14

### Added
- ORF Sound integration: search and discover Austrian public radio podcasts (opt-in via Settings → Search)
- Home screen: new "ORF Sound: Podcasts" section showing ORF podcasts

### Changed
- Search provider settings: individual toggles instead of multi-select dialog
- Search provider settings renamed to "Active search providers"
- Apple (iTunes) and Podcast Index moved down in provider list and disabled by default

## [2.4.1] - 2026-04-14

### Added
- "What's New" popup shown once after updating, highlighting new provider integrations

## [2.4.0] - 2026-04-14

### Added
- SRF Play integration: search and discover Swiss public radio podcasts (opt-in via Settings → Search)
- Home screen: new "SRF Play: Beliebte Podcasts" section showing popular SRF podcasts

## [2.3.1] - 2026-04-14

### Fixed
- BBC World Service Recommended Today: fix null podcast ID causing 404 when tapping certain tiles

## [2.3.0] - 2026-03-30

### Added
- Home screen: new "BBC Audio Recommended Today" section showing today's recommended episodes

## [2.2.2] - 2026-03-24

### Fixed
- Fix overflow due to wrong return type

## [2.2.1] - 2026-03-24

### Fixed
- App crash on startup when update interval was set to a large value (>24 days)

## [2.1.0] - 2026-03-23

### Changed
- "Add Podcast" screen now shows ARD Sounds popular podcasts instead of Apple Podcasts suggestions

## [2.0.2] - 2026-03-23

### Fixed
- BBC Sounds: filter out search results that have no valid podcast RSS feed, preventing dead subscription links

## [2.0.1] - 2026-03-?

### Changed
- Renamed ARD Audiothek integration to **ARD Sounds**

## [2.0.0]

### Added
- **BBC Sounds**: search and subscribe to BBC podcasts (opt-in via Settings → Search)
- **Search provider selection**: choose which podcast services to query in settings

### Changed
- ARD Audiothek: updated to new playout API for the "Was ist heute wichtig" section
