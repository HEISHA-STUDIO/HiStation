package com.wilson.histation;

import dji.common.remotecontroller.BatteryState;
import dji.sdk.remotecontroller.RemoteController;

class RemoteControllerProxy {
    private static final RemoteControllerProxy ourInstance = new RemoteControllerProxy();

    static RemoteControllerProxy getInstance() {
        return ourInstance;
    }

    private BatteryState.Callback callback = new BatteryState.Callback() {
        @Override
        public void onUpdate(BatteryState batteryState) {
            //HSCloudBridge.getInstance().sendDebug("RC Battery: " + batteryState.getRemainingChargeInPercent());
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
}
