package com.wilson.histation;

import dji.sdk.remotecontroller.RemoteController;
import dji.common.remotecontroller.ChargeRemaining;

class RemoteControllerProxy {
    private static final RemoteControllerProxy ourInstance = new RemoteControllerProxy();

    static RemoteControllerProxy getInstance() {
        return ourInstance;
    }

    private ChargeRemaining.Callback callback = new ChargeRemaining.Callback() {
        @Override
        public void onUpdate(ChargeRemaining chargeRemaining) {

        }
    };
    boolean isCallbackSetted = false;

    private RemoteControllerProxy() {

    }

    public void setBatteryStateCallback(RemoteController remoteController) {

        if(isCallbackSetted)
            return;
        isCallbackSetted = true;

        if(remoteController != null) {
            HSCloudBridge.getInstance().sendDebug("Set Callback");
            remoteController.setChargeRemainingCallback(callback);
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
