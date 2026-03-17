import Toybox.Position;
import Toybox.Lang;
import Toybox.System;

enum SegmentState {
    STATE_IDLE,
    STATE_ARMED,
    STATE_TIMING,
    STATE_RECORDED
}

class SegmentEngine {

    private const START_RADIUS_M = 30;
    private const FINISH_RADIUS_M = 30;
    private const MIN_SPEED_MPS = 1.5f;

    private var _state as SegmentState = STATE_IDLE;
    private var _startTime as Lang.Number = 0;
    private var _currentSegment as Segment?;
    private var _posService as PositionService;

    function initialize() {
        _posService = new PositionService();
    }

    function arm() as Void {
        _state = STATE_ARMED;
        _posService.startListening(method(:onPosition));
    }

    function disarm() as Void {
        _state = STATE_IDLE;
        _posService.stopListening();
    }

    function setSegment(seg as Segment) as Void {
        _currentSegment = seg;
    }

    function onPosition(info as Position.Info) as Void {
        if (_currentSegment == null) { return; }
        var loc = info.position;
        var speed = info.speed;
        if (_state == STATE_ARMED) {
            if (speed >= MIN_SPEED_MPS && Geo.distanceM(loc, _currentSegment.startLoc) <= START_RADIUS_M) {
                _state = STATE_TIMING;
                _startTime = System.getTimer();
                Logger.log("SegmentEngine: timing started");
            }
        } else if (_state == STATE_TIMING) {
            if (Geo.distanceM(loc, _currentSegment.finishLoc) <= FINISH_RADIUS_M) {
                var elapsed = System.getTimer() - _startTime;
                _state = STATE_RECORDED;
                Logger.log("SegmentEngine: recorded " + elapsed + "ms");
                LocalStore.saveAttempt(new Attempt(_currentSegment.id, elapsed));
                _state = STATE_ARMED;
            }
        }
    }
}
