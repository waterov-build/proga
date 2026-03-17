import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Lang;

class ResultView extends WatchUi.View {

    private var _attemptTime as Lang.Number;

    function initialize(attemptTime as Lang.Number) {
        View.initialize();
        _attemptTime = attemptTime;
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.ResultLayout(dc));
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
        var label = View.findDrawableById("timeLabel") as WatchUi.Text;
        if (label != null) {
            label.setText(TimeUtil.formatMs(_attemptTime));
        }
    }
}
