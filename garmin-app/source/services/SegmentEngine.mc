import Toybox.Lang;
import Toybox.Position;
import Toybox.Math;

class SegmentEngine {

    private var _segments      as Array<Lang.Dictionary> = [];
    private var _activeSegment as Lang.Dictionary?       = null;
    private var _startTime     as Number                 = 0;

    function initialize() {
    }

    function loadSegments(segments as Array<Lang.Dictionary>) as Void {
        _segments = segments;
    }

    function update(location as Position.Location) as Void {
        if (_activeSegment == null) {
            _checkSegmentStart(location);
        } else {
            _checkSegmentEnd(location);
        }
    }

    function hasActiveSegment() as Boolean {
        return _activeSegment != null;
    }

    function getCurrentSegmentName() as String {
        if (_activeSegment != null) {
            return _activeSegment["name"] as String;
        }
        return "";
    }

    function getElapsedMs() as Number {
        if (_activeSegment == null) { return 0; }
        return (Time.now().value() - _startTime) * 1000;
    }

    private function _checkSegmentStart(location as Position.Location) as Void {
        for (var i = 0; i < _segments.size(); i++) {
            var seg = _segments[i] as Lang.Dictionary;
            if (_isNear(location, seg["start_lat"] as Float, seg["start_lon"] as Float, 20.0f)) {
                _activeSegment = seg;
                _startTime     = Time.now().value();
                return;
            }
        }
    }

    private function _checkSegmentEnd(location as Position.Location) as Void {
        var seg = _activeSegment as Lang.Dictionary;
        if (_isNear(location, seg["end_lat"] as Float, seg["end_lon"] as Float, 20.0f)) {
            _activeSegment = null;
        }
    }

    private function _isNear(location as Position.Location, lat as Float, lon as Float, thresholdM as Float) as Boolean {
        var coords = location.toDegrees();
        var dLat   = coords[0] - lat;
        var dLon   = coords[1] - lon;
        var dist   = Math.sqrt(dLat * dLat + dLon * dLon) * 111319.5f;
        return dist < thresholdM;
    }
}
