import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;

class Geo {

    private const EARTH_RADIUS_M = 6371000.0d;

    static function distanceM(a as Position.Location, b as Position.Location) as Lang.Double {
        var aRad = a.toRadians();
        var bRad = b.toRadians();
        var dLat = bRad[0] - aRad[0];
        var dLon = bRad[1] - aRad[1];
        var sinDLat = Math.sin(dLat / 2.0d);
        var sinDLon = Math.sin(dLon / 2.0d);
        var h = sinDLat * sinDLat + Math.cos(aRad[0]) * Math.cos(bRad[0]) * sinDLon * sinDLon;
        return 2.0d * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }
}
