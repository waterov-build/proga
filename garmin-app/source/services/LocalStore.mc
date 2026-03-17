import Toybox.Application.Storage;
import Toybox.Lang;

class LocalStore {

    private const KEY_SEGMENTS = "segments";
    private const KEY_ATTEMPTS = "attempts";

    function initialize() {
    }

    function loadSegments() as Lang.Array {
        var data = Storage.getValue(KEY_SEGMENTS);
        return data != null ? data as Lang.Array : [] as Lang.Array;
    }

    function saveSegments(segments as Lang.Array) as Void {
        Storage.setValue(KEY_SEGMENTS, segments);
    }

    static function saveAttempt(attempt as Attempt) as Void {
        var attempts = Storage.getValue("attempts") as Lang.Array;
        if (attempts == null) { attempts = [] as Lang.Array; }
        attempts.add(attempt.toDict());
        Storage.setValue("attempts", attempts);
    }

    static function loadAttempts() as Lang.Array {
        var data = Storage.getValue("attempts") as Lang.Array;
        return data != null ? data : [] as Lang.Array;
    }

    static function clearAttempts() as Void {
        Storage.setValue("attempts", [] as Lang.Array);
    }
}
