# :net:common

This module contains general network related utilities.
Provides the shared OkHttp client, user-agent configuration, and HTTP helpers used by all other net modules.
Cleartext traffic is disabled app-wide, so the shared client rewrites every `http://` request to `https://`; always use it for network access.
