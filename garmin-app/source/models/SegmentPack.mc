import Toybox.Lang;

class SegmentPack {

    var eventId as Lang.String;
    var eventName as Lang.String;
    var segments as Lang.Array;
    var version as Lang.Number;

    function initialize(eventId as Lang.String, eventName as Lang.String,
                        segments as Lang.Array, version as Lang.Number) {
        self.eventId = eventId;
        self.eventName = eventName;
        self.segments = segments;
        self.version = version;
    }

    static function fromDict(d as Lang.Dictionary) as SegmentPack {
        var segs = d["segments"] as Lang.Array;
        return new SegmentPack(d["event_id"] as Lang.String,
                               d["event_name"] as Lang.String,
                               segs,
                               d["version"] as Lang.Number);
    }
}
