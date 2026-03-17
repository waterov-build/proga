import Toybox.Communications;
import Toybox.Lang;

class PhoneBridge {

    function initialize() {
    }

    function sendPendingAttempts() as Void {
        var attempts = LocalStore.loadAttempts();
        if (attempts.size() == 0) {
            Logger.log("PhoneBridge: no pending attempts");
            return;
        }
        var msg = { "type" => "attempts", "data" => attempts };
        Communications.transmit(msg, null, new PhoneBridgeCallback());
        Logger.log("PhoneBridge: sent " + attempts.size() + " attempts");
    }
}

class PhoneBridgeCallback extends Communications.ConnectionListener {

    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {
        Logger.log("PhoneBridge: sync complete");
        LocalStore.clearAttempts();
    }

    function onError() as Void {
        Logger.log("PhoneBridge: sync error");
    }
}
