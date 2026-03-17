using Toybox.Application as App;
using Toybox.WatchUi as Ui;
using Toybox.Position as Position;
using Toybox.Sensor as Sensor;
using Toybox.Timer as Timer;
using Toybox.BluetoothLowEnergy as Ble;
using Toybox.Activity as Activity;
using Toybox.FitContributor as FitContributor;
using Toybox.System as Sys;
using Toybox.Time as Time;
using Toybox.Lang as Lang;
using Toybox.Graphics as Graphics;
using Toybox.Math as Math;

//
// ENDURO PLUS — Garmin Connect IQ watch application
// Tracks rider position and speed along enduro race routes,
// awards score at checkpoints based on elapsed time, and
// exposes a full BLE GATT service for result comparison with other
// participants via the Android companion app.
//

// ---------- Constants ----------

// Score awarded for reaching a checkpoint (base value).
// Actual score = BASE_CHECKPOINT_SCORE + timeBonus(elapsed)
const BASE_CHECKPOINT_SCORE = 100;

// Radius (metres) within which a checkpoint is considered "reached"
const CHECKPOINT_RADIUS_M = 20;

// ---------- BLE GATT profile UUIDs (custom 128-bit) ----------
// All share the base 12340000-1234-1234-1234-123456789abc pattern.
// The Android companion uses these same UUIDs to discover characteristics.

// Primary service UUID
const BLE_SERVICE_UUID       = "12340000-1234-1234-1234-123456789abc";
// Characteristic: total score + checkpoint results (read / notify)
const BLE_CHAR_SCORE_UUID    = "12340001-1234-1234-1234-123456789abc";
// Characteristic: GPS track polyline snapshot (read)
const BLE_CHAR_TRACK_UUID    = "12340002-1234-1234-1234-123456789abc";
// Characteristic: checkpoint list pushed from companion (write)
const BLE_CHAR_CP_LIST_UUID  = "12340003-1234-1234-1234-123456789abc";
// Characteristic: live status string (read / notify)
const BLE_CHAR_STATUS_UUID   = "12340004-1234-1234-1234-123456789abc";

// ---------- Data structures ----------

// Represents a single GPS checkpoint on the route.
// Fields:
//   lat  - latitude  (degrees, WGS-84)
//   lon  - longitude (degrees, WGS-84)
//   name - human-readable label shown on the watch UI
class Checkpoint {
    var lat;
    var lon;
    var name;

    function initialize(lat, lon, name) {
        self.lat = lat;
        self.lon = lon;
        self.name = name;
    }
}

// Holds the result for one completed checkpoint segment.
// Fields:
//   checkpoint - the Checkpoint object
//   elapsedSec - time from race start to this checkpoint (seconds)
//   score      - points awarded for this checkpoint
class CheckpointResult {
    var checkpoint;
    var elapsedSec;
    var score;

    function initialize(checkpoint, elapsedSec, score) {
        self.checkpoint = checkpoint;
        self.elapsedSec = elapsedSec;
        self.score = score;
    }
}

// ---------- Score formula ----------

// Calculates bonus score based on how quickly a checkpoint was reached.
// The faster the elapsed time relative to a "par" time, the higher the bonus.
// parSec — expected/par time for the segment (seconds)
// elapsedSec — actual elapsed time (seconds)
function calcTimeBonus(parSec, elapsedSec) {
    if (elapsedSec <= 0 || parSec <= 0) {
        return 0;
    }
    var ratio = parSec.toFloat() / elapsedSec.toFloat();
    // Clamp to [0.5, 2.0] so the bonus stays in a sane range
    if (ratio < 0.5) { ratio = 0.5; }
    if (ratio > 2.0) { ratio = 2.0; }
    // Scale: ratio 1.0 → bonus 0, ratio 2.0 → bonus +100, ratio 0.5 → bonus -50
    return (ratio * 100 - 100).toNumber();
}

// ---------- Position helpers ----------

// Haversine distance in metres between two (lat, lon) pairs in degrees.
function haversineDistM(lat1, lon1, lat2, lon2) {
    var R = 6371000.0; // Earth radius, metres
    var dLat = (lat2 - lat1) * Math.PI / 180.0;
    var dLon = (lon2 - lon1) * Math.PI / 180.0;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
          + Math.cos(lat1 * Math.PI / 180.0) * Math.cos(lat2 * Math.PI / 180.0)
          * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// ---------- BLE companion service ----------

// EnduroBleDelegate handles BLE events for the companion connection.
// It advertises race results so the Android companion app can collect
// and compare them across participants.
// Receives the EnduroPlusModel so it can read live telemetry without
// coupling to the application lifecycle.
class EnduroBleDelegate extends Ble.BleDelegate {

    var _model;

    function initialize(model) {
        BleDelegate.initialize();
        _model = model;
    }

    // Called when a central (Android phone) connects or disconnects.
    function onConnectedStateChanged(device, state) {
        if (state == Ble.CONNECTION_STATE_CONNECTED) {
            Sys.println("BLE: companion connected – " + device.getName());
            _model.onBleConnected(device);
        } else {
            Sys.println("BLE: companion disconnected");
        }
    }

    // The companion reads a characteristic — serve the matching payload.
    function onCharacteristicRead(requestId, offset, responseNeeded, characteristic) {
        var uuid = characteristic.getUuid();
        var payload = null;

        if (uuid.equals(Ble.stringToUuid(BLE_CHAR_SCORE_UUID))) {
            payload = _model.serializeResults();
        } else if (uuid.equals(Ble.stringToUuid(BLE_CHAR_TRACK_UUID))) {
            payload = _model.serializeTrack();
        } else if (uuid.equals(Ble.stringToUuid(BLE_CHAR_STATUS_UUID))) {
            payload = _model.getStatusString().toUtf8Array();
        }

        if (responseNeeded) {
            Ble.sendResponse(requestId, Ble.ATT_ERROR_SUCCESS, payload);
        }
    }

    // The companion writes a characteristic — handle checkpoint list updates.
    function onCharacteristicWrite(requestId, preparedWrite, responseNeeded, offset, value, characteristic) {
        var uuid = characteristic.getUuid();
        if (uuid.equals(Ble.stringToUuid(BLE_CHAR_CP_LIST_UUID))) {
            // Decode UTF-8 payload and pass to model for parsing
            var text = value != null ? value.decodeToString() : "";
            Sys.println("BLE: checkpoint list received (" + text.length() + " bytes)");
            _model.loadCheckpointsFromString(text);
        } else {
            Sys.println("BLE: write on unknown char, length=" + (value != null ? value.size() : 0));
        }
        if (responseNeeded) {
            Ble.sendResponse(requestId, Ble.ATT_ERROR_SUCCESS, null);
        }
    }
}

// ---------- Main view ----------

// EnduroPlusView renders the race HUD:
//   Line 1 — race timer  (MM:SS)
//   Line 2 — total score
//   Line 3 — current speed  (km/h)
//   Line 4 — GPS point count / last checkpoint name / status
class EnduroPlusView extends Ui.View {

    var _model;

    function initialize(model) {
        View.initialize();
        _model = model;
    }

    function onLayout(dc) {
        setLayout(Rez.Layouts.MainLayout(dc));
    }

    function onUpdate(dc) {
        // Clear background
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var w = dc.getWidth();
        var h = dc.getHeight();

        // Timer (top)
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.15, Graphics.FONT_LARGE,
                    _model.getTimerString(), Graphics.TEXT_JUSTIFY_CENTER);

        // Score (upper-middle)
        dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.38, Graphics.FONT_MEDIUM,
                    "SCORE: " + _model.getTotalScore(), Graphics.TEXT_JUSTIFY_CENTER);

        // Speed (lower-middle)
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.57, Graphics.FONT_MEDIUM,
                    _model.getSpeedString(), Graphics.TEXT_JUSTIFY_CENTER);

        // Status / next checkpoint / track stats (bottom)
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.75, Graphics.FONT_SMALL,
                    _model.getStatusString(), Graphics.TEXT_JUSTIFY_CENTER);

        // GPS accuracy bar (very bottom)
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.88, Graphics.FONT_XTINY,
                    _model.getGpsAccuracyString(), Graphics.TEXT_JUSTIFY_CENTER);
    }
}

// ---------- Input delegate ----------

// EnduroPlusInputDelegate maps hardware button presses:
//   SELECT — start / stop race
//   BACK   — return to launcher (only when not racing)
class EnduroPlusInputDelegate extends Ui.BehaviorDelegate {

    var _model;

    function initialize(model) {
        BehaviorDelegate.initialize();
        _model = model;
    }

    function onSelect() {
        if (!_model.isRacing) {
            _model.startRace();
        } else {
            _model.stopRace();
        }
        Ui.requestUpdate();
        return true;
    }

    function onBack() {
        if (!_model.isRacing) {
            Ui.popView(Ui.SLIDE_IMMEDIATE);
        }
        return true;
    }
}

// ---------- Race model ----------

// EnduroPlusModel is the data/logic layer:
//   • holds the list of checkpoints to visit in order
//   • records the full GPS track via TrackRecorder
//   • tracks position and speed via Position callbacks
//   • awards score when the rider enters a checkpoint radius
//   • accepts checkpoint updates pushed via BLE from the companion
//   • serialises results and track for BLE export
class EnduroPlusModel {

    var isRacing = false;
    var _startMoment = null;
    var _totalScore = 0;
    var _results = [];            // Array of CheckpointResult
    var _nextCpIdx = 0;           // Index into _checkpoints
    var _checkpoints = [];        // Array of Checkpoint
    var _lastLat = null;
    var _lastLon = null;
    var _lastSpeedMs = 0.0;       // m/s
    var _gpsAccuracy = 0;         // Position.QUALITY_* constant
    var _timer = null;
    var _trackRecorder = null;    // TrackRecorder instance

    // Demo checkpoint list — replace with course-specific data loaded
    // from FIT course files or pushed via BLE from the companion app.
    function initialize() {
        _trackRecorder = new TrackRecorder();
        _checkpoints = [
            new Checkpoint(55.751244, 37.618423, "CP1-Start"),
            new Checkpoint(55.752100, 37.619800, "CP2-Rock"),
            new Checkpoint(55.753500, 37.621200, "CP3-Hill"),
            new Checkpoint(55.754800, 37.622500, "CP4-River"),
            new Checkpoint(55.756000, 37.624000, "CP5-Finish"),
        ];
    }

    // Begin position tracking and start the race timer.
    function startRace() {
        _startMoment = Time.now();
        _totalScore = 0;
        _results = [];
        _nextCpIdx = 0;
        isRacing = true;

        _trackRecorder.start(_startMoment);

        Position.enableLocationEvents(
            Position.LOCATION_CONTINUOUS,
            method(:onPositionUpdate)
        );

        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
        Sensor.enableSensorEvents(method(:onSensorUpdate));

        // Refresh display every second
        _timer = new Timer.Timer();
        _timer.start(method(:onTick), 1000, true);
    }

    // Stop tracking and persist results as a FIT activity.
    function stopRace() {
        isRacing = false;
        if (_timer != null) {
            _timer.stop();
            _timer = null;
        }
        _trackRecorder.stop();
        Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
        Sensor.enableSensorEvents(null);
        _saveResults();
    }

    // Callback: GPS position update.
    function onPositionUpdate(info) {
        if (info == null) {
            return;
        }
        if (info.accuracy != null) {
            _gpsAccuracy = info.accuracy;
        }
        var loc = info.position;
        if (loc == null) {
            return;
        }
        var deg = loc.toDegrees();
        _lastLat = deg[0];
        _lastLon = deg[1];
        if (info.speed != null) {
            _lastSpeedMs = info.speed;
        }
        if (isRacing) {
            _trackRecorder.addPoint(_lastLat, _lastLon, _lastSpeedMs);
            _checkCheckpoints();
        }
        Ui.requestUpdate();
    }

    // Callback: sensor data update (heart rate, cadence, etc.).
    function onSensorUpdate(sensorInfo) {
        if (sensorInfo != null && sensorInfo.heartRate != null) {
            Sys.println("HR: " + sensorInfo.heartRate);
        }
    }

    // Callback: 1-second tick to refresh the display timer.
    function onTick() {
        Ui.requestUpdate();
    }

    // Test whether the rider has entered the next checkpoint's radius.
    function _checkCheckpoints() {
        if (_nextCpIdx >= _checkpoints.size()) {
            return; // All checkpoints reached
        }
        if (_lastLat == null || _lastLon == null) {
            return;
        }
        var cp = _checkpoints[_nextCpIdx];
        var dist = haversineDistM(_lastLat, _lastLon, cp.lat, cp.lon);
        if (dist <= CHECKPOINT_RADIUS_M) {
            _reachCheckpoint(cp);
        }
    }

    // Award score for reaching a checkpoint and advance to the next one.
    function _reachCheckpoint(cp) {
        var now = Time.now();
        var elapsed = (now.value() - _startMoment.value()).toFloat();
        // Par time placeholder: 60 s × checkpoint index.
        // TODO: replace with course-specific par times loaded from the
        // companion app or a FIT course file so scoring reflects real
        // terrain difficulty and checkpoint distances.
        var parSec = 60 * (_nextCpIdx + 1);
        var bonus = calcTimeBonus(parSec, elapsed);
        var score = BASE_CHECKPOINT_SCORE + bonus;
        if (score < 0) { score = 0; }
        _totalScore += score;
        _results.add(new CheckpointResult(cp, elapsed.toNumber(), score));
        Sys.println("Reached: " + cp.name + "  elapsed=" + elapsed + "s  score=" + score);
        _nextCpIdx++;
    }

    // Persist results as a FIT activity record so they can be synced via
    // Garmin Connect and compared with other participants.
    function _saveResults() {
        var session = Activity.createSession({
            :name     => "ENDURO PLUS",
            :sport    => Activity.SPORT_GENERIC,
            :subSport => Activity.SUB_SPORT_ENDURO,
        });
        if (session == null) {
            return;
        }
        // FIT custom field.  fieldId=0 is the first user-defined field slot;
        // Connect IQ reserves IDs 0-7 for application-defined fields within
        // a given mesgType so there is no conflict with standard FIT fields.
        // See the FitContributor API docs for the full field-ID policy.
        var scoreField = session.createField(
            "EnduroScore",
            0,
            FitContributor.DATA_TYPE_SINT32,
            { :mesgType => FitContributor.MESG_TYPE_SESSION, :units => "pts" }
        );
        if (scoreField != null) {
            scoreField.setData(_totalScore);
        }
        // Custom field: total track distance (metres)
        var distField = session.createField(
            "TrackDistM",
            1,
            FitContributor.DATA_TYPE_FLOAT,
            { :mesgType => FitContributor.MESG_TYPE_SESSION, :units => "m" }
        );
        if (distField != null) {
            distField.setData(_trackRecorder.getTotalDistanceM().toFloat());
        }
        session.stop();
        session.save();
        Sys.println("Session saved. Score=" + _totalScore
                    + " pts, track=" + _trackRecorder.getPointCount() + " points recorded");
    }

    // Parses a checkpoint list pushed from the companion app over BLE.
    // Expected format (one checkpoint per line): "name,lat,lon"
    // Example: "CP1-Start,55.751244,37.618423\nCP2-Rock,55.752100,37.619800"
    function loadCheckpointsFromString(text) {
        if (text == null || text.length() == 0) {
            return;
        }
        var newList = [];
        var lines = text.split("\n");
        for (var i = 0; i < lines.size(); i++) {
            var parts = lines[i].trim().split(",");
            if (parts.size() >= 3) {
                var name = parts[0].trim();
                var lat  = parts[1].trim().toFloat();
                var lon  = parts[2].trim().toFloat();
                if (name.length() > 0 && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                    newList.add(new Checkpoint(lat, lon, name));
                }
            }
        }
        if (newList.size() > 0) {
            _checkpoints = newList;
            Sys.println("Loaded " + _checkpoints.size() + " checkpoints via BLE");
        }
    }

    // Called by the BLE delegate when a companion device connects.
    function onBleConnected(device) {
        Sys.println("Companion connected: " + device.getName());
    }

    // Serialise current results to a byte array suitable for BLE transfer.
    // Format (UTF-8 text): "score=NNN;cp1=name,time,score;cp2=..."
    function serializeResults() {
        var s = "score=" + _totalScore;
        for (var i = 0; i < _results.size(); i++) {
            var r = _results[i];
            s = s + ";cp" + (i + 1) + "=" + r.checkpoint.name
                  + "," + r.elapsedSec + "," + r.score;
        }
        return s.toUtf8Array();
    }

    // Serialise the GPS track for BLE transfer.
    function serializeTrack() {
        return _trackRecorder.serialize().toUtf8Array();
    }

    // --- View helpers ---

    // Returns elapsed time as "MM:SS" string (or "--:--" before start).
    function getTimerString() {
        if (_startMoment == null) {
            return "--:--";
        }
        var now = Time.now();
        var totalSec = now.value() - _startMoment.value();
        var mm = (totalSec / 60).toNumber();
        var ss = (totalSec % 60).toNumber();
        return mm.format("%02d") + ":" + ss.format("%02d");
    }

    function getTotalScore() {
        return _totalScore;
    }

    // Returns current speed formatted as "XX.X km/h"
    function getSpeedString() {
        var kmh = _lastSpeedMs * 3.6;
        return kmh.format("%.1f") + " km/h";
    }

    // Returns a short status line for the HUD.
    function getStatusString() {
        if (!isRacing) {
            return "PRESS SELECT";
        }
        if (_nextCpIdx >= _checkpoints.size()) {
            return "FINISHED! " + _totalScore + "pts";
        }
        var trackPoints = _trackRecorder.getPointCount();
        return "NEXT: " + _checkpoints[_nextCpIdx].name + " [" + trackPoints + "]";
    }

    // Returns a human-readable GPS accuracy string.
    function getGpsAccuracyString() {
        switch (_gpsAccuracy) {
            case Position.QUALITY_GOOD:
                return "GPS: GOOD";
            case Position.QUALITY_USABLE:
                return "GPS: OK";
            case Position.QUALITY_POOR:
                return "GPS: POOR";
            case Position.QUALITY_LAST_KNOWN:
                return "GPS: LAST";
            default:
                return "GPS: --";
        }
    }
}

// ---------- Application entry point ----------

class EnduroPlusApp extends App.AppBase {

    var _model;
    var _bleDelegate;

    function initialize() {
        AppBase.initialize();
        _model = new EnduroPlusModel();
    }

    function onStart(state) {
        // Register BLE GATT peripheral service with four characteristics:
        //   SCORE    (read/notify) — serialised results
        //   TRACK    (read)       — GPS track polyline
        //   CP_LIST  (write)      — checkpoint list from companion
        //   STATUS   (read/notify)— live status string
        _bleDelegate = new EnduroBleDelegate(_model);

        Ble.registerProfile({
            :uuid       => Ble.stringToUuid(BLE_SERVICE_UUID),
            :appearance => Ble.APPEARANCE_RUNNING_SENSOR,
            :characteristics => [
                {
                    :uuid       => Ble.stringToUuid(BLE_CHAR_SCORE_UUID),
                    :properties => Ble.PROPERTY_READ | Ble.PROPERTY_NOTIFY,
                    :permissions => Ble.PERMISSION_READ,
                },
                {
                    :uuid       => Ble.stringToUuid(BLE_CHAR_TRACK_UUID),
                    :properties => Ble.PROPERTY_READ,
                    :permissions => Ble.PERMISSION_READ,
                },
                {
                    :uuid        => Ble.stringToUuid(BLE_CHAR_CP_LIST_UUID),
                    :properties  => Ble.PROPERTY_WRITE,
                    :permissions => Ble.PERMISSION_WRITE,
                },
                {
                    :uuid        => Ble.stringToUuid(BLE_CHAR_STATUS_UUID),
                    :properties  => Ble.PROPERTY_READ | Ble.PROPERTY_NOTIFY,
                    :permissions => Ble.PERMISSION_READ,
                },
            ],
        });

        Ble.setDelegate(_bleDelegate);
    }

    function onStop(state) {
        if (_model.isRacing) {
            _model.stopRace();
        }
    }

    function getInitialView() {
        return [
            new EnduroPlusView(_model),
            new EnduroPlusInputDelegate(_model),
        ];
    }
}
