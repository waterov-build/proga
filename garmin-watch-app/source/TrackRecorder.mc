using Toybox.Position as Position;
using Toybox.System as Sys;
using Toybox.Lang as Lang;
using Toybox.Time as Time;
using Toybox.Math as Math;

//
// TrackRecorder — stores a GPS polyline recorded during a race.
//
// Usage:
//   var recorder = new TrackRecorder();
//   recorder.start();                         // called when race starts
//   recorder.addPoint(lat, lon, speedMs);     // called from position callback
//   recorder.stop();                          // called when race ends
//   var pts = recorder.getPoints();           // Array of TrackPoint
//   var encoded = recorder.serialize();       // compact string for BLE export
//

// Maximum number of track points to keep in memory.
// At one point per second this covers ~17 minutes; increase if needed.
const MAX_TRACK_POINTS = 1000;

// Minimum distance (metres) between consecutive stored points.
// Filters out GPS jitter without losing meaningful movement.
const MIN_POINT_DIST_M = 5;

// One recorded GPS sample.
class TrackPoint {
    var lat;       // degrees WGS-84
    var lon;       // degrees WGS-84
    var speedMs;   // m/s (may be 0 if sensor unavailable)
    var timeSec;   // seconds since race start

    function initialize(lat, lon, speedMs, timeSec) {
        self.lat     = lat;
        self.lon     = lon;
        self.speedMs = speedMs;
        self.timeSec = timeSec;
    }
}

class TrackRecorder {

    var _points = [];        // Array of TrackPoint
    var _isRecording = false;
    var _startMoment = null; // Toybox.Time.Moment at race start

    function initialize() {
        _points = [];
    }

    // Call when the race begins.
    function start(startMoment) {
        _startMoment = startMoment;
        _points = [];
        _isRecording = true;
    }

    // Call when the race ends.
    function stop() {
        _isRecording = false;
    }

    // Called from the position update callback with the latest fix.
    // Drops the point if it is too close to the previous one to avoid
    // flooding memory with stationary GPS noise.
    function addPoint(lat, lon, speedMs) {
        if (!_isRecording || _startMoment == null) {
            return;
        }
        if (_points.size() >= MAX_TRACK_POINTS) {
            // Ring-buffer: drop oldest point to stay within memory limit
            _points = _points.slice(1, null);
        }
        var timeSec = (Time.now().value() - _startMoment.value()).toNumber();
        if (_points.size() > 0) {
            var last = _points[_points.size() - 1];
            var d = _haversineDist(last.lat, last.lon, lat, lon);
            if (d < MIN_POINT_DIST_M) {
                return; // Too close — skip
            }
        }
        _points.add(new TrackPoint(lat, lon, speedMs, timeSec));
    }

    function getPoints() {
        return _points;
    }

    function getPointCount() {
        return _points.size();
    }

    // Returns total track distance in metres (sum of consecutive segments).
    function getTotalDistanceM() {
        var dist = 0.0;
        for (var i = 1; i < _points.size(); i++) {
            var p0 = _points[i - 1];
            var p1 = _points[i];
            dist += _haversineDist(p0.lat, p0.lon, p1.lat, p1.lon);
        }
        return dist;
    }

    // Serialises the track to a compact CSV-like string for BLE transfer.
    // Format: "track:<count>|<lat>,<lon>,<spd>,<t>|..."
    // Coordinates are rounded to 6 decimal places (~0.1 m precision).
    // Only the most recent MAX_BLE_POINTS points are included to fit BLE MTU.
    function serialize() {
        var MAX_BLE_POINTS = 50; // ~1500 bytes at 6dp / point
        var start = _points.size() > MAX_BLE_POINTS
                    ? _points.size() - MAX_BLE_POINTS
                    : 0;
        var s = "track:" + _points.size();
        for (var i = start; i < _points.size(); i++) {
            var p = _points[i];
            s = s + "|" + p.lat.format("%.6f")
                      + "," + p.lon.format("%.6f")
                      + "," + p.speedMs.format("%.1f")
                      + "," + p.timeSec;
        }
        return s;
    }

    // Private: Haversine distance between two lat/lon pairs in metres.
    function _haversineDist(lat1, lon1, lat2, lon2) {
        var R    = 6371000.0;
        var dLat = (lat2 - lat1) * Math.PI / 180.0;
        var dLon = (lon2 - lon1) * Math.PI / 180.0;
        var a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(lat1 * Math.PI / 180.0)
                 * Math.cos(lat2 * Math.PI / 180.0)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
