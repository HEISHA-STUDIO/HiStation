package com.wilson.histation;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_mediafile_request;
import com.MAVLink.DLink.msg_mediafile_request_list;
import com.MAVLink.enums.CAMERA_MODE;
import com.MAVLink.enums.CAMERA_STORAGE_LOCATION;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MEDIAFILE_REQUEST_TYPE;
import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;

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
        msg.request_type = MEDIAFILE_REQUEST_TYPE.INFORMATION;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

    public void testINMediaFileRequest() {
        msg_mediafile_request msg = new msg_mediafile_request();
        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
        msg.index = index_in++;
        msg.request_type = MEDIAFILE_REQUEST_TYPE.INFORMATION;

        MavlinkHub.getInstance().mavlink_message_handle(msg.pack());
    }

}
