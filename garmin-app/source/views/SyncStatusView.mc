import Toybox.WatchUi;
import Toybox.Graphics;

class SyncStatusView extends WatchUi.View {

    private var _syncController as SyncController;

    function initialize() {
        View.initialize();
        _syncController = new SyncController();
    }

    function onLayout(dc as Graphics.Dc) as Void {
        setLayout(Rez.Layouts.SyncStatusLayout(dc));
    }

    function onShow() as Void {
        _syncController.triggerSync();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        View.onUpdate(dc);
    }
}
