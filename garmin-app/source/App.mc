import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

class EnduroRaceApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Lang.Dictionary?) as Void {
    }

    function onStop(state as Lang.Dictionary?) as Void {
    }

    function getInitialView() as [ WatchUi.Views ] or [ WatchUi.Views, WatchUi.InputDelegates ] {
        return [ new HomeView(), new HomeDelegate() ];
    }
}

function getApp() as EnduroRaceApp {
    return Application.getApp() as EnduroRaceApp;
}
