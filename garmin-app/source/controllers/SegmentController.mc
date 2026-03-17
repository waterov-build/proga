import Toybox.Lang;

class SegmentController {

    private var _localStore as LocalStore;

    function initialize() {
        _localStore = new LocalStore();
    }

    function getSegments() as Lang.Array {
        return _localStore.loadSegments();
    }

    function selectSegment(segmentId as Lang.String) as Segment or Null {
        var segments = getSegments();
        for (var i = 0; i < segments.size(); i++) {
            var seg = segments[i] as Segment;
            if (seg.id.equals(segmentId)) {
                return seg;
            }
        }
        return null;
    }
}
