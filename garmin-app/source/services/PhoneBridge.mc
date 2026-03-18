import Toybox.Communications;
import Toybox.Lang;

class PhoneBridge {

    function initialize() {
        Communications.registerForPhoneAppMessages(method(:onMessageReceived));
    }

    function sendActivity(payload as Lang.Dictionary) as Void {
        Communications.transmitMessage(payload, {}, method(:onSendComplete));
    }

    function syncLastResult(callback as Lang.Method) as Void {
        Communications.makeWebRequest(
            Application.getApp().getProperty("backendUrl") as String + "/sync/last-result",
            {},
            {:method => Communications.HTTP_REQUEST_METHOD_GET,
             :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON},
            callback
        );
    }

    function onMessageReceived(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data as Lang.Dictionary;
        if (data["type"].equals("segments")) {
            var engine = Application.getApp().getProperty("segmentEngine") as SegmentEngine;
            if (engine != null) {
                engine.loadSegments(data["segments"] as Array<Lang.Dictionary>);
            }
        }
    }

    function onSendComplete(responseCode as Number, data as Lang.Dictionary?) as Void {
    }
}
