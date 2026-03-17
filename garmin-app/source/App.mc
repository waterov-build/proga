import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

class EnduroApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Lang.Dictionary?) as Void {
        var segmentEngine = new SegmentEngine();
        var phoneBridge   = new PhoneBridge();
        var recordingSvc  = new RecordingService(segmentEngine);
        Application.getApp().setProperty("segmentEngine", segmentEngine);
        Application.getApp().setProperty("phoneBridge",   phoneBridge);
        Application.getApp().setProperty("recordingSvc",  recordingSvc);
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new HomeView(), new HomeDelegate()];
    }

    function onStop(state as Lang.Dictionary?) as Void {
        var recordingSvc = Application.getApp().getProperty("recordingSvc") as RecordingService;
        if (recordingSvc != null) {
            recordingSvc.stop();
        }
    }
}
