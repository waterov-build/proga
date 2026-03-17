import Toybox.FitContributor;
import Toybox.ActivityRecording;
import Toybox.Lang;

class RecordingService {

    private var _session as ActivityRecording.Session?;

    function initialize() {
    }

    function start() as Void {
        var opts = {
            :name => "EnduroRide",
            :sport => ActivityRecording.SPORT_CYCLING,
            :subSport => ActivityRecording.SUB_SPORT_MOUNTAIN
        };
        _session = ActivityRecording.createSession(opts);
        _session.start();
        Logger.log("RecordingService: session started");
    }

    function stop() as Void {
        if (_session != null) {
            _session.stop();
            _session.save();
            _session = null;
            Logger.log("RecordingService: session saved");
        }
    }
}
