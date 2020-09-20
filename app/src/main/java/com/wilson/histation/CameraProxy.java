package com.wilson.histation;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.enums.CAMERA_MODE;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_CAMERA_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;

class CameraProxy {
    private static final CameraProxy ourInstance = new CameraProxy();
    private static final int zoomLengthMin = 240;
    private static final int zoomLengthMax = 480;
    private static final int zoomLengthStep = 10;

    static CameraProxy getInstance() {
        return ourInstance;
    }

    private CameraProxy() {
    }

    private VideoFeeder.VideoFeed videoFeed = null;
    private VideoFeeder.VideoDataListener videoDataListener = new VideoFeeder.VideoDataListener() {
        @Override
        public void onReceive(byte[] bytes, int i) {
            if(HSVideoFeeder.getInstance().videoSource == VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_DRONE_CAMERA) {
                byte[] buf = new byte[i];
                System.arraycopy(bytes,0,buf,0,i);
                HSVideoFeeder.getInstance().feedVideo(buf);
            }
        }
    };

    public void setVideoDataListener(Camera camera) {
        if(videoFeed == null) {
            videoFeed = VideoFeeder.getInstance().provideTranscodedVideoFeed();

            if(videoFeed != null) {
                videoFeed.addVideoDataListener(videoDataListener);
            }
        }
    }

    public void setMode(int mode) {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Camera");
            return;
        }

        if(!camera.isConnected()) {
            HSCloudBridge.getInstance().sendDebug("Camera disconnect");
            return;
        }

        switch (mode) {
            case CAMERA_MODE.CAMERA_MODE_IMAGE:
                camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if(djiError == null) {
                            HSCloudBridge.getInstance().sendDebug("Success");
                        } else {
                            HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                        }
                    }
                });
                break;
            case CAMERA_MODE.CAMERA_MODE_VIDEO:
                camera.setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if(djiError == null) {
                            HSCloudBridge.getInstance().sendDebug("Success");
                        } else {
                            HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                        }
                    }
                });
                break;
        }
    }

    public void handleCommand(msg_command_int msg) {
        switch (msg.command) {
            case MAV_CMD_SET_CAMERA_MODE:
                handleSetMode(msg);
                break;
            case MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE:
                handleTakePicture();
                break;
            case MAV_CMD_VIDEO_START_CAPTURE:
                handleStartVideo();
                break;
            case MAV_CMD_VIDEO_STOP_CAPTURE:
                handleStopVideo();
                break;
            case MAV_CMD_SET_CAMERA_ZOOM:
                handleZoom(msg);
        }
    }

    private void handleZoom(msg_command_int msg) {
        Camera camera = MApplication.getCameraInstance();

        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_ACCEPTED);

        float factor = msg.param2;
        if(factor < 0)
            factor = 0;
        if(factor > 100)
            factor = 100;

        int length = zoomLengthMin + (int)(0.24*factor)*zoomLengthStep;

        camera.setHybridZoomFocalLength(length, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_FAILED);
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }

    private void handleSetMode(msg_command_int msg) {
        Camera camera = MApplication.getCameraInstance();

        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        SettingsDefinitions.CameraMode mode;
        if(msg.param2 == CAMERA_MODE.CAMERA_MODE_IMAGE) {
            mode = SettingsDefinitions.CameraMode.SHOOT_PHOTO;
        } else if(msg.param2 == CAMERA_MODE.CAMERA_MODE_VIDEO) {
            mode = SettingsDefinitions.CameraMode.RECORD_VIDEO;
        } else if(msg.param2 == CAMERA_MODE.CAMERA_MODE_DOWNLOAD) {
            mode = SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD;
        } else {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.setMode(mode, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_CAMERA_MODE, (short) MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

    private void handleTakePicture() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    MavlinkHub.getInstance().sendText("One photo taken");
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_CAMERA_IMAGE_CAPTURE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

    private void handleStartVideo() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_START_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_START_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_START_CAPTURE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.startRecordVideo(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_START_CAPTURE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    MavlinkHub.getInstance().sendText("Start record");
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_START_CAPTURE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

    private void handleStopVideo() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_STOP_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_STOP_CAPTURE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            MavlinkHub.getInstance().sendText("No camera");
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_STOP_CAPTURE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.stopRecordVideo(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_STOP_CAPTURE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                    MavlinkHub.getInstance().sendText("Stop record");
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_VIDEO_STOP_CAPTURE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

}
