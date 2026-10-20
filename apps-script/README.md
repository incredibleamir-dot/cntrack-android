# Apps Script setup

Two independent web apps:

| Script | Purpose | App setting |
| --- | --- | --- |
| `cn-rail-proxy.gs` | CN Rail auth + tracking (+ optional GPS), with event/ETA history | Tracking proxy URL |
| `sheet-sync.gs` | Returns checked rows from your shipment sheet | Sheet sync URL |

Both are deployed as **Web app**, **Execute as: Me**, **Who has access: Anyone**.
The resulting URL ends in `/exec`.

> Treat each `/exec` URL like a password. Anyone who has it can call your
> deployment and consume your CN API quota.

---

## 1. CN Rail proxy

1. Go to <https://script.google.com> → **New project** → rename it
   `cn-rail-proxy`.
2. Replace `Code.gs` with the contents of [`cn-rail-proxy.gs`](cn-rail-proxy.gs).
3. **Project Settings** (gear icon) → **Script properties** → add:

   | Property | Required | Value |
   | --- | --- | --- |
   | `CN_API_KEY` | yes | CN Rail API key |
   | `CN_AUTH_KEY` | yes | CN Rail auth key |
   | `CN_AUTH_URL` | no | Override the token URL |
   | `CN_TRACKING_URL` | no | Override the tracking URL |
   | `CN_GPS_URL` | no | Override the GPS URL |
   | `ACTIVITY_EVENTS_MAX` | no | Events kept per container (default `60`) |
   | `ETA_REVISIONS_MAX` | no | ETA revisions kept per container (default `60`) |

4. **Deploy** → **New deployment** → type **Web app**:
   - Description: `CNTrack tracking v1`
   - Execute as: **Me**
   - Who has access: **Anyone**
5. Authorize when prompted, then copy the **Web app URL**.

### Verify

Open in a browser:

```
<WEB_APP_URL>?action=ping
```

Expected:

```json
{"success":true,"service":"cn-rail-proxy","version":"1.0.0","time":"..."}
```

Then try a container:

```
<WEB_APP_URL>?containers=CNRU1234567&activityLimit=100
```

Expected shape:

```json
{
  "success": true,
  "count": 1,
  "results": {
    "CNRU1234567": {
      "equipmentId": "CNRU1234567",
      "summary": {
        "equipmentId": "CNRU1234567",
        "lastEvent": "RAMPED",
        "lastEventTime": "2026-09-28 11:07 ET",
        "lastEventLocation": "BRAMPTON INTER TERM",
        "customsDescription": "Remanifest to Freight Forwarder",
        "customsTimestamp": "2026-09-23T04:32:00.000Z",
        "lastFreeDay": "",
        "waybillStatus": "Executing",
        "destination": "MONTREAL TERMONT",
        "etaTime": "2026-09-29 03:30 ET",
        "etaDescription": "ARRIVED",
        "etaStation": "MONTREAL WHARF"
      },
      "activity": [
        {
          "EquipmentId": "CNRU1234567",
          "Event_Time": "2026-09-28 11:07 ET",
          "Event_Description": "RAMPED",
          "Event_Location_Station": "BRAMPTON INTER TERM"
        }
      ],
      "eta": [],
      "customStatus": [],
      "storage": [],
      "inMaster": false
    }
  }
}
```

### Parameters

| Parameter | Meaning |
| --- | --- |
| `containers` | Comma-separated container numbers (10 or 11 characters) |
| `activityLimit` | Return only the newest N events (`0`/omitted = all stored) |
| `action=gps` | Raw CN GPS locations for the same `containers` |
| `action=ping` | Health check |
| `action=prune&days=90` | Delete history for containers not polled in N days |

### History behaviour

CN returns only the latest `Event` per container, so the proxy stores events and
ETA revisions in Script Properties as it is polled:

- `he_<container>` — `[[time, description, station], ...]`
- `ht_<container>` — `[[status, destination, time, description, station], ...]`
- `hs_<container>` — last seen date (used by prune)

Duplicates are skipped. Old entries are trimmed to keep each property well under
the 9 KB per-value limit. Script Properties have a 500 KB total quota per
project, which is enough for a small fleet; prune stale containers if you track
hundreds over months.

No history exists until the proxy has been called more than once, so the first
app refresh shows just the current event and ETA.

---

## 2. Sheet sync

The simplest setup is a script bound to the spreadsheet.

1. Open the shipment Google Sheet → **Extensions** → **Apps Script**.
2. Replace `Code.gs` with [`sheet-sync.gs`](sheet-sync.gs).
3. (Optional) **Project Settings** → **Script properties**:
   - `SHEET_NAME` — tab name, default `Main Sheet`
   - `SHEET_ID` — spreadsheet id, only needed for a standalone project
4. **Deploy** → **New deployment** → **Web app** (execute as you, access anyone).
5. Copy the `/exec` URL.

### Sheet format

Header names are matched on row 1. Column order does not matter.

| Column | Header | Used for |
| --- | --- | --- |
| C | `Job#` | Job number (required) |
| D | `MBL#` | Master bill |
| E | `HBL#` | House bill / PO |
| F | `Consignee` | Customer (used for grouping and search) |
| G | `Container Number` | One or more containers, separated by newlines or commas |
| I | `Port of Discharge` | Port code shown on the shipment card |
| J | `ETA Date` | Port ETA date (`yyyy-mm-dd` or a date cell) |
| AA | `App Sync` | Checkbox; only `TRUE` rows are returned |

If a header is missing, the script falls back to the column positions above.

See [`../docs/SHEET_FORMAT.md`](../docs/SHEET_FORMAT.md) for an example.

### Verify

Open `<SHEET_SYNC_URL>` in a browser:

```json
{
  "success": true,
  "count": 2,
  "shipments": [
    {
      "job": "JOB-1001",
      "mbl": "MEDU1234567",
      "hbl": "HBL-77",
      "po": "HBL-77",
      "customer": "ACME",
      "portOfDischarge": "MONTREAL",
      "etaDate": "2026-10-28",
      "containers": ["CNRU1234567"]
    }
  ]
}
```

`?action=ping` returns a health check.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Missing script property: CN_API_KEY` | Add both CN properties, then redeploy a new version |
| `CN auth failed (HTTP 401/403)` | Check the API key/auth key, and that the CN account is active |
| `success: false` with `HTTP 404` | Redeploy as a Web app and use the `/exec` URL, not `/dev` |
| App says "Tracking URL is not set" | Paste the URL in `⋮` → Settings |
| Empty `activity` | Normal on a fresh deployment; it fills as the app polls |
| Sheet sync returns `count: 0` | Check the `App Sync` checkbox is ticked and the tab name matches |
| `No active spreadsheet` | The script is standalone; set the `SHEET_ID` property |
| Quota errors from Google | Script Properties / UrlFetch daily quotas; prune history and poll less often |
