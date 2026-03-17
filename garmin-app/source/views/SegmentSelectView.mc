import Toybox.WatchUi;
import Toybox.Graphics;

class SegmentSelectView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.SegmentSelectLayout(dc));
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
    }
}

class SegmentSelectDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        WatchUi.pushView(new RideView(), new RideDelegate(), WatchUi.SLIDE_LEFT);
        return true;
    }
}
