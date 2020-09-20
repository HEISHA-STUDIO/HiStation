package com.wilson.histation;

import com.MAVLink.DLink.msg_attitude;
import com.MAVLink.DLink.msg_command_ack;
import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_command_progress;
import com.MAVLink.DLink.msg_battery_batterystatus;
import com.MAVLink.DLink.msg_global_position_int;
import com.MAVLink.DLink.msg_gps_raw_int;
import com.MAVLink.DLink.msg_heartbeat;
import com.MAVLink.DLink.msg_radio_status;
import com.MAVLink.DLink.msg_statustext;
import com.MAVLink.DLink.msg_sys_status;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Parser;
import com.MAVLink.enums.GPS_FIX_TYPE;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_MODE_FLAG;
import com.MAVLink.enums.MAV_SEVERITY;
import com.MAVLink.enums.MAV_TYPE;

import java.util.ArrayList;

import dji.common.battery.BatteryState;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;

import static com.MAVLink.DLink.msg_command_int.MAVLINK_MSG_ID_COMMAND_INT;
import static com.MAVLink.DLink.msg_manual_control.MAVLINK_MSG_ID_MANUAL_CONTROL;
import static com.MAVLink.DLink.msg_mediafile_request.MAVLINK_MSG_ID_MEDIAFILE_REQUEST;
import static com.MAVLink.DLink.msg_mediafile_request_list.MAVLINK_MSG_ID_MEDIAFILE_REQUEST_LIST;
import static com.MAVLink.DLink.msg_mission_count.MAVLINK_MSG_ID_MISSION_COUNT;
import static com.MAVLink.DLink.msg_mission_item.MAVLINK_MSG_ID_MISSION_ITEM;
import static com.MAVLink.DLink.msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST;
import static com.MAVLink.DLink.msg_mission_request_list.MAVLINK_MSG_ID_MISSION_REQUEST_LIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_PAUSE_CONTINUE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_FLIGHT_PREPARE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_ONE_KEY_TO_CHARGE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_LOCK;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_REQUEST_STATUS;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_TURN_ON_RC;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_PAD_UNLOCK;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_CAMERA_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_STORAGE_LOCATION;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STREAMING_REQUEST;

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
                //HSCloudBridge.getInstance().sendDebug("Memory: " +  ((int) Runtime.getRuntime().maxMemory())/1024/1024);

                try {
                    sendHeartbeat();
                    //sendBatteryStatus();
                    //testSystemStatus();
                    //testGlobalPosition();
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

    public void mavlink_message_handle(MAVLinkPacket packet) {
        //MApplication.LOG("MSG: " + packet.msgid);
        //HSCloudBridge.getInstance().sendDebug("MSG: " + packet.msgid);
        switch (packet.msgid) {
            case MAVLINK_MSG_ID_MISSION_REQUEST_LIST:
            case MAVLINK_MSG_ID_MISSION_COUNT:
            case MAVLINK_MSG_ID_MISSION_ITEM:
            case MAVLINK_MSG_ID_MISSION_REQUEST:
                MissionPlanner.getInstance().handleMavlinkMessage(packet);
                break;
            case MAVLINK_MSG_ID_MEDIAFILE_REQUEST_LIST:
            case MAVLINK_MSG_ID_MEDIAFILE_REQUEST:
                MediaFileManager.getInstance().handleMavlinkMessage(packet);
                break;
            case MAVLINK_MSG_ID_COMMAND_INT:
                commandDistribute(packet);
                break;
            case MAVLINK_MSG_ID_MANUAL_CONTROL:
                GimbaProxy.getInstance().handleMavlinkPacket(packet);
                break;
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

        if(RemoteControllerProxy.getInstance().isLive())
            msg.system_status += 10;
        if(FlightControllerProxy.getInstance().isLive())
            msg.system_status += 1*16;

        if(FlightControllerProxy.getInstance().isFlying()) {
            msg.system_status += 4*16;
            msg.system_status += 2*16;

            if(FlightControllerProxy.getInstance().isPaused()) {
                msg.system_status += 8*16;
            }
        } else {
            if(MissionPlanner.getInstance().preflightCheck() == 0) {
                msg.system_status += 2*16;
            }
        }

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

    private void testGlobalPosition() {
        msg_global_position_int msg = new msg_global_position_int();

        msg.alt = (int)(20.0f * 1000);
        msg.hdg = 0;
        msg.lat = (int)(23.567f * 10000000);
        msg.lon = (int)(118.976 * 10000000);
        msg.relative_alt = (int)(30.0f * 1000);
        msg.distance_to_home = 12500;
        msg.vx = (short)(0.0 * 100);
        msg.vy = (short)(0.0 * 100);
        msg.vz = (short)(0.0 * 100);
        msg.time_boot_ms = (long)(0);

        sendMavlinkPacket(msg.pack());
    }

    public void sendGlobalPosition(FlightControllerState flightControllerState) {
        if(flightControllerState == null)
            return;

        LocationCoordinate3D locationCoordinate3D = flightControllerState.getAircraftLocation();

        if( locationCoordinate3D == null)
            return;

        //float takeoffAltitude = flightControllerState.getTakeoffLocationAltitude();
        //HSCloudBridge.getInstance().sendDebug("TAKEOFF: " + takeoffAltitude);
        float velX = flightControllerState.getVelocityX();
        float velY = flightControllerState.getVelocityY();
        float velZ = flightControllerState.getVelocityZ();
        float height = 0;
        if(MissionPlanner.getInstance().homeLocation != null)
            height = locationCoordinate3D.getAltitude() - MissionPlanner.getInstance().homeLocation.getAltitude();

        int hdg = (int)(flightControllerState.getAttitude().yaw*100);
        if(hdg < 0)
            hdg += 36000;

        msg_global_position_int msg = new msg_global_position_int();

        if(msg == null) {
            //MQTTService.MQTTLog("NULL POINT");
            return;
        }

        msg.alt = (int)(locationCoordinate3D.getAltitude() * 1000);
        msg.hdg = hdg;
        msg.lat = (int)(locationCoordinate3D.getLatitude() * 10000000);
        msg.lon = (int)(locationCoordinate3D.getLongitude() * 10000000);
        msg.relative_alt = (int)(height * 1000);
        msg.distance_to_home = 12500;
        msg.vx = (short)(velX * 100);
        msg.vy = (short)(velY * 100);
        msg.vz = (short)(velZ * 100);
        msg.time_boot_ms = (long)(0);

        sendMavlinkPacket(msg.pack());
        //HSCloudBridge.getInstance().sendDebug(msg.toString());
    }

    public void sendGPSStatus(FlightControllerState flightControllerState) {
        //MQTTService.getInstance().publishTopic("LOG", ("IN").getBytes());
        LocationCoordinate3D locationCoordinate3D = flightControllerState.getAircraftLocation();
        int gpsCount = flightControllerState.getSatelliteCount();
        GPSSignalLevel gpsSignalLevel = flightControllerState.getGPSSignalLevel();

        msg_gps_raw_int message = new msg_gps_raw_int();
        message.lat = (int)(locationCoordinate3D.getLatitude() * 10000000);
        message.lon = (int)(locationCoordinate3D.getLongitude() * 10000000);
        message.alt = (int)(locationCoordinate3D.getAltitude() * 1000);
        message.satellites_visible = (short)gpsCount;
        message.cog = 0;
        message.eph = 0;
        message.epv = 0;
        message.time_usec = 0;

        switch (gpsSignalLevel) {
            case NONE:
                message.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_GPS;
                break;
            case LEVEL_0:
            case LEVEL_1:
                message.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_FIX;
                break;
            case LEVEL_2:
            case LEVEL_3:
                message.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_2D_FIX;
                break;
            case LEVEL_4:
            case LEVEL_5:
                message.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_3D_FIX;
                break;
            default:
                message.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_GPS;
                break;
        }

        sendMavlinkPacket(message.pack());
    }

    public void sendAttitude(FlightControllerState flightControllerState) {
        Attitude attitude = flightControllerState.getAttitude();

        float roll = (float)(attitude.roll*Math.PI/180);
        float pitch = (float)(attitude.pitch*Math.PI/180);
        float yaw = (float)(attitude.yaw*Math.PI/180);

        msg_attitude msg = new msg_attitude();

        msg.time_boot_ms = 0;
        msg.roll = roll;
        msg.pitch = pitch;
        msg.yaw = yaw;
        msg.rollspeed = 0;
        msg.pitchspeed = 0;
        msg.yawspeed = 0;

        sendMavlinkPacket(msg.pack());
    }

    private void testSystemStatus() {
        msg_sys_status msg = new msg_sys_status();

            msg.current_battery = (short)0;
            msg.voltage_battery = (short)1500;
            msg.battery_remaining = (byte)70;

        sendMavlinkPacket(msg.pack());
    }

    public void sendSystemStatus(BatteryState batteryState) {
        msg_sys_status msg = new msg_sys_status();

        if(batteryState != null) {
            msg.current_battery = (short)batteryState.getCurrent();
            msg.voltage_battery = (short)batteryState.getVoltage();
            msg.battery_remaining = (byte)batteryState.getChargeRemainingInPercent();
            //MQTTService.getInstance().publishTopic(MQTTService.TOPIC_LOG, ("Percent: " + msg.battery_remaining).getBytes());
        }

        sendMavlinkPacket(msg.pack());
    }

    public void sendText(String text) {
        msg_statustext msg = new msg_statustext();

        msg.setText(text);
        msg.severity = MAV_SEVERITY.MAV_SEVERITY_INFO;

        sendMavlinkPacket(msg.pack());
    }

    private void commandDistribute(MAVLinkPacket packet) {
        msg_command_int msg = (msg_command_int)packet.unpack();

        switch (msg.command) {
            case MAV_CMD_PAD_REQUEST_STATUS:
            case MAV_CMD_PAD_LOCK:
            case MAV_CMD_PAD_UNLOCK:
            case MAV_CMD_PAD_TURN_ON_RC:
            case MAV_CMD_PAD_TURN_OFF_RC:
            case MAV_CMD_PAD_TURN_ON_DRONE:
            case MAV_CMD_PAD_TURN_OFF_DRONE:
            case MAV_CMD_PAD_CANOPY_OPEN:
            case MAV_CMD_PAD_CANOPY_CLOSE:
            case MAV_CMD_PAD_TURN_ON_CHARGE:
            case MAV_CMD_PAD_TURN_OFF_CHARGE:
                ChargePad.getInstance().handCommand(msg);
                break;
            case MAV_CMD_FLIGHT_PREPARE:
            case MAV_CMD_ONE_KEY_TO_CHARGE:
            case MAV_CMD_DO_PAUSE_CONTINUE:
            case MAV_CMD_NAV_TAKEOFF:
                MissionPlanner.getInstance().handleCommand(msg);
                break;
            case MAV_CMD_SET_STORAGE_LOCATION:
            case MAV_CMD.MAV_CMD_REQUEST_STORAGE_FORMAT:
                MediaFileManager.getInstance().handleCommand(msg);
                break;
            case MAV_CMD_SET_CAMERA_MODE:
            case MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE:
            case MAV_CMD_VIDEO_START_CAPTURE:
            case MAV_CMD_VIDEO_STOP_CAPTURE:
            case MAV_CMD_SET_CAMERA_ZOOM:
                CameraProxy.getInstance().handleCommand(msg);
                break;
            case MAV_CMD_VIDEO_STREAMING_REQUEST:
                HSVideoFeeder.getInstance().handleCommand(msg);
                break;
        }
    }

    public void sendCommandAck(int command, short result) {
        msg_command_ack msg = new msg_command_ack();
        msg.command = command;
        msg.result = result;

        sendMavlinkPacket(msg.pack());
        HSCloudBridge.getInstance().sendDebug(msg.toString());
    }

    public void sendRssi() {
        msg_radio_status msg = new msg_radio_status();
        msg.rssi = 180;

        sendMavlinkPacket(msg.pack());
    }

    public void sendCommandProgress(int command, short total, short step) {
        msg_command_progress msg = new msg_command_progress();
        msg.command = (short)command;
        msg.step_total = total;
        msg.step_complete = step;

        sendMavlinkPacket(msg.pack());
    }

    public void sendBatteryStatus() {
        msg_battery_batterystatus msg = new msg_battery_batterystatus();
        msg.time_total = 1800;
        msg.time_remaining = 800;

        sendMavlinkPacket(msg.pack());
    }
}
