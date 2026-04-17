# Changelog

All notable changes to Öffi Sounds are documented here.

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
