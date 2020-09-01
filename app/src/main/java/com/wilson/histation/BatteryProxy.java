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
            MavlinkHub.getInstance().sendSystemStatus(batteryState);

            percent = batteryState.getChargeRemainingInPercent();

            if(bLowPower) {
                if(percent > 35) {
                    bLowPower = false;
                }
            } else {
                if(percent < 30) {
                    bLowPower = true;
                }
            }
        }
    };
    private boolean isCallbackSetted = false;
    private boolean bLowPower = true;
    private int percent = 0;

    public void setCallback(Battery battery) {
        //if(isCallbackSetted)
        //    return;
        //isCallbackSetted = true;

        if(battery != null) {
            battery.setStateCallback(callback);
        }
    }

    public boolean isLive() {
        Battery battery = MApplication.getBatteryInstance();

        if(battery == null)
            return false;

        if(battery.isConnected())
            return true;
        else
            return false;
    }

    public boolean isLowPower() {
        if(!isLive()) {
            return false;
        } else {
            return bLowPower;
        }
    }
}
