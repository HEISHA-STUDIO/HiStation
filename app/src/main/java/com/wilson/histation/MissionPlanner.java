package com.wilson.histation;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_mission_ack;
import com.MAVLink.DLink.msg_mission_count;
import com.MAVLink.DLink.msg_mission_item;
import com.MAVLink.DLink.msg_mission_request;
import com.MAVLink.DLink.msg_rc_channels_override;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.enums.MAV_MODE;
import com.MAVLink.enums.MAV_RESULT;
import com.alibaba.fastjson.JSON;


import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.ArrayList;
import java.util.List;

import dji.common.error.DJIError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.sdkmanager.DJISDKManager;

import static android.content.Context.MODE_PRIVATE;
import static com.MAVLink.DLink.msg_mission_count.MAVLINK_MSG_ID_MISSION_COUNT;
import static com.MAVLink.DLink.msg_mission_item.MAVLINK_MSG_ID_MISSION_ITEM;
import static com.MAVLink.DLink.msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST;
import static com.MAVLink.DLink.msg_mission_request_list.MAVLINK_MSG_ID_MISSION_REQUEST_LIST;
import static com.MAVLink.DLink.msg_rc_channels_override.MAVLINK_MSG_ID_RC_CHANNELS_OVERRIDE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_PAUSE_CONTINUE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_FLIGHT_PREPARE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_ONE_KEY_TO_CHARGE;
import static com.MAVLink.enums.MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;

class MissionPlanner {
    private static final MissionPlanner ourInstance = new MissionPlanner();

    static MissionPlanner getInstance() {
        return ourInstance;
    }

    private MissionPlanner() {
        addListener();
        loadSavedMission();
    }

    protected void finalize( )
    {
        removeListener();
    }

    private List<Location>  savedWaypoints;
    private int waypointCount = 0;
    private int waypointReceived = 0;

    private float mSpeed = 10.0f;
    private List<Waypoint> waypointList = new ArrayList<>();
    public static WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder();
    private WaypointMissionOperator waypointMissionOperator;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    public LocationCoordinate3D homeLocation = null;

    boolean mannulControl = false;
    boolean isMannulControl = false;

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {
            HSCloudBridge.getInstance().sendDebug("downloadEvent");
        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {
            HSCloudBridge.getInstance().sendDebug("uploadEvent");
        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
            HSCloudBridge.getInstance().sendDebug("executionEvent");
        }

        @Override
        public void onExecutionStart() {
            HSCloudBridge.getInstance().sendDebug("onExecutionStart");
        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            HSCloudBridge.getInstance().sendDebug("onExecutionFinish");
        }
    };

    private void loadSavedMission() {
        SharedPreferences read = MApplication.app.getSharedPreferences("histation", MODE_PRIVATE);
        String missionStr = read.getString("mission", "");

        //Log.i(MApplication.LOG_TAG, "M: " + missionStr);
        MApplication.LOG("Waypoints: " + missionStr);
        savedWaypoints = JSON.parseArray(missionStr, Location.class);
        if(savedWaypoints == null) {
            savedWaypoints = new ArrayList<>();
        }
    }

    private void saveCurrentMission() {
        if(savedWaypoints.size() > 1) {
            savedWaypoints.get(0).setLatitude(savedWaypoints.get(1).getLatitude());
            savedWaypoints.get(0).setLongitude(savedWaypoints.get(1).getLongitude());
            savedWaypoints.get(0).setAltitude(savedWaypoints.get(1).getAltitude());
        }

        SharedPreferences.Editor edit = MApplication.app.getSharedPreferences("histation", MODE_PRIVATE).edit();
        String missionStr = JSON.toJSONString(savedWaypoints);

        edit.putString("mission", missionStr).commit();
    }

    public void handleMavlinkMessage(MAVLinkPacket packet) {
        MApplication.LOG("Mission msg in: " + packet.msgid);
        switch (packet.msgid) {
            case MAVLINK_MSG_ID_MISSION_REQUEST_LIST:
                sendWayPointCount(packet);
                HSCloudBridge.getInstance().sendDebug("Request list");
                break;
            case MAVLINK_MSG_ID_MISSION_COUNT:
                startWayPointReceive(packet);
                break;
            case MAVLINK_MSG_ID_MISSION_ITEM:
                receiveMissionItem(packet);
                break;
            case MAVLINK_MSG_ID_MISSION_REQUEST:
                sendWayPoint(packet);
                break;
            case MAVLINK_MSG_ID_RC_CHANNELS_OVERRIDE:
                handleRCOverride(packet);
                break;
        }
    }

    private void startWayPointReceive(MAVLinkPacket packet) {
        msg_mission_count msg = (msg_mission_count)packet.unpack();
        HSCloudBridge.getInstance().sendDebug(msg.toString());
        waypointCount = msg.count;
        waypointReceived = 0;

        savedWaypoints.clear();
        requestMavLinkNextWaypoint();

        MApplication.LOG("Start receive: " + waypointCount);
    }

    private void requestMavLinkNextWaypoint() {
        MAVLinkPacket packet;
        if(waypointReceived == waypointCount) {
            saveCurrentMission();
            msg_mission_ack msg = new msg_mission_ack();
            msg.type = 0;
            packet = msg.pack();
            HSCloudBridge.getInstance().sendDebug(msg.toString());
        } else {
            msg_mission_request msg = new msg_mission_request();
            msg.seq = waypointReceived;
            packet = msg.pack();
            HSCloudBridge.getInstance().sendDebug(msg.toString());
        }

        MavlinkHub.getInstance().sendMavlinkPacket(packet);
    }

    private void receiveMissionItem(MAVLinkPacket packet) {
        msg_mission_item msg = (msg_mission_item)packet.unpack();
        HSCloudBridge.getInstance().sendDebug(msg.toString());
        if(msg.command == 16) {
            Location waypoint = new Location(msg.x, msg.y, msg.z);
            if(waypointReceived == msg.seq) {
                savedWaypoints.add(waypoint);
                waypointReceived = msg.seq + 1;
                MApplication.LOG("Received: " + waypointReceived);
                requestMavLinkNextWaypoint();
            }
        }
    }

    private void sendWayPointCount(MAVLinkPacket packet) {
        msg_mission_count msg = new msg_mission_count();
        msg.count = savedWaypoints.size();

        MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
        HSCloudBridge.getInstance().sendDebug(msg.toString());
    }

    private void sendWayPoint(MAVLinkPacket packet) {
        msg_mission_request msg = (msg_mission_request)packet.unpack();
        if(msg.seq < savedWaypoints.size()) {
            msg_mission_item item = new msg_mission_item();
            item.command = 16;
            item.frame = MAV_FRAME_GLOBAL_RELATIVE_ALT;
            item.seq = msg.seq;
            item.x = savedWaypoints.get(msg.seq).getLatitude();
            item.y = savedWaypoints.get(msg.seq).getLongitude();
            item.z = savedWaypoints.get(msg.seq).getAltitude();

            MApplication.LOG("Item " +msg.seq + ": " + item.x + " " + item.y + " " + item.z);

            MavlinkHub.getInstance().sendMavlinkPacket(item.pack());
        }
    }

    private int configureWaypoints() {
        if(savedWaypoints.size() < 2) {
            return 1;
        }

        waypointList.clear();
        for(int i = 1; i<savedWaypoints.size(); i++) {
            Location location = savedWaypoints.get(i);
            Waypoint waypoint = new Waypoint(location.getLatitude(), location.getLongitude(), location.getAltitude());
            waypointList.add(waypoint);
        }

        if(waypointMissionBuilder == null) {
            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .waypointList(waypointList).waypointCount(waypointList.size());
        } else {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .waypointList(waypointList).waypointCount(waypointList.size());
        }

        if (waypointMissionOperator == null) {
            waypointMissionOperator = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }

        if(waypointMissionOperator == null) {
            return 3;
        }

        DJIError error = waypointMissionOperator.loadMission(waypointMissionBuilder.build());
        if(error != null) {
            return 4;
        }

        return 0;
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (waypointMissionOperator == null) {
            MissionControl missionControl = DJISDKManager.getInstance().getMissionControl();
            if(missionControl != null)
                waypointMissionOperator = missionControl.getWaypointMissionOperator();
        }
        return waypointMissionOperator;
    }

    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    public void startMission() {
        if(configureWaypoints() > 0) {
            HSCloudBridge.getInstance().sendDebug("fail to configure the mission");
        } else {
            waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError == null) {
                        HSCloudBridge.getInstance().sendDebug("upload mission success");
                        waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if(djiError == null) {
                                    HSCloudBridge.getInstance().sendDebug("start mission success");
                                } else {
                                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                                }
                            }
                        });
                    } else {
                        HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                    }
                }
            });
        }
    }

    public void handleCommand(msg_command_int command ) {
        switch (command.command) {
            case MAV_CMD_FLIGHT_PREPARE:
                handleFlightPrepare();
                break;
            case MAV_CMD_ONE_KEY_TO_CHARGE:
                handleCharge();
                break;
            case MAV_CMD_NAV_TAKEOFF:
                if(isMannulControl)
                    mannulTakeoff();
                else
                    handleTakeoff();
                break;
            case MAV_CMD_DO_PAUSE_CONTINUE:
                handlePause();
                break;
            case MAV_CMD_NAV_RETURN_TO_LAUNCH:
                handleRTL();
                break;
            case MAV_CMD_DO_SET_MODE:
                handleSetMode(command);
                break;
        }
    }

    private void handleSetMode(msg_command_int command) {
        FlightController flightController = MApplication.getFlightControllerInstance();

        if(flightController == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_SET_MODE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!flightController.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_SET_MODE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_SET_MODE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        flightController.setRollPitchControlMode(RollPitchControlMode.ANGLE);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        mannulControl = false;
        if(command.param1 == MAV_MODE.MAV_MODE_MANUAL)
            mannulControl = true;

        flightController.setVirtualStickModeEnabled(mannulControl, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_SET_MODE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    isMannulControl = mannulControl;
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_SET_MODE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                }
            }
        });
    }

    private void handleFlightPrepare() {
        if(MApplication.commandBusy) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("System busy");
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        // Lock bar
        ChargePad.getInstance().handPadLock(new CommandResultListener() {
            @Override
            public void accepted() {
                MavlinkHub.getInstance().sendText("Try to lock the position bars");
            }

            @Override
            public void rejected(String reason) {
                MavlinkHub.getInstance().sendText("Fail to lock the position bars: " + reason);
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
            }

            @Override
            public void completed() {
                MavlinkHub.getInstance().sendText("Position bars locked");
                MavlinkHub.getInstance().sendCommandProgress(MAV_CMD_FLIGHT_PREPARE, (short)5, (short)1);
                MApplication.commandBusy = false;

                // Turn on rc
                ChargePad.getInstance().handTurnOnRC(new CommandResultListener() {
                    @Override
                    public void accepted() {
                        MavlinkHub.getInstance().sendText("Try to turn on the remote");
                    }

                    @Override
                    public void rejected(String reason) {
                        MavlinkHub.getInstance().sendText("Fail to turn on the remote: " + reason);
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }

                    @Override
                    public void completed() {
                        MavlinkHub.getInstance().sendText("Remote on");
                        MavlinkHub.getInstance().sendCommandProgress(MAV_CMD_FLIGHT_PREPARE, (short)5, (short)2);
                        MApplication.commandBusy = false;
                        // turn the drone
                        ChargePad.getInstance().handTurnOnDrone(new CommandResultListener() {
                            @Override
                            public void accepted() {
                                MavlinkHub.getInstance().sendText("Try to turn on the drone");
                            }

                            @Override
                            public void rejected(String reason) {
                                MavlinkHub.getInstance().sendText("Fail to turn on the drone: " + reason);
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            }

                            @Override
                            public void completed() {
                                MavlinkHub.getInstance().sendText("Drone on");
                                MavlinkHub.getInstance().sendCommandProgress(MAV_CMD_FLIGHT_PREPARE, (short)5, (short)3);
                                MApplication.commandBusy = false;
                                // turn off charge
                                ChargePad.getInstance().handTurnOffCharge(new CommandResultListener() {
                                    @Override
                                    public void accepted() {
                                        MavlinkHub.getInstance().sendText("Try to turn off charge");
                                    }

                                    @Override
                                    public void rejected(String reason) {
                                        MavlinkHub.getInstance().sendText("Fail to turn off charge: " + reason);
                                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                    }

                                    @Override
                                    public void completed() {
                                        MavlinkHub.getInstance().sendText("Charge off");
                                        MavlinkHub.getInstance().sendCommandProgress(MAV_CMD_FLIGHT_PREPARE, (short)5, (short)4);
                                        MApplication.commandBusy = false;

                                        ChargePad.getInstance().handOpenCanapy(new CommandResultListener() {
                                            @Override
                                            public void accepted() {
                                                MavlinkHub.getInstance().sendText("Try to open the canopy");
                                            }

                                            @Override
                                            public void rejected(String reason) {
                                                MavlinkHub.getInstance().sendText("Fail to open the canopy");
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                            }

                                            @Override
                                            public void completed() {
                                                MavlinkHub.getInstance().sendText("Preflight prepare complete");
                                                MavlinkHub.getInstance().sendCommandProgress(MAV_CMD_FLIGHT_PREPARE, (short)5, (short)5);
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                                            }

                                            @Override
                                            public void failed(String reason) {
                                                MavlinkHub.getInstance().sendText("Fail to open the canopy");
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                            }
                                        });
                                    }

                                    @Override
                                    public void failed(String reason) {
                                        MavlinkHub.getInstance().sendText("Fail to turn off charge: " + reason);
                                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                    }
                                });
                            }

                            @Override
                            public void failed(String reason) {
                                MavlinkHub.getInstance().sendText("Fail to turn on the drone: " + reason);
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            }
                        });
                    }

                    @Override
                    public void failed(String reason) {
                        MavlinkHub.getInstance().sendText("Fail to turn on the remote: " + reason);
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }
                });
            }

            @Override
            public void failed(String reason) {
                MavlinkHub.getInstance().sendText("Fail to lock the position bars: " + reason);
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_FLIGHT_PREPARE, (short)MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    private void handleCharge() {
        if(MApplication.commandBusy) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("System busy");
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        ChargePad.getInstance().handPadLock(new CommandResultListener() {
            @Override
            public void accepted() {
                MavlinkHub.getInstance().sendText("Try to lock position bars");
            }

            @Override
            public void rejected(String reason) {
                MavlinkHub.getInstance().sendText("Fail to lock position bars: " + reason);
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
            }

            @Override
            public void completed() {
                MavlinkHub.getInstance().sendText("Position bars locked");
                MApplication.commandBusy = false;

                ChargePad.getInstance().handTurnOffDrone(new CommandResultListener() {
                    @Override
                    public void accepted() {
                        MavlinkHub.getInstance().sendText("Try to turn off the drone");
                    }

                    @Override
                    public void rejected(String reason) {
                        MavlinkHub.getInstance().sendText("Fail to turn off the drone: " + reason);
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }

                    @Override
                    public void completed() {
                        MavlinkHub.getInstance().sendText("Drone off");
                        MApplication.commandBusy = false;

                        ChargePad.getInstance().handTurnOnCharge(new CommandResultListener() {
                            @Override
                            public void accepted() {
                                MavlinkHub.getInstance().sendText("Try to start charge");
                            }

                            @Override
                            public void rejected(String reason) {
                                MavlinkHub.getInstance().sendText("Fail to start charge: " + reason);
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            }

                            @Override
                            public void completed() {
                                MavlinkHub.getInstance().sendText("Charge start");
                                MApplication.commandBusy = false;

                                ChargePad.getInstance().handTurnOffRC(new CommandResultListener() {
                                    @Override
                                    public void accepted() {
                                        MavlinkHub.getInstance().sendText("Try to turn off the remote");
                                    }

                                    @Override
                                    public void rejected(String reason) {
                                        MavlinkHub.getInstance().sendText("Fail to turn off the remote: " + reason);
                                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                    }

                                    @Override
                                    public void completed() {
                                        MavlinkHub.getInstance().sendText("Remote off");
                                        MApplication.commandBusy = false;

                                        ChargePad.getInstance().handCloseCanapy(new CommandResultListener() {
                                            @Override
                                            public void accepted() {
                                                MavlinkHub.getInstance().sendText("Try to close the canopy");
                                            }

                                            @Override
                                            public void rejected(String reason) {
                                                MavlinkHub.getInstance().sendText("Fail to close the canopy: " + reason);
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                            }

                                            @Override
                                            public void completed() {
                                                MavlinkHub.getInstance().sendText("Wait for charge complete");
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                                            }

                                            @Override
                                            public void failed(String reason) {
                                                MavlinkHub.getInstance().sendText("Fail to close the canopy: " + reason);
                                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                            }
                                        });
                                    }

                                    @Override
                                    public void failed(String reason) {
                                        MavlinkHub.getInstance().sendText("Fail to turn off the remote: " + reason);
                                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                    }
                                });
                            }

                            @Override
                            public void failed(String reason) {
                                MavlinkHub.getInstance().sendText("Fail to start charge: " + reason);
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            }
                        });
                    }

                    @Override
                    public void failed(String reason) {
                        MavlinkHub.getInstance().sendText("Fail to turn off the drone: " + reason);
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }
                });
            }

            @Override
            public void failed(String reason) {
                MavlinkHub.getInstance().sendText("Fail to lock position bars: " + reason);
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_ONE_KEY_TO_CHARGE, (short)MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    private void mannulTakeoff() {
        int rst = preflightCheckMannul();
        if(rst > 0) {
            MavlinkHub.getInstance().sendText("flight check failed code: " + rst);
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!ChargePad.getInstance().isLive()) {
            MavlinkHub.getInstance().sendText("Charge pad not detected");
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(ChargePad.getInstance().getBarStatus() != ChargePad.BAR_STATUS_LOCKED) {
            MavlinkHub.getInstance().sendText("drone not at center");
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        homeLocation = MApplication.getFlightControllerInstance().getState().getAircraftLocation();

        ChargePad.getInstance().handPadUnLock(new CommandResultListener() {
            @Override
            public void accepted() {
                MavlinkHub.getInstance().sendText("trying to unlock the drone");
            }

            @Override
            public void rejected(String reason) {

            }

            @Override
            public void completed() {
                MavlinkHub.getInstance().sendText("Trying to takeoff");
                MApplication.getFlightControllerInstance().startTakeoff(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if(djiError == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            MavlinkHub.getInstance().sendText("Takeoff failed");
                        }
                    }
                });
            }

            @Override
            public void failed(String reason) {
                MavlinkHub.getInstance().sendText("Fail to unlock the drone");
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    private void handleTakeoff() {
        int rst = preflightCheck();
        if(rst > 0) {
            MavlinkHub.getInstance().sendText("flight check failed code: " + rst);
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!ChargePad.getInstance().isLive()) {
            MavlinkHub.getInstance().sendText("Charge pad not detected");
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(ChargePad.getInstance().getBarStatus() != ChargePad.BAR_STATUS_LOCKED) {
            MavlinkHub.getInstance().sendText("drone not at center");
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        homeLocation = MApplication.getFlightControllerInstance().getState().getAircraftLocation();

        MavlinkHub.getInstance().sendText("trying to configure the mission");
        if(configureWaypoints() > 0) {
            MavlinkHub.getInstance().sendText("fail to configure the mission");
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
        } else {
            MavlinkHub.getInstance().sendText("trying to upload Mission");
            waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError != null) {
                        MavlinkHub.getInstance().sendText(djiError.getDescription());
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    } else {
                        ChargePad.getInstance().handPadUnLock(new CommandResultListener() {
                            @Override
                            public void accepted() {
                                MavlinkHub.getInstance().sendText("trying to unlock the drone");
                            }

                            @Override
                            public void rejected(String reason) {

                            }

                            @Override
                            public void completed() {
                                MavlinkHub.getInstance().sendText("trying to start the mission");
                                waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if(djiError != null) {
                                            MavlinkHub.getInstance().sendText("fail: " + djiError.getDescription());
                                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
                                        } else {
                                            //HSVideoFeeder.getInstance().videoSource = HSVideoFeeder.VIDEO_SOURCE_DRONE;
                                            MavlinkHub.getInstance().sendText("mission started");
                                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void failed(String reason) {
                                MavlinkHub.getInstance().sendText("Fail to unlock the drone");
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_TAKEOFF, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            }
                        });
                    }
                }
            });
        }
    }

    public void handleCancelMission() {
        waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError != null) {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }

    public void handleReStartMission() {
        waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError != null) {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }

    public void testStartMission() {
        if(configureWaypoints() > 0) {
            MavlinkHub.getInstance().sendText("fail to configure the mission");
        } else {
            waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError != null) {
                        HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                    } else {
                        waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if(djiError != null) {
                                    MavlinkHub.getInstance().sendText("fail: " + djiError.getDescription());
                                } else {
                                    //HSVideoFeeder.getInstance().videoSource = HSVideoFeeder.VIDEO_SOURCE_DRONE;
                                    MavlinkHub.getInstance().sendText("mission started");
                                }
                            }
                        });
                    }
                }
            });

        }
    }

    private void handlePause() {
        if(isMannulControl) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!FlightControllerProxy.getInstance().isFlying()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(MApplication.commandBusy == true) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }
        MApplication.commandBusy = true;

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        if(FlightControllerProxy.getInstance().isPaused()) {
            getWaypointMissionOperator().resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError == null) {
                        FlightControllerProxy.getInstance().isPaused = false;
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    } else {
                        MavlinkHub.getInstance().sendText(djiError.getDescription());
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }
                    MApplication.commandBusy = false;
                }
            });
        } else {
            getWaypointMissionOperator().pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError == null) {
                        FlightControllerProxy.getInstance().isPaused = true;
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    } else {
                        MavlinkHub.getInstance().sendText(djiError.getDescription());
                        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_DO_PAUSE_CONTINUE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    }
                    MApplication.commandBusy = false;
                }
            });
        }
    }

    private double getDistanceMeter(GlobalCoordinates gpsFrom, GlobalCoordinates gpsTo, Ellipsoid ellipsoid) {
        GeodeticCurve geodeticCurve = new GeodeticCalculator().calculateGeodeticCurve(ellipsoid, gpsFrom, gpsTo);
        return geodeticCurve.getEllipsoidalDistance();
    }

    private double getWaypointDistance(Location from, Location to) {
        GlobalCoordinates start = new GlobalCoordinates(from.getLatitude(), from.getLongitude());
        GlobalCoordinates end = new GlobalCoordinates(to.getLatitude(), to.getLongitude());

        double v_dis = getDistanceMeter(start, end, Ellipsoid.WGS84);

        return Math.sqrt(v_dis*v_dis + (from.getAltitude() - to.getAltitude())*(from.getAltitude() - to.getAltitude()));
    }

    private double getMaxDistance(List<Location> locations) {

        if(locations == null) return 0;
        if(locations.size() < 2) return 0;

        double maxDistance = 0;
        Location uavLocation = FlightControllerProxy.getInstance().getCurrentLocation();

        for(int i=1; i <locations.size(); i++) {
            double dis = getWaypointDistance(uavLocation, locations.get(i));
            if(dis > maxDistance)
                maxDistance = dis;
        }

        return maxDistance;
    }

    private double getLastWaypointDistance(List<Location> locations) {
        if(locations == null) return 0;
        if(locations.size() < 2) return 0;

        Location uavLocation = FlightControllerProxy.getInstance().getCurrentLocation();
        double dis = getWaypointDistance(uavLocation, locations.get(locations.size() - 1));

        return dis;
    }

    private double getTotalDistance(List<Location> locations) {
        if(locations == null) return 0;
        if(locations.size() < 2) return 0;

        double totalDistance = 0;

        Location uavLocation = FlightControllerProxy.getInstance().getCurrentLocation();
        double disTemp;

        for(int i=1; i <locations.size(); i++) {
            if(i == 1)
                disTemp = getWaypointDistance(uavLocation, locations.get(1));
            else
                disTemp = getWaypointDistance(locations.get(i), locations.get(1-1));

            totalDistance += disTemp;
        }
        disTemp = getWaypointDistance(uavLocation, locations.get(locations.size() - 1));
        totalDistance += disTemp;

        return totalDistance;
    }

    public int preflightCheckMannul() {
        if(!RemoteControllerProxy.getInstance().isLive()) {
            return 1;
        }

        if(!FlightControllerProxy.getInstance().isLive()) {
            return 2;
        }

        if(!FlightControllerProxy.getInstance().isPositionOK()) {
            return 3;
        }

        if(BatteryProxy.getInstance().isLowPower()) {
            return 8;
        }

        if(ChargePad.getInstance().getBarStatus() != ChargePad.BAR_STATUS_LOCKED) {
            return 9;
        }

        return 0;
    }

    public int preflightCheck() {
        if(isMannulControl)
            return preflightCheckMannul();
        else
            return preflightCheckAuto();
    }

    public int preflightCheckAuto() {

        if(!RemoteControllerProxy.getInstance().isLive()) {
            return 1;
        }

        if(!FlightControllerProxy.getInstance().isLive()) {
            return 2;
        }

        if(!FlightControllerProxy.getInstance().isPositionOK()) {
            return 3;
        }

        if(savedWaypoints.size() < 2) {
            return 4;
        }

        if(getMaxDistance(savedWaypoints) > 5000) {
            return 5;
        }

        if(getLastWaypointDistance(savedWaypoints) < 30) {
            return 6;
        }

        if(getTotalDistance(savedWaypoints) > 20000) {
            return 7;
        }

        if(BatteryProxy.getInstance().isLowPower()) {
            return 8;
        }

        if(ChargePad.getInstance().getBarStatus() != ChargePad.BAR_STATUS_LOCKED) {
            return 9;
        }

        return 0;
    }

    void handleRTL() {
        FlightController flightController = MApplication.getFlightControllerInstance();
        if(flightController == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!flightController.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!FlightControllerProxy.getInstance().isFlying()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_NAV_RETURN_TO_LAUNCH, (short)MAV_RESULT.MAV_RESULT_FAILED);
                }
            }
        });
    }

    private void handleRCOverride(MAVLinkPacket packet) {
        if(!isMannulControl)
            return;

        FlightController flightController = MApplication.getFlightControllerInstance();
        if(flightController == null)
            return;
        if(!flightController.isConnected())
            return;

        msg_rc_channels_override msg = (msg_rc_channels_override)packet.unpack();

        int roll_stick = msg.chan1_raw;
        float roll = 0.0f;
        if(roll_stick >= 1000 && roll_stick <= 2000) {
            roll = (roll_stick-1500) * 60.0f / 1000;
        }

        int pitch_stick = msg.chan2_raw;
        float pitch = 0.0f;
        if(pitch_stick >= 1000 && pitch_stick <= 2000) {
            pitch = (pitch_stick-1500) * 60.0f / 1000;
        }

        int thr_stick = msg.chan3_raw;
        float thr = 0.0f;
        if(thr_stick >= 1000 && thr_stick <= 2000) {
            thr = (thr_stick-1500) * 10.0f / 1000;
        }

        int yaw_stick = msg.chan4_raw;
        float yaw = 0.0f;
        if(yaw_stick >= 1000 && yaw_stick <= 2000) {
            yaw = (yaw_stick - 1500) * 60.0f / 1000;
        }

        FlightControlData flightControlData = new FlightControlData(pitch,roll,yaw,thr);

        flightController.sendVirtualStickFlightControlData(flightControlData, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError != null) {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }
}
