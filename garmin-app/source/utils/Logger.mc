import Toybox.Lang;
import Toybox.System;

class Logger {

    static function log(msg as Lang.String) as Void {
        System.println("[EnduroRace] " + msg);
    }
}
