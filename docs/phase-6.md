# Phase 6 — maps and satellite

Native MapLibre 12.0.1 renders keyless OpenStreetMap streets, colored clipped
route lines and start/end markers. Tap the preview for full-screen street /
satellite, time/speed scrubber, pitch and flyover. Interpolation does not cross
unrecorded gaps; GeoJSON lines split at the antimeridian. No bulk/offline-region
API is exposed and prefetch is disabled. Only viewed street tiles are cached,
using server cache headers. OSM attribution and an identifying User-Agent are set.

Satellite uses the user's MapTiler key and TileJSON provider attribution. Keys
are encrypted with Android Keystore AES-GCM, bound to their setting names, never
stored in saved UI bundles, and excluded from HTTP/native disk caches and logs.
The shared HTTP client rejects new background requests and cancels active calls
when the app backgrounds. Map views stop with their lifecycle; the terrain page
is unloaded in the background. Map tile requests necessarily reveal viewed tile
coordinates to the selected provider, not the stored route or fitness database.

The official MapLibre terrain support table explicitly marks Android Native
unsupported. A bundled MapLibre GL JS 4.7.1 renderer in a restricted platform
WebView supplies optional true 3D elevation terrain and camera flyover instead.
It requests MapTiler elevation tiles (terrain-rgb-v2) and never GPS altitude. It
requires a user key and a WebGL-capable WebView. No remote scripts, JS bridge,
external file/content access, arbitrary navigation or third-party tile hosts
are allowed. Native maps remain available if 3D is unsupported.

MapTiler's terms allow a temporary personal cache for a single end user; this
implementation uses direct requests and no bulk download/proxy service. The
user's provider account must authorize satellite/terrain usage. No account key
was supplied, so paid/provider-authenticated imagery cannot be end-to-end verified
here. Bad keys/network failures remain recoverable without breaking local data.

New dependencies: MapLibre Native for the required native map; explicit OkHttp
for identifying headers, cache control, key-safe requests and the foreground HTTP
gate. The bundled JS/CSS (about 850 KB) is necessary only because native terrain
is unsupported; its license is in licenses/maplibre-gl-js-LICENSE.txt. No schema
change.

Verification: 24 JVM tests and 14 Android 35 tests pass, including real native
map/style/route layer creation without external basemap requests, antimeridian
and clipped geometry, Keystore encryption/AAD binding, and HTTP rejection before
a background connection. Debug build and lint pass with zero errors. Final
small terrain-init/attribution adjustments are build-checked separately.
Physical-device rendering, provider-key imagery, and WebGL terrain with real
elevation/account access remain explicit manual acceptance checks.

Sources:
- https://maplibre.org/maplibre-style-spec/terrain/
- https://maplibre.org/maplibre-native/android/examples/
- https://maplibre.org/maplibre-gl-js/docs/examples/3d-terrain/
- https://operations.osmfoundation.org/policies/tiles/
- https://docs.maptiler.com/cloud/api/tiles/
- https://www.maptiler.com/terms/cloud/
