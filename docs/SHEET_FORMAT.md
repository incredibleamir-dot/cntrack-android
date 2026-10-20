# Shipment sheet format

The app does not read the spreadsheet directly. `apps-script/sheet-sync.gs`
reads the tab and returns only the rows whose **App Sync** checkbox is `TRUE`.

## Columns

| Col | Header | Required | Notes |
| --- | --- | --- | --- |
| C | `Job#` | yes | Unique job number. Rows without it are skipped. |
| D | `MBL#` | no | Master bill of lading. |
| E | `HBL#` | no | House bill / PO. Multiple values may be separated by `/`, `,`, `;`, or newlines. |
| F | `Consignee` | no | Customer name. Used for the Customer grouping mode and search. |
| G | `Container Number` | no | Containers separated by newlines, commas, or semicolons. Valid format: 4 letters + 7 digits. |
| I | `Port of Discharge` | no | Shown on shipment cards and used for sorting context. |
| J | `ETA Date` | no | Date cell or `yyyy-mm-dd`. Displayed as a friendly date in the app. |
| AA | `App Sync` | yes | Checkbox. Only ticked rows are synced. |

Other columns in the sheet are ignored.

### TBA jobs

When a job number is not issued yet, put the MBL in the **Job#** column and
leave the real job number blank. The app labels such groups by their MBL and
shows a `TBA` pill. Since `Job#` is unique in the app's database, the MBL
value is stored as the job number until it is replaced.

### Container numbers

- Case is ignored; the proxy and app normalize to uppercase.
- A missing ISO check digit is fine — the CN API uses the first 10 characters.
- A container may appear on only one job at a time.

## Example

| C | D | E | F | G | I | J | AA |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `JOB-1001` | `MEDU1234567` | `HBL-77` | `ACME TRADING` | `CNRU1234567`<br>`TGHU7654321` | `MONTREAL` | `2026-10-28` | ✅ |
| `MEDU7654321` (TBA) | `MEDU7654321` | | `NORTHSTAR` | `OOLU1234567` | `VANCOUVER` | `2026-11-04` | ✅ |
| `JOB-1002` | | | `ACME TRADING` | `CMAU9988776` | `TORONTO` | | ☐ |

Row 3 is not ticked, so it is not returned.

## Syncing

- `⋮` → **Pull shipments from sheet** syncs immediately.
- The app also tries once per day on launch if a sheet URL is configured.
- Syncing replaces the local shipment list with the ticked rows; tracking cache
  and archive state for unchanged containers are preserved.
- Removing a tick or a row removes that shipment from the app on the next sync.
