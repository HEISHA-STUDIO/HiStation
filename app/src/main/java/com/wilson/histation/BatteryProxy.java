package com.wilson.histation;

import dji.common.battery.BatteryState;
import dji.sdk.battery.Battery;

class BatteryProxy {
    private static final BatteryProxy ourInstance = new BatteryProxy();

    static BatteryProxy getInstance() {
        return ourInstance;
    }

    private BatteryProxy() {
    }

    private BatteryState.Callback callback = new BatteryState.Callback() {
        @Override
        public void onUpdate(BatteryState batteryState) {
            //HSCloudBridge.getInstance().sendDebug("Battery: " + batteryState.getChargeRemainingInPercent());
        }
    };
    private boolean isCallbackSetted = false;

    public void setCallback(Battery battery) {
        //if(isCallbackSetted)
        //    return;
        //isCallbackSetted = true;

        if(battery != null) {
            battery.setStateCallback(callback);
        }
    }
}
