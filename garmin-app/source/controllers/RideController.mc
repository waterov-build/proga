import Toybox.Lang;
import Toybox.System;

class RideController {

    private var _recordingService as RecordingService;
    private var _segmentEngine as SegmentEngine;
    private var _isRiding as Lang.Boolean = false;

    function initialize() {
        _recordingService = new RecordingService();
        _segmentEngine = new SegmentEngine();
    }

    function startRide() as Void {
        if (!_isRiding) {
            _isRiding = true;
            _recordingService.start();
            _segmentEngine.arm();
            Logger.log("RideController: ride started");
        }
    }

    function stopRide() as Void {
        if (_isRiding) {
            _isRiding = false;
            _segmentEngine.disarm();
            _recordingService.stop();
            Logger.log("RideController: ride stopped");
        }
    }
}
