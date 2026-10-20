# CNTrack — Android

A native Android app for tracking CN rail intermodal containers. It reads your
shipment list from Google Sheets, pulls live tracking from the CN Rail API
through a small Google Apps Script proxy, and keeps a local SQLite cache with a
rail-accurate route map.

Kotlin + Jetpack Compose. No Python, no WebView, no server of your own.

## Features

- **Shipments** — active/closed jobs with port ETA, LFD, and container progress.
- **Containers** — watchlist with status, last event, ETA revisions, customs,
  free day, and delay verdict.
- **Grouping** — group either screen by Shipment, Customer, MBL, or HBL.
- **Detail** — full event history, ETA revision history, customs and storage
  info, and a route map that follows the rail network between stations.
- **Quick Track** — look up any container number without adding a shipment.
- **Alerts** — customs holds, free-day countdowns, and ETA delays.
- **Notifications** — background checks for IN-GATE, RAMPED, DERAMPED and
  OUT-GATE milestones via WorkManager.
- **PDF export** — share a container summary.
- **Configurable endpoints** — set your tracking proxy and sheet sync URLs in
  the app; no rebuild required.
- **Offline cache** — tracking responses are cached for 24 hours; the map's
  station and rail data are bundled.

## Screens

| Screen | Purpose |
| --- | --- |
| Shipments | Jobs with ETA, customer, MBL/HBL, container counts |
| Containers | Flat or grouped container watchlist |
| Quick Track | Ad-hoc container lookup |
| Alerts | Customs / LFD / ETA warnings |
| Detail | Events, ETA revisions, customs, storage, map, PDF |
| Settings | Tracking proxy URL, sheet sync URL |
| About | Version and data sources |

## Repository layout

```
app/                     Android application
  src/main/assets/cntrack/shipments.db      reference DB (schema + station/rail data)
  src/main/assets/cntrack/maps/cache_v1.json  rail graph used by the map
apps-script/
  cn-rail-proxy.gs       CN Rail API proxy (auth, tracking, GPS, history)
  sheet-sync.gs          Google Sheet -> app shipment rows
  README.md              deployment guide
docs/SHEET_FORMAT.md     expected sheet columns and examples
BUILD.md                 build and signing instructions
```

## Quick start

### 1. Requirements

- JDK 17
- Android SDK platform 35 (build-tools 35.0.0)
- Gradle 8.7 (wrapper included — `./gradlew`)
- A Google account for the Apps Script projects
- CN Rail API credentials (`apiKey` and `authKey`)

### 2. Deploy the two Apps Script endpoints

Follow [`apps-script/README.md`](apps-script/README.md):

1. Create `cn-rail-proxy` and set `CN_API_KEY` / `CN_AUTH_KEY` script
   properties. Deploy as a web app (execute as you, access anyone).
2. Create `sheet-sync` bound to your shipment sheet (or set `SHEET_ID`).
   Deploy the same way.
3. Copy both `/exec` URLs.

### 3. Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [`BUILD.md`](BUILD.md) for Android Studio, SDK setup, release signing, and
troubleshooting.

### 4. Configure the app

Open the app → `⋮` → **Settings** → paste the tracking proxy URL and the sheet
sync URL → **Save**. Then `⋮` → **Pull shipments from sheet**.

The shipment sheet format is documented in
[`docs/SHEET_FORMAT.md`](docs/SHEET_FORMAT.md).

## How live data works

```
CN Rail API  <-  cn-rail-proxy.gs  <-  app (OkHttp)
                      |
                Script Properties (event + ETA history)

Google Sheet  <-  sheet-sync.gs  <-  app
```

The CN tracking endpoint returns **one** `Event` per container — the latest
one. To make history useful, `cn-rail-proxy.gs` appends each new event and each
new ETA revision to Script Properties every time the app polls. History
therefore builds up over time:

- Fresh deployment: one event and one ETA row per container.
- After repeated refreshes: a growing event trail and ETA revision list.

History limits are configurable with the `ACTIVITY_EVENTS_MAX` and
`ETA_REVISIONS_MAX` script properties (default 60 each). Script Properties are
capped at 500 KB per project; use `?action=prune&days=90` to drop containers
that have not been polled recently.

Tracking responses are cached locally for 24 hours. Use `Refresh` in the app to
force a live pull. With no tracking URL configured, the app keeps working from
the cache and bundled map data.

## Privacy

- CN credentials are stored only in the Apps Script project properties. They
  are never shipped in the APK or committed to this repository.
- Shipment rows stay in your Google Sheet and in the app's local SQLite
  database.
- The repo's bundled `shipments.db` contains only schema and reference tables
  (station coordinates, rail routes, aliases). It contains no shipments,
  customers, or container history.
- Apps Script web app URLs act as capability links — anyone with a URL can call
  your deployment. Keep them private and redeploy if leaked.

## Limitations

- CN history is accumulated by the proxy; a brand-new deployment has no
  historical events until it has been polled for a while.
- The rail map uses a bundled station/route graph. Container positions between
  stations are interpolated along the rail network, not live GPS.
- The app is portrait-first and targets phones; tablets work but are not tuned.

## License

MIT — see [`LICENSE`](LICENSE).

Developed by Amir Arshad (Avancer IFS).
