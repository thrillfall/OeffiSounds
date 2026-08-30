# :app

The main application module that integrates all features and hosts app-specific UI screens not large enough for their own module.
`PodcastApp` initializes the app; `ClientConfigurator` registers service implementations (download, sync) at startup.

The miniplayer (the collapsed player bar at the bottom of the screen) is implemented in `ExternalPlayerFragment`.
It is hosted in `MainActivity` as a bottom sheet. `MainActivity` controls its visibility via `setPlayerVisible()` based on playback state.

## Wear OS Communication (play flavor only)

`WearListenerService` is a `WearableListenerService` that handles `DataLayer` messages from connected watches.
It responds to watch-initiated requests.
The service is kept alive by the Android framework while at least one watch is connected.

## ARD Sounds home sections

The `Audiothek*Section` classes on the home screen read the ARD playout page
(`https://api.ard.de/playout-api/v1/pages?canonicalWebURL=/`) and pick the widget they need out of `widgets`.
A widget is identified either by its `widgetType` (stable) or by its `title` (editorial, may change).
Each entry in a widget's `teasers` carries an `assetId`: `urn:ard:show:…` for a podcast, `urn:ard:episode:…` for a
single episode.

A subscribable feed URL is `https://api.ardaudiothek.de/programsets/<urn:ard:show:…>`; `:parser:feed` recognises the
JSON it returns. Episode teasers additionally carry `assetDownloadURL` and are opened in the player directly instead.
The older `https://api.ardaudiothek.de/homescreen` endpoint no longer returns recommendations, so do not add callers.
