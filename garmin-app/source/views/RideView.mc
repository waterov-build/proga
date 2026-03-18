import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Timer;
import Toybox.Lang;

class RideView extends WatchUi.View {

    private var _timer     as Timer.Timer;
    private var _elapsed   as Number = 0;
    private var _timerLabel as WatchUi.Text?;

    function initialize() {
        View.initialize();
        _timer = new Timer.Timer();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.HomeLayout(dc));
        _timerLabel = View.findDrawableById("timerLabel") as WatchUi.Text;
    }

    function onShow() as Void {
        var recordingSvc = Application.getApp().getProperty("recordingSvc") as RecordingService;
        if (recordingSvc != null) {
            recordingSvc.start();
        }
        _timer.start(method(:onTick), 1000, true);
    }

    function onTick() as Void {
        _elapsed++;
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var minutes = _elapsed / 60;
        var seconds = _elapsed % 60;
        if (_timerLabel != null) {
            _timerLabel.setText(minutes.format("%02d") + ":" + seconds.format("%02d"));
        }
        View.onUpdate(dc);
    }

    function onHide() as Void {
        _timer.stop();
    }
}

class RideDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        var recordingSvc = Application.getApp().getProperty("recordingSvc") as RecordingService;
        if (recordingSvc != null) {
            recordingSvc.stop();
        }
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }

    function onSelect() as Boolean {
        WatchUi.pushView(new ResultView(), new ResultDelegate(), WatchUi.SLIDE_UP);
        return true;
    }
}
