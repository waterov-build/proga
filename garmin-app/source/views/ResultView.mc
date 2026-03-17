import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Lang;

class ResultView extends WatchUi.View {

    private var _rankLabel     as WatchUi.Text?;
    private var _yourTimeLabel as WatchUi.Text?;
    private var _bestTimeLabel as WatchUi.Text?;

    function initialize() {
        View.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.HomeLayout(dc));
        _rankLabel     = View.findDrawableById("segmentLabel") as WatchUi.Text;
        _yourTimeLabel = View.findDrawableById("timerLabel")   as WatchUi.Text;
        _bestTimeLabel = View.findDrawableById("statusLabel")  as WatchUi.Text;
    }

    function onShow() as Void {
        var bridge = Application.getApp().getProperty("phoneBridge") as PhoneBridge;
        if (bridge != null) {
            bridge.syncLastResult(method(:onResultReceived));
        }
    }

    function onResultReceived(result as Lang.Dictionary) as Void {
        if (_rankLabel != null) {
            _rankLabel.setText("#" + result["rank"].toString());
        }
        if (_yourTimeLabel != null) {
            _yourTimeLabel.setText(result["your_time"].toString());
        }
        if (_bestTimeLabel != null) {
            _bestTimeLabel.setText(result["best_time"].toString());
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
    }
}

class ResultDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }
}
