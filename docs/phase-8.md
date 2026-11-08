# Phase 8 — food sources, OCR and AI

Implemented a FoodSource interface with Open Food Facts, USDA FoodData Central,
and Swiss API/offline sources. OFF uses an identifying User-Agent and explicit
search/barcode actions. USDA uses an encrypted user key. The Swiss database now
has a free, unrestricted public API: its actual search/detail responses were
checked against the published specification. An attributed offline v7.1 snapshot
of all 1,246 foods is also bundled so lookup remains useful without keys/network.
Unknown values and trace/unparseable spreadsheet values remain unknown. Explicit
unit conversions (including g→mg/µg) are tested; required missing macros stay
blank in confirmation, never silently become saved zeroes.

CameraX captures barcode, nutrition-label and meal photos. Existing images can
also be selected without storage permission. Images are resized/oriented, EXIF
location metadata is stripped, and OCR/barcode work entirely on-device. Label
parsing is conservative; drafts are estimated and editable, with explicit column
and serving-basis review. Parsed/imported food is not logged until both food and
amount are confirmed.

## Necessary plan deviations

Google's own ML Kit disclosure documents diagnostic/usage analytics and the
opt-out request remains open. This conflicts with the plan's no-analytics and
no-background-network rules. Bundled Tesseract4Android + English model and ZXing
are used instead; ML Kit and its transport/telemetry components are not present
in the merged manifest. CameraX remains the camera stack. The local libraries,
model provenance and native dependency licenses are included. JitPack is scoped
only to the Tesseract group; all versions are pinned.

Similarly, the suggested WorkManager network retries conflict with the explicit
no-background-network rule. AI requests queue in Room (schema v6), including an
app-private photo where needed. Retry is explicit in the visible app on Wi-Fi or
while charging. Completed estimates remain reviewable offline; confirmation
atomically saves the food and consumes the queued draft. No cloud sync, automatic
upload, background retry or automatic food logging is implemented.

## Gemini and settings

AiProvider's first implementation uses Gemini generateContent with typed JSON
schema and validation, for text or photos. Default model is the documented
`gemini-flash-lite-latest` alias and the model ID is editable. AI-generated
nutrition is labeled estimated and editable. Free-tier Google data-use notice
appears with provider settings and lookup controls. Keys are stored in the
existing Keystore-encrypted store; no key enters saved UI bundles or exports.
Lookup mode is in DataStore: database first with AI fallback, AI only, database
only. Mode rules are enforced in both UI and controller. Manual entry is always
available with no key or network.

Verification passed: build, lint with 0 errors, 29 JVM tests and 20 Android 35
emulator tests. Coverage includes source parsing/units, lookup policy, persistent
queued requests, explicit confirmation, actual bundled OCR/barcode recognition
on synthetic images, an offline Swiss search→food confirmation→meal flow,
v1→v6 migration and all earlier workflows. Remote Gemini/USDA
account calls need the user's valid keys; no paid request or shared key was used.
Physical camera/label diversity and provider quotas remain manual acceptance.

Sources/provenance:
- Open Food Facts API docs and ODbL / DbCL source attribution.
- https://fdc.nal.usda.gov/api-guide/
- https://www.naehrwertdaten.ch/en/ and the official downloads/API specification.
- https://developers.google.com/ml-kit/android-data-disclosure
- https://github.com/googlesamples/mlkit/issues/593
- https://github.com/adaptech-cz/Tesseract4Android and https://github.com/zxing/zxing
- https://ai.google.dev/gemini-api/docs/models and Gemini generateContent docs.
