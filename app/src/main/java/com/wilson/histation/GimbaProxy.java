package com.wilson.histation;

import dji.common.error.DJIError;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.gimbal.Gimbal;

class GimbaProxy {
    private static final GimbaProxy ourInstance = new GimbaProxy();

    static GimbaProxy getInstance() {
        return ourInstance;
    }

    private GimbaProxy() {

    }

    public void moving(float x, float y) {
        Gimbal gimbal = MApplication.getGimbalInstance();

        if(gimbal != null) {
            Rotation.Builder builder = new Rotation.Builder();
            Rotation rotation = builder.mode(RotationMode.SPEED).pitch(x).yaw(y).build();
            gimbal.rotate(rotation, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

}
