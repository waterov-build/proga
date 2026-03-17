import Toybox.Lang;
import Toybox.System;

class Attempt {

    var segmentId as Lang.String;
    var timeMs as Lang.Number;
    var timestamp as Lang.Number;

    function initialize(segmentId as Lang.String, timeMs as Lang.Number) {
        self.segmentId = segmentId;
        self.timeMs = timeMs;
        self.timestamp = System.getClockTime().sec;
    }

    function toDict() as Lang.Dictionary {
        return {
            "segment_id" => segmentId,
            "time_ms" => timeMs,
            "timestamp" => timestamp
        };
    }
}
