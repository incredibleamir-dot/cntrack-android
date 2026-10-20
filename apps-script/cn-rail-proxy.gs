/**
 * CNTrack — CN Rail tracking proxy for the Android app.
 *
 * Deploy: Extensions > Apps Script (or script.google.com) > Deploy > New deployment
 *   Type: Web app · Execute as: Me · Who has access: Anyone
 *
 * Required script properties (Project Settings > Script properties):
 *   CN_API_KEY   CN Rail API key
 *   CN_AUTH_KEY  CN Rail auth key
 *
 * Optional properties:
 *   CN_AUTH_URL, CN_TRACKING_URL, CN_GPS_URL
 *   ACTIVITY_EVENTS_MAX (default 60)  ETA_REVISIONS_MAX (default 60)
 *
 * Endpoints:
 *   GET  ?containers=AAAU1234567,BBB...&activityLimit=100   tracking (app format)
 *   GET  ?action=gps&containers=...                          raw CN GPS locations
 *   GET  ?action=ping                                        health check
 *   GET  ?action=prune&days=90                               drop stale history
 *
 * CN returns only the latest Event per container. This proxy accumulates events
 * and ETA revisions in Script Properties each time it is polled, keyed by
 * container number. History therefore grows over time after deployment.
 */

var AUTH_URL = 'https://api.cn.ca/v1/oauth/jwt-token/accesstokenJWT?grant_type=client_credentials';
var TRACKING_URL = 'https://api.cn.ca/customers/v1/shipments/tracking';
var GPS_URL = 'https://api.cn.ca/customers/v1/gpslocation';
var TRACKING_BATCH = 20;
var GPS_BATCH = 100;
var VERSION = '1.0.0';

function doGet(e) {
  return handle_(e);
}

function doPost(e) {
  return handle_(e);
}

function handle_(e) {
  var p = (e && e.parameter) || {};
  try {
    var action = String(p.action || 'tracking').toLowerCase();
    if (action === 'ping') {
      return json_({
        success: true,
        service: 'cn-rail-proxy',
        version: VERSION,
        time: new Date().toISOString()
      });
    }
    if (action === 'gps') {
      return json_(fetchGps_(p));
    }
    if (action === 'prune') {
      return json_(prune_(p));
    }
    return json_(fetchTracking_(p));
  } catch (err) {
    return json_({ success: false, error: String((err && err.message) || err) });
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function prop_(name, fallback) {
  var v = PropertiesService.getScriptProperties().getProperty(name);
  return v === null || v === '' ? fallback : v;
}

function required_(name) {
  var v = prop_(name, '');
  if (!v) throw new Error('Missing script property: ' + name);
  return v;
}

function intProp_(name, fallback) {
  var n = parseInt(prop_(name, ''), 10);
  return isNaN(n) || n <= 0 ? fallback : n;
}

function token_() {
  var cache = CacheService.getScriptCache();
  var cached = cache.get('cn_token');
  if (cached) return cached;

  var lock = LockService.getScriptLock();
  var locked = false;
  try {
    locked = lock.waitLock(15000);
  } catch (ignored) {}

  try {
    cached = cache.get('cn_token');
    if (cached) return cached;

    var apiKey = required_('CN_API_KEY');
    var authKey = required_('CN_AUTH_KEY');
    var url = prop_('CN_AUTH_URL', AUTH_URL);
    var res = UrlFetchApp.fetch(url, {
      method: 'post',
      headers: {
        'x-apikey': apiKey,
        'Authorization': 'Basic ' + Utilities.base64Encode(apiKey + ':' + authKey)
      },
      muteHttpExceptions: true
    });
    var code = res.getResponseCode();
    var body = res.getContentText();
    if (code < 200 || code >= 300) {
      throw new Error('CN auth failed (HTTP ' + code + '): ' + truncate_(body, 200));
    }
    var data = JSON.parse(body);
    if (!data.access_token) throw new Error('CN auth returned no access_token');
    cache.put('cn_token', data.access_token, 3000);
    return data.access_token;
  } finally {
    if (locked) lock.releaseLock();
  }
}

function fetchTracking_(p) {
  var ids = parseIds_(p.containers);
  if (!ids.length) throw new Error('Missing containers parameter');

  var requested = parseInt(p.activityLimit, 10);
  var eventLimit = isNaN(requested) || requested <= 0 ? 0 : Math.min(requested, 200);

  var byApi = {};
  for (var i = 0; i < ids.length; i++) {
    var apiId = normalizeForApi_(ids[i]);
    if (!byApi[apiId]) byApi[apiId] = [];
    byApi[apiId].push(ids[i]);
  }
  var apiIds = Object.keys(byApi);

  var token = token_();
  var equipment = {};
  var warnings = [];
  for (var start = 0; start < apiIds.length; start += TRACKING_BATCH) {
    var chunk = apiIds.slice(start, start + TRACKING_BATCH);
    var url = prop_('CN_TRACKING_URL', TRACKING_URL) + '?equipmentIds=' + chunk.join(',');
    var out = cnGet_(url, token);
    token = out.token;
    if (out.code !== 200) {
      warnings.push('tracking batch HTTP ' + out.code);
      continue;
    }
    var list = equipmentList_(out.data);
    for (var j = 0; j < list.length; j++) {
      var item = list[j];
      if (!item) continue;
      var key = normalizeForApi_(item.EquipmentId ||
        (item.InquiryValue && item.InquiryValue[0]) || '');
      if (key) equipment[key] = item;
    }
    if (start + TRACKING_BATCH < apiIds.length) Utilities.sleep(150);
  }
  if (!Object.keys(equipment).length && warnings.length) {
    throw new Error(warnings.join('; '));
  }

  var store = PropertiesService.getScriptProperties();
  var lock = LockService.getScriptLock();
  var locked = false;
  try {
    locked = lock.waitLock(20000);
  } catch (ignored) {}

  var results = {};
  var writes = {};
  try {
    var all = store.getProperties();
    var today = Utilities.formatDate(new Date(), 'UTC', 'yyyy-MM-dd');
    var eventCap = intProp_('ACTIVITY_EVENTS_MAX', 60);
    var etaCap = intProp_('ETA_REVISIONS_MAX', 60);

    for (var a = 0; a < apiIds.length; a++) {
      var api = apiIds[a];
      var eq = equipment[api];
      if (!eq) continue;
      var originals = byApi[api];
      for (var b = 0; b < originals.length; b++) {
        var cid = originals[b];
        var built = build_(cid, eq, all['he_' + cid], all['ht_' + cid], eventCap, etaCap, eventLimit);
        results[cid] = built.record;
        if (built.changed) {
          writes['he_' + cid] = built.he;
          writes['ht_' + cid] = built.ht;
        }
        if (all['hs_' + cid] !== today) writes['hs_' + cid] = today;
      }
    }

    if (locked && Object.keys(writes).length) {
      store.setProperties(writes, false);
    }
  } finally {
    if (locked) lock.releaseLock();
  }

  return {
    success: true,
    count: Object.keys(results).length,
    fetchedAt: new Date().toISOString(),
    warnings: warnings,
    results: results
  };
}

function build_(cid, eq, heRaw, htRaw, eventCap, etaCap, eventLimit) {
  var he = parseList_(heRaw);
  var ht = parseList_(htRaw);
  var changed = false;

  var event = eq.Event || {};
  var evTime = str_(event.Time);
  var evDesc = str_(event.Description);
  var evStation = station_(event.Location);
  var evKey = [evTime, evDesc, evStation].join('|');
  if (evDesc && !hasKey_(he, evKey)) {
    he.push([evTime, evDesc, evStation]);
    if (he.length > eventCap) he = he.slice(-eventCap);
    changed = true;
  }

  var eta = eq.ETA || {};
  var etaTime = str_(eta.Time);
  var etaDesc = str_(eta.Description);
  var etaStation = station_(eta.Location);
  var status = str_(eq.WaybillStatus);
  var dest = station_(eq.Destination);
  var etaKey = [status, dest, etaTime, etaDesc, etaStation].join('|');
  if (etaTime && !hasKey_(ht, etaKey)) {
    ht.push([status, dest, etaTime, etaDesc, etaStation]);
    if (ht.length > etaCap) ht = ht.slice(-etaCap);
    changed = true;
  }

  var customs = latestCustoms_(eq);
  var lfd = str_((eq.StorageCharge || {}).LastFreeDay);

  var summary = {
    equipmentId: cid,
    lastEvent: evDesc,
    lastEventTime: evTime,
    lastEventLocation: evStation,
    customsDescription: customs.description,
    customsTimestamp: customs.timestamp,
    lastFreeDay: lfd,
    waybillStatus: status,
    destination: dest,
    etaTime: etaTime,
    etaDescription: etaDesc,
    etaStation: etaStation
  };

  var visible = eventLimit > 0 ? he.slice(-eventLimit) : he;
  var activity = visible.map(function (row) {
    return {
      EquipmentId: cid,
      Event_Time: str_(row[0]),
      Event_Description: str_(row[1]),
      Event_Location_Station: str_(row[2])
    };
  });

  var etaRows = ht.map(function (row) {
    return {
      EquipmentId: cid,
      Way_Bill_Status: str_(row[0]),
      Destination_Station: str_(row[1]) || null,
      ETA_Time: str_(row[2]),
      ETA_Description: str_(row[3]),
      ETA_Location_Station: str_(row[4])
    };
  });

  var customStatus = [];
  if (customs.description || customs.timestamp) {
    customStatus.push({
      EquipmentId: cid,
      CustomsHold_Description: customs.description,
      CustomsHold_Timestamp: customs.timestamp
    });
  }

  var storage = [];
  if (lfd) storage.push({ EquipmentId: cid, LastFreeDay: lfd });

  var record = {
    equipmentId: cid,
    summary: summary,
    activity: activity,
    eta: etaRows,
    customStatus: customStatus,
    storage: storage,
    inMaster: false
  };

  return {
    record: record,
    he: JSON.stringify(he),
    ht: JSON.stringify(ht),
    changed: changed
  };
}

function latestCustoms_(eq) {
  var holds = eq.CustomsHolds;
  if (holds && holds.length) {
    var pick = null;
    for (var i = 0; i < holds.length; i++) {
      if (holds[i] && holds[i].Timestamp) { pick = holds[i]; break; }
    }
    if (!pick) pick = holds[0];
    if (pick) {
      return { description: str_(pick.Description), timestamp: str_(pick.Timestamp) };
    }
  }
  var one = eq.CustomsHold || {};
  return { description: str_(one.Description), timestamp: str_(one.Timestamp) };
}

function fetchGps_(p) {
  var ids = parseIds_(p.containers);
  if (!ids.length) throw new Error('Missing containers parameter');

  var token = token_();
  var results = {};
  var warnings = [];
  for (var start = 0; start < ids.length; start += GPS_BATCH) {
    var chunk = ids.slice(start, start + GPS_BATCH);
    var res = UrlFetchApp.fetch(prop_('CN_GPS_URL', GPS_URL), {
      method: 'post',
      contentType: 'application/json',
      headers: {
        'Authorization': 'Bearer ' + token,
        'X-Limit': String(GPS_BATCH)
      },
      payload: JSON.stringify({ Id: chunk }),
      muteHttpExceptions: true
    });
    if (res.getResponseCode() !== 200) {
      warnings.push('gps batch HTTP ' + res.getResponseCode());
      continue;
    }
    var data = JSON.parse(res.getContentText());
    var list = equipmentList_((data.GpsLocation || {}));
    for (var i = 0; i < list.length; i++) {
      var item = list[i];
      if (item && item.EquipmentId) results[item.EquipmentId] = item;
    }
    if (start + GPS_BATCH < ids.length) Utilities.sleep(150);
  }

  return {
    success: Object.keys(results).length > 0,
    count: Object.keys(results).length,
    warnings: warnings,
    results: results
  };
}

function prune_(p) {
  var days = parseInt(p.days, 10);
  if (isNaN(days) || days < 1) days = 90;
  var cutoff = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);
  var store = PropertiesService.getScriptProperties();
  var all = store.getProperties();
  var removed = 0;
  for (var key in all) {
    if (key.indexOf('hs_') !== 0) continue;
    if (all[key] >= cutoff) continue;
    var cid = key.slice(3);
    store.deleteProperty('hs_' + cid);
    store.deleteProperty('he_' + cid);
    store.deleteProperty('ht_' + cid);
    removed++;
  }
  return { success: true, prunedContainers: removed, olderThanDays: days };
}

function cnGet_(url, token) {
  var res = UrlFetchApp.fetch(url, {
    headers: { 'Authorization': 'Bearer ' + token, 'Accept': 'application/json' },
    muteHttpExceptions: true
  });
  if (res.getResponseCode() === 401) {
    CacheService.getScriptCache().remove('cn_token');
    token = token_();
    res = UrlFetchApp.fetch(url, {
      headers: { 'Authorization': 'Bearer ' + token, 'Accept': 'application/json' },
      muteHttpExceptions: true
    });
  }
  var code = res.getResponseCode();
  var data = null;
  if (code === 200) {
    try { data = JSON.parse(res.getContentText()); } catch (ignored) {}
  }
  return { code: code, data: data, token: token };
}

function equipmentList_(wrapper) {
  var eq = wrapper && wrapper.Equipment;
  if (!eq) return [];
  return Array.isArray(eq) ? eq : [eq];
}

function parseIds_(raw) {
  var parts = String(raw || '').split(',');
  var out = [];
  var seen = {};
  for (var i = 0; i < parts.length; i++) {
    var id = normalizeContainer_(parts[i]);
    if (!id || seen[id]) continue;
    seen[id] = true;
    out.push(id);
  }
  return out;
}

function normalizeContainer_(value) {
  return String(value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function normalizeForApi_(value) {
  var s = normalizeContainer_(value);
  var m = /^([A-Z]{4})([0-9]{7})$/.exec(s);
  if (m) return m[1] + String(parseInt(m[2].slice(0, 6), 10));
  m = /^([A-Z]{4})([0-9]{6})$/.exec(s);
  if (m) return m[1] + String(parseInt(m[2], 10));
  return s;
}

function parseList_(raw) {
  if (!raw) return [];
  try {
    var v = JSON.parse(raw);
    return Array.isArray(v) ? v : [];
  } catch (ignored) {
    return [];
  }
}

function hasKey_(rows, key) {
  for (var i = rows.length - 1; i >= 0; i--) {
    if (rows[i] && Array.isArray(rows[i]) && rows[i].join('|') === key) return true;
  }
  return false;
}

function station_(loc) {
  return loc && loc.Station ? String(loc.Station).trim() : '';
}

function str_(v) {
  return v === null || v === undefined ? '' : String(v).trim();
}

function truncate_(s, n) {
  s = String(s || '');
  return s.length > n ? s.slice(0, n) + '…' : s;
}
