import Toybox.Lang;
import Toybox.Communications;

class SyncController {

    private var _phoneBridge as PhoneBridge;

    function initialize() {
        _phoneBridge = new PhoneBridge();
    }

    function triggerSync() as Void {
        Logger.log("SyncController: initiating sync");
        _phoneBridge.sendPendingAttempts();
    }
}
