import Toybox.Lang;
import Toybox.Position;

class Segment {

    var id as Lang.String;
    var name as Lang.String;
    var startLoc as Position.Location;
    var finishLoc as Position.Location;
    var distanceM as Lang.Float;

    function initialize(id as Lang.String, name as Lang.String,
                        startLoc as Position.Location, finishLoc as Position.Location,
                        distanceM as Lang.Float) {
        self.id = id;
        self.name = name;
        self.startLoc = startLoc;
        self.finishLoc = finishLoc;
        self.distanceM = distanceM;
    }
}
