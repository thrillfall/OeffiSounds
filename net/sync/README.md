# :net:sync

This folder contains modules related to external services for synchronization. The module `model` provides the basic interfaces for implementing a synchronization backend. The other modules contains backends for specific synchronization services.

Nextcloud (gpoddersync) authentication is done via the Nextcloud Android app (SSO). When selecting the Nextcloud provider in the app, AntennaPod opens the Nextcloud account chooser and uses the selected account/token for all sync requests.
