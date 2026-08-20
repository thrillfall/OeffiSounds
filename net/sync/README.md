# :net:sync

This folder contains modules related to external services for synchronization. The module `model` provides the basic interfaces for implementing a synchronization backend. The other modules contains backends for specific synchronization services.
Local changes are queued in `SynchronizationQueue` and flushed to the active backend by `SyncService`.

Nextcloud (gpoddersync) authentication is done via the Nextcloud Android app (SSO). When selecting the Nextcloud provider in the app, Öffi Sounds opens the Nextcloud account chooser and uses the selected account/token for all sync requests.
