package com.wilson.histation;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_mission_ack;
import com.MAVLink.DLink.msg_mission_count;
import com.MAVLink.DLink.msg_mission_item;
import com.MAVLink.DLink.msg_mission_request;
import com.MAVLink.MAVLinkPacket;
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
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.sdkmanager.DJISDKManager;

import static android.content.Context.MODE_PRIVATE;
import static com.MAVLink.DLink.msg_mission_count.MAVLINK_MSG_ID_MISSION_COUNT;
import static com.MAVLink.DLink.msg_mission_item.MAVLINK_MSG_ID_MISSION_ITEM;
import static com.MAVLink.DLink.msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST;
import static com.MAVLink.DLink.msg_mission_request_list.MAVLINK_MSG_ID_MISSION_REQUEST_LIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_PAUSE_CONTINUE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_FLIGHT_PREPARE;
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
        }
    }

    private void startWayPointReceive(MAVLinkPacket packet) {
        msg_mission_count msg = (msg_mission_count)packet.unpack();
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
        } else {
            msg_mission_request msg = new msg_mission_request();
            msg.seq = waypointReceived;
            packet = msg.pack();
        }

        MavlinkHub.getInstance().sendMavlinkPacket(packet);
    }

    private void receiveMissionItem(MAVLinkPacket packet) {
        msg_mission_item msg = (msg_mission_item)packet.unpack();
        if(msg.command == 16) {
            Location waypoint = new Location(msg.x, msg.y, msg.z);
            savedWaypoints.add(waypoint);
            waypointReceived = msg.seq + 1;
            MApplication.LOG("Received: " + waypointReceived);
            requestMavLinkNextWaypoint();
        }
    }

    private void sendWayPointCount(MAVLinkPacket packet) {
        msg_mission_count msg = new msg_mission_count();
        msg.count = savedWaypoints.size();

        MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
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
                handleTakeoff();
                break;
            case MAV_CMD_DO_PAUSE_CONTINUE:
                handlePause();
                break;
        }
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

    private void handlePause() {
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

    public int preflightCheck() {

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

        if(getMaxDistance(savedWaypoints) > 1000) {
            return 5;
        }

        if(getLastWaypointDistance(savedWaypoints) < 30) {
            return 6;
        }

        if(getTotalDistance(savedWaypoints) > 2000) {
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
}
