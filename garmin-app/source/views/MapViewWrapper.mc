import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Map;

class MapViewWrapper extends WatchUi.MapView {

    function initialize() {
        MapView.initialize();
    }

    function onLayout(dc as Graphics.Dc) as Void {
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        MapView.onUpdate(dc);
    }
}
