/**
 * CNTrack — shipment sheet sync endpoint.
 *
 * Reads the "Main Sheet" tab and returns rows whose "App Sync" checkbox is
 * checked, as JSON for the Android app.
 *
 * Deploy:
 *   1. Open the Google Sheet > Extensions > Apps Script (or paste into a
 *      standalone project and set SHEET_ID below).
 *   2. Save, then Deploy > New deployment > Web app.
 *        Execute as: Me
 *        Who has access: Anyone
 *   3. Paste the /exec URL into the app: ⋮ → Settings → Sheet sync URL.
 *
 * Optional script properties:
 *   SHEET_ID    spreadsheet id (standalone projects only)
 *   SHEET_NAME  tab name (default "Main Sheet")
 *
 * Expected headers on row 1 (matched by name, order does not matter):
 *   Job# | MBL# | HBL# | Consignee | Container Number | Port of Discharge |
 *   ETA Date | App Sync
 */

var DEFAULT_SHEET_NAME = 'Main Sheet';

function doGet(e) {
  var p = (e && e.parameter) || {};
  try {
    if (String(p.action || '').toLowerCase() === 'ping') {
      return json_({ success: true, service: 'cntrack-sheet-sync', time: new Date().toISOString() });
    }
    return json_(buildShipments_());
  } catch (err) {
    return json_({ success: false, error: String((err && err.message) || err) });
  }
}

function doPost(e) {
  return doGet(e);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function prop_(name) {
  var v = PropertiesService.getScriptProperties().getProperty(name);
  return v === null ? '' : String(v).trim();
}

function buildShipments_() {
  var ss = openSheet_();
  var name = prop_('SHEET_NAME') || DEFAULT_SHEET_NAME;
  var sh = ss.getSheetByName(name);
  if (!sh) return { success: false, error: 'Sheet not found: ' + name };

  var values = sh.getDataRange().getValues();
  if (!values || values.length < 2) return { success: true, shipments: [], count: 0 };

  var header = values[0].map(function (h) { return String(h).trim(); });
  function col(headerName, fallbackIndex) {
    var idx = header.indexOf(headerName);
    return idx >= 0 ? idx : fallbackIndex;
  }

  var cJob = col('Job#', 2);
  var cMbl = col('MBL#', 3);
  var cHbl = col('HBL#', 4);
  var cCons = col('Consignee', 5);
  var cCont = col('Container Number', 6);
  var cPod = col('Port of Discharge', 8);
  var cEta = col('ETA Date', 9);
  var cSync = col('App Sync', 26);

  var shipments = [];
  for (var r = 1; r < values.length; r++) {
    var row = values[r];
    var syncVal = cSync >= 0 ? row[cSync] : false;
    var isSync = syncVal === true || String(syncVal).trim().toUpperCase() === 'TRUE';
    if (!isSync) continue;

    var job = str_(cJob, row);
    if (!job) continue;

    var hbl = normHbl_(str_(cHbl, row));
    var containers = str_(cCont, row)
      .split(/[\r\n,;]+/)
      .map(function (s) { return s.trim().toUpperCase(); })
      .filter(function (s) { return s.length > 0; });

    shipments.push({
      job: job,
      mbl: str_(cMbl, row),
      hbl: hbl,
      po: hbl,
      customer: str_(cCons, row),
      portOfDischarge: str_(cPod, row).toUpperCase(),
      etaDate: fmtDate_(cEta, row),
      containers: containers
    });
  }

  return { success: true, count: shipments.length, shipments: shipments };
}

function openSheet_() {
  var id = prop_('SHEET_ID');
  if (id) return SpreadsheetApp.openById(id);
  var active = SpreadsheetApp.getActiveSpreadsheet();
  if (!active) throw new Error('No active spreadsheet. Set the SHEET_ID script property.');
  return active;
}

function str_(idx, row) {
  if (idx < 0 || row[idx] === null || row[idx] === undefined) return '';
  return String(row[idx]).trim();
}

function fmtDate_(idx, row) {
  if (idx < 0) return '';
  var v = row[idx];
  if (v === null || v === undefined || v === '') return '';
  if (v instanceof Date) {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }
  var s = String(v).trim();
  var m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (m) {
    var mo = ('0' + m[2]).slice(-2);
    var da = ('0' + m[3]).slice(-2);
    return m[1] + '-' + mo + '-' + da;
  }
  var d = new Date(s);
  if (!isNaN(d.getTime())) {
    return Utilities.formatDate(d, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }
  return s;
}

function normHbl_(hbl) {
  var parts = hbl.split(/[\r\n\/,;]+/)
    .map(function (s) { return s.trim(); })
    .filter(function (s) { return s.length > 0; })
    .filter(function (s, i, a) { return a.indexOf(s) === i; });
  var v = parts.join(' / ');
  var up = v.toUpperCase();
  if (up === 'NA' || up === 'N/A' || up === '-' || up === 'NIL' || up === 'NULL') return '';
  return v;
}
