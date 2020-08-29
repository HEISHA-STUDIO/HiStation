package com.wilson.histation;

import com.MAVLink.DLink.msg_heartbeat;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Parser;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_MODE_FLAG;
import com.MAVLink.enums.MAV_TYPE;

import java.util.ArrayList;

class MavlinkHub {
    private static final MavlinkHub ourInstance = new MavlinkHub();

    static MavlinkHub getInstance() {
        return ourInstance;
    }

    private static int mavlinkIndex = 0;
    private MAVLinkPacket mavLinkPacket;
    private Parser mavlinkParser = new Parser();

    ArrayList<byte[]> receiveBuf = new ArrayList<byte[]>();

    private Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //MApplication.LOG("Heartbeat Thread");

                try {
                    sendHeartbeat();
                } catch (Exception e) {
                    e.printStackTrace();
                    MApplication.LOG(e.getMessage());
                }

            }
        }
    };
    private Thread threadHB = null;

    private Runnable decoder = new Runnable() {
        @Override
        public void run() {
            while (true) {
                boolean empty = false;
                byte[] encData = null;

                synchronized (receiveBuf) {
                    if(receiveBuf.size() == 0) {
                        empty = true;
                    } else {
                        encData = receiveBuf.remove(0);
                    }
                }

                if(empty) {
                    try{
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                for(int i=0; i<encData.length; i++) {
                    int ch = 0xFF & encData[i];

                    if ((mavLinkPacket = mavlinkParser.mavlink_parse_char(ch)) != null) {
                        mavlink_message_handle(mavLinkPacket);
                    }
                }
            }
        }
    };
    private Thread threadDecode = null;

    HSCloudBridge.MavLinkListener mavLinkListener = new HSCloudBridge.MavLinkListener() {
        @Override
        public void onMessage(byte[] data) {
            synchronized (receiveBuf) {
                receiveBuf.add(data);
            }

        }
    };

    private MavlinkHub() {
        if(threadHB == null) {
            threadHB = new Thread(heartbeat);
            threadHB.start();
        }

        if(threadDecode == null) {
            threadDecode = new Thread(decoder);
            threadDecode.start();
        }
    }

    public void sendMavlinkPacket(MAVLinkPacket packet) {
        packet.seq = mavlinkIndex++;

        if(HSCloudBridge.getInstance().isConnected) {
            //MApplication.LOG(HSCloudBridge.getInstance().getTopic());
            HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic() + "-1", packet.encodePacket());
        }
    }

    private void mavlink_message_handle(MAVLinkPacket packet) {
        //MApplication.LOG("MSG: " + packet.msgid);
        //HSCloudBridge.getInstance().sendDebug("MSG: " + packet.msgid);
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

    public void wakeup() {
        if(threadHB != null) {
            HSCloudBridge.getInstance().sendDebug("HB Thread: " + threadHB.isAlive());
            HSCloudBridge.getInstance().sendDebug("HB Thread: " + threadHB.isInterrupted());
            if(!threadHB.isAlive())
                threadHB.start();
        } else {
            threadHB = new Thread(heartbeat);
            threadHB.start();
        }
    }
}
