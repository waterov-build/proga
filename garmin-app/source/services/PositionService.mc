import Toybox.Position;
import Toybox.Lang;

class PositionService {

    private var _listener as Method?;

    function initialize() {
    }

    function startListening(listener as Method) as Void {
        _listener = listener;
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
    }

    function stopListening() as Void {
        Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
        _listener = null;
    }

    function onPosition(info as Position.Info) as Void {
        if (_listener != null && info.accuracy >= Position.QUALITY_USABLE) {
            (_listener as Method).invoke(info);
        }
    }
}
