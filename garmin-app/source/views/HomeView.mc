import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Lang;

class HomeView extends WatchUi.View {

    private var _statusLabel as WatchUi.Text?;
    private var _segmentLabel as WatchUi.Text?;

    function initialize() {
        View.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.HomeLayout(dc));
        _statusLabel  = View.findDrawableById("statusLabel")  as WatchUi.Text;
        _segmentLabel = View.findDrawableById("segmentLabel") as WatchUi.Text;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var engine = Application.getApp().getProperty("segmentEngine") as SegmentEngine;
        if (engine != null && engine.hasActiveSegment()) {
            _statusLabel.setText(WatchUi.loadResource(Rez.Strings.Recording) as String);
            _segmentLabel.setText(engine.getCurrentSegmentName());
        } else {
            _statusLabel.setText(WatchUi.loadResource(Rez.Strings.Ready) as String);
            _segmentLabel.setText("--");
        }
        View.onUpdate(dc);
    }
}

class HomeDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        WatchUi.pushView(new RideView(), new RideDelegate(), WatchUi.SLIDE_UP);
        return true;
    }
}
