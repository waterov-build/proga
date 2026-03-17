import Toybox.WatchUi;
import Toybox.Graphics;

class HomeView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.HomeLayout(dc));
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
    }
}

class HomeDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        WatchUi.pushView(new SegmentSelectView(), new SegmentSelectDelegate(), WatchUi.SLIDE_LEFT);
        return true;
    }
}
