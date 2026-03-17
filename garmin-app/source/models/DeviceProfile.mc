import Toybox.Lang;
import Toybox.System;

class DeviceProfile {

    var deviceId as Lang.String;
    var model as Lang.String;
    var screenWidth as Lang.Number;
    var screenHeight as Lang.Number;

    function initialize() {
        var devSettings = System.getDeviceSettings();
        deviceId = devSettings.uniqueIdentifier;
        model = devSettings.modelName;
        screenWidth = devSettings.screenWidth;
        screenHeight = devSettings.screenHeight;
    }
}
