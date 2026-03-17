import Toybox.Lang;

class TimeUtil {

    static function formatMs(ms as Lang.Number) as Lang.String {
        var totalSec = ms / 1000;
        var mins = totalSec / 60;
        var secs = totalSec % 60;
        var millis = (ms % 1000) / 10;
        return mins.format("%02d") + ":" + secs.format("%02d") + "." + millis.format("%02d");
    }
}
