package com.wilson.histation;

import dji.sdk.remotecontroller.RemoteController;

class RemoteControllerProxy {
    private static final RemoteControllerProxy ourInstance = new RemoteControllerProxy();

    static RemoteControllerProxy getInstance() {
        return ourInstance;
    }

    boolean isCallbackSetted = false;

    private RemoteControllerProxy() {

    }

    public void setBatteryStateCallback(RemoteController remoteController) {

        if(isCallbackSetted)
            return;
        isCallbackSetted = true;

        if(remoteController != null) {

        } else {
            HSCloudBridge.getInstance().sendDebug("NONE RC");
        }
    }

    public boolean isLive() {
        RemoteController remoteController = MApplication.getRemoteControllerInstance();

        if(remoteController == null)
            return false;

        if(remoteController.isConnected())
            return true;
        else
            return false;
    }
}
