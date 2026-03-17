using Toybox.Activity as Activity;
using Toybox.Position as Position;
using Toybox.Math as Math;
using Toybox.System as Sys;

//
// CourseLoader — loads checkpoint data from FIT course files stored on the
// device and computes distance-based par times for the scoring formula.
//
// Usage (from EnduroPlusModel.initialize()):
//
//   var loader = new CourseLoader();
//   if (loader.getCourseCount() > 0) {
//       var result = loader.loadCourse(0);  // load first course
//       _checkpoints = result[:checkpoints];
//       _parTimes    = result[:parTimes];
//   }
//
// The returned dictionary always has both keys even if the course is empty.
//

// Average enduro speed (km/h) used to convert inter-checkpoint distances to
// par times when the FIT course file carries no explicit segment time data.
const ENDURO_AVG_SPEED_KMH = 15.0;

// Minimum par time (seconds) per segment so scoring never degenerates.
const MIN_PAR_SEC = 10;

// Fallback par time (seconds) used when distance cannot be calculated.
const FALLBACK_PAR_SEC = 60;

class CourseLoader {

    // Returns the number of FIT courses available on the device, or 0 if the
    // Activity API is unavailable or no courses have been downloaded.
    function getCourseCount() {
        try {
            var courses = Activity.getCourses();
            return courses != null ? courses.size() : 0;
        } catch (e instanceof Lang.Exception) {
            Sys.println("CourseLoader: getCourseCount exception — " + e.getErrorMessage());
            return 0;
        }
    }

    // Loads the course at the given index.
    // Returns a dictionary { :checkpoints => Array<Checkpoint>, :parTimes => Array<Number> }.
    // Both arrays are empty when the course has no usable waypoints.
    function loadCourse(index) {
        var empty = { :checkpoints => [], :parTimes => [] };
        try {
            var courses = Activity.getCourses();
            if (courses == null || index >= courses.size()) {
                return empty;
            }
            var course = courses[index];
            var waypoints = course.waypoints;
            if (waypoints == null || waypoints.size() == 0) {
                Sys.println("CourseLoader: course[" + index + "] has no waypoints");
                return empty;
            }
            var checkpoints = _waypointsToCheckpoints(waypoints);
            if (checkpoints.size() == 0) {
                return empty;
            }
            var parTimes = _calcParTimes(checkpoints);
            Sys.println("CourseLoader: loaded " + checkpoints.size()
                        + " checkpoints from course[" + index + "]");
            return { :checkpoints => checkpoints, :parTimes => parTimes };
        } catch (e instanceof Lang.Exception) {
            Sys.println("CourseLoader: loadCourse exception — " + e.getErrorMessage());
            return empty;
        }
    }

    // Converts an array of Activity.Waypoint (or compatible) objects into an
    // array of Checkpoint objects.  Points without a valid position are skipped.
    function _waypointsToCheckpoints(waypoints) {
        var result = [];
        for (var i = 0; i < waypoints.size(); i++) {
            var wp = waypoints[i];
            if (wp == null || wp.position == null) {
                continue;
            }
            var deg = wp.position.toDegrees();
            var lat = deg[0];
            var lon = deg[1];
            // Reject points at (0, 0) — almost certainly invalid data.
            if (lat == 0.0 && lon == 0.0) {
                continue;
            }
            var name = (wp has :name && wp.name != null && wp.name.length() > 0)
                       ? wp.name
                       : ("CP" + (i + 1));
            result.add(new Checkpoint(lat, lon, name));
        }
        return result;
    }

    // Calculates par times (seconds) for each checkpoint.
    // parTimes[i] is the expected elapsed time to reach checkpoint i.
    //
    // Strategy:
    //   • parTimes[0] — travel from the assumed starting position to CP0.
    //     Because the start position is unknown at load time, a fixed
    //     grace period is used (FALLBACK_PAR_SEC).
    //   • parTimes[i > 0] — Haversine distance from CP(i-1) to CP(i)
    //     divided by ENDURO_AVG_SPEED_KMH, giving a realistic segment time.
    function _calcParTimes(checkpoints) {
        var times = new [checkpoints.size()];
        for (var i = 0; i < checkpoints.size(); i++) {
            if (i == 0) {
                times[0] = FALLBACK_PAR_SEC;
            } else {
                var prev = checkpoints[i - 1];
                var curr = checkpoints[i];
                var distM = _haversineDist(prev.lat, prev.lon, curr.lat, curr.lon);
                var speedMs = ENDURO_AVG_SPEED_KMH / 3.6;
                var t = (distM / speedMs).toNumber();
                times[i] = t > MIN_PAR_SEC ? t : MIN_PAR_SEC;
            }
        }
        return times;
    }

    // Haversine distance in metres between two WGS-84 coordinate pairs.
    function _haversineDist(lat1, lon1, lat2, lon2) {
        var R    = 6371000.0;
        var dLat = (lat2 - lat1) * Math.PI / 180.0;
        var dLon = (lon2 - lon1) * Math.PI / 180.0;
        var a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(lat1 * Math.PI / 180.0)
                 * Math.cos(lat2 * Math.PI / 180.0)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
