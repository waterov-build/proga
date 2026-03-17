import Toybox.WatchUi;
import Toybox.Graphics;

class RideView extends WatchUi.View {

    private var _rideController as RideController;

    function initialize() {
        View.initialize();
        _rideController = new RideController();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.RideLayout(dc));
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
    }

    function onShow() as Void {
        _rideController.startRide();
    }

    function onHide() as Void {
        _rideController.stopRide();
    }
}

class RideDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}
