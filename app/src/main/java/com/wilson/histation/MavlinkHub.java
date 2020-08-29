package com.wilson.histation;

import com.MAVLink.DLink.msg_heartbeat;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_MODE_FLAG;
import com.MAVLink.enums.MAV_TYPE;

class MavlinkHub {
    private static final MavlinkHub ourInstance = new MavlinkHub();

    static MavlinkHub getInstance() {
        return ourInstance;
    }

    private static int mavlinkIndex = 0;

    private Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                sendHeartbeat();
            }
        }
    };
    private Thread threadHB = null;

    private MavlinkHub() {
        if(threadHB == null) {
            threadHB = new Thread(heartbeat);
            threadHB.start();
        }
    }

    public void sendMavlinkPacket(MAVLinkPacket packet) {
        packet.seq = mavlinkIndex++;

        if(HSCloudBridge.getInstance().isConnected) {
            //MApplication.LOG(HSCloudBridge.getInstance().getTopic());
            HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic() + "-1", packet.encodePacket());
        }
    }

    private void sendHeartbeat() {
        msg_heartbeat msg = new msg_heartbeat();
        msg.custom_mode = 3;
        msg.type = MAV_TYPE.MAV_TYPE_QUADROTOR;
        msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA;
        msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED
                | MAV_MODE_FLAG.MAV_MODE_FLAG_STABILIZE_ENABLED
                | MAV_MODE_FLAG.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED
                | MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;

        msg.system_status = 0;
        msg.system_status += 10;
        msg.system_status += 1*16;

        sendMavlinkPacket(msg.pack());
    }
}
