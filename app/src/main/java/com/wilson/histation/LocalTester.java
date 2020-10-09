package com.wilson.histation;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_mediafile_request;
import com.MAVLink.DLink.msg_mediafile_request_list;
import com.MAVLink.DLink.msg_rc_channels_override;
import com.MAVLink.enums.CAMERA_MODE;
import com.MAVLink.enums.CAMERA_STORAGE_LOCATION;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_MODE;
import com.MAVLink.enums.MEDIAFILE_REQUEST_TYPE;
import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_MODE;

class LocalTester {
    private static final LocalTester ourInstance = new LocalTester();

    static LocalTester getInstance() {
        return ourInstance;
    }

    private LocalTester() {
    }

    private int index_sd = 0;
    private int index_in = 0;

    public void testBarLock() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_PAD_LOCK;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testBarUnlock() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_PAD_UNLOCK;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testTurnOnDrone() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testTurnOffDrone() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSetStorageSD() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_STORAGE_LOCATION;
        msg.param1 = CAMERA_STORAGE_LOCATION.SDCARD;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSetStorageInternal() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_STORAGE_LOCATION;
        msg.param1 = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSetPhotoMode() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_MODE;
        msg.param2 = CAMERA_MODE.CAMERA_MODE_IMAGE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSetVideoMode() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_MODE;
        msg.param2 = CAMERA_MODE.CAMERA_MODE_VIDEO;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSetDownloadMode() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_MODE;
        msg.param2 = CAMERA_MODE.CAMERA_MODE_DOWNLOAD;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testTakePhoto() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testStartRecord() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testStopRecord() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testT3Streaming() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_VIDEO_STREAMING_REQUEST;
        msg.param1 = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_T3_CAMERA;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testDownloadStreaming() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_VIDEO_STREAMING_REQUEST;
        msg.param1 = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_MEDIAFILE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testDroneStreaming() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_VIDEO_STREAMING_REQUEST;
        msg.param1 = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_DRONE_CAMERA;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDList() {
        msg_mediafile_request_list msg = new msg_mediafile_request_list();
        msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testInternalList() {
        msg_mediafile_request_list msg = new msg_mediafile_request_list();
        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDMediaFileReqeust() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;
        msg.index = index_sd++;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.THUMBNAIL;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testINMediaFileRequest() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
        msg.index = index_in++;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.THUMBNAIL;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDPreviewRequest() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;
        msg.index = index_in++;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.PREVIEW;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDRawFile() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
        msg.index = index_in++;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.RAW;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDRawPage() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.PAGE;
        msg.index = 0;
        msg.page_index = 0;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testSDFormat() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_REQUEST_STORAGE_FORMAT;
        msg.param1 = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testZoomIn() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM;
        msg.param2 = 7;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testZoomOut() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM;
        msg.param2 = 3;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testEnableVirtualStick() {
        msg_command_int msg = new msg_command_int();
        msg.command = MAV_CMD_DO_SET_MODE;
        msg.param1 = MAV_MODE.MAV_MODE_MANUAL;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testUP() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan3_raw = 1800;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testDown() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan3_raw = 1200;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testForward() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan2_raw = 1200;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testBackward() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan2_raw = 1800;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testLeft() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan1_raw = 1200;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testRight() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan1_raw = 1800;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testYaw() {
        msg_rc_channels_override msg = new msg_rc_channels_override();
        msg.chan4_raw = 1800;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testCancelMission() {
        MissionPlanner.getInstance().handleCancelMission();
    }

    public void testRestart() {
        MissionPlanner.getInstance().handleReStartMission();
    }

    public void testStart() {
        MissionPlanner.getInstance().testStartMission();
    }
}

