import Toybox.Position;
import Toybox.Sensor;
import Toybox.Lang;

class RecordingService {

    private var _engine   as SegmentEngine;
    private var _active   as Boolean = false;
    private var _trackPts as Array<Lang.Dictionary> = [];

    function initialize(engine as SegmentEngine) {
        _engine = engine;
    }

    function start() as Void {
        if (_active) { return; }
        _active   = true;
        _trackPts = [];
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE]);
        Sensor.enableSensorEvents(method(:onSensor));
    }

    function stop() as Void {
        if (!_active) { return; }
        _active = false;
        Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
        Sensor.enableSensorEvents(null);
        _flush();
    }

    function onPosition(info as Position.Info) as Void {
        if (!_active || info.position == null) { return; }
        _engine.update(info.position);
        _trackPts.add({
            "lat"  => info.position.toDegrees()[0],
            "lon"  => info.position.toDegrees()[1],
            "time" => Time.now().value()
        });
    }

    function onSensor(info as Sensor.Info) as Void {
    }

    private function _flush() as Void {
        if (_trackPts.size() == 0) { return; }
        var bridge = Application.getApp().getProperty("phoneBridge") as PhoneBridge;
        if (bridge != null) {
            bridge.sendActivity({"track" => _trackPts});
        }
        _trackPts = [];
    }
}
