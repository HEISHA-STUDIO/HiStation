package com.wilson.histation;

import com.MAVLink.DLink.msg_manual_control;
import com.MAVLink.MAVLinkPacket;

import dji.common.error.DJIError;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.gimbal.Gimbal;

import static com.MAVLink.DLink.msg_manual_control.MAVLINK_MSG_ID_MANUAL_CONTROL;

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

    public void handleMavlinkPacket(MAVLinkPacket packet) {
        switch (packet.msgid) {
            case MAVLINK_MSG_ID_MANUAL_CONTROL:
                handleManualControl(packet);
                break;
        }
    }

    private void handleManualControl(MAVLinkPacket packet) {
        msg_manual_control msg = (msg_manual_control)packet.unpack();

        int op = 0;

        if(msg.x > 1000) {
            op = 0;
        } else if(msg.x > 500) {
            op = 1;
        } else if (msg.x < -500) {
            op = 2;
        } else if (msg.y > 500) {
            op = 3;
        } else if (msg.y < -500) {
            op = 4;
        }

        if(op > 0) {
            Gimbal gimbal = MApplication.getGimbalInstance();
            if(gimbal != null) {
                Rotation.Builder builder = new Rotation.Builder();
                Rotation rotation = builder.mode(RotationMode.SPEED).pitch(0).yaw(0).build();
                if(op == 1)
                    rotation = builder.mode(RotationMode.SPEED).pitch(10).yaw(0).build();
                else if(op == 2)
                    rotation = builder.mode(RotationMode.SPEED).pitch(-10).yaw(0).build();
                else if(op == 3)
                    rotation = builder.mode(RotationMode.SPEED).pitch(0).yaw(10).build();
                else if(op == 4)
                    rotation = builder.mode(RotationMode.SPEED).pitch(0).yaw(-10).build();
                gimbal.rotate(rotation, null);
            }
        }
    }
}
