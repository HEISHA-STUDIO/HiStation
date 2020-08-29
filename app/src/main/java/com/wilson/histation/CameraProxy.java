package com.wilson.histation;

import com.MAVLink.enums.CAMERA_MODE;
import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;
import com.vividsolutions.jts.noding.snapround.MCIndexPointSnapper;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.remotecontroller.H;
import dji.sdk.remotecontroller.V;

class CameraProxy {
    private static final CameraProxy ourInstance = new CameraProxy();

    static CameraProxy getInstance() {
        return ourInstance;
    }

    private CameraProxy() {
    }

    private VideoFeeder.VideoFeed videoFeed = null;
    private VideoFeeder.VideoDataListener videoDataListener = new VideoFeeder.VideoDataListener() {
        @Override
        public void onReceive(byte[] bytes, int i) {
            //MQTTService.getInstance().publishTopic(MQTTService.TOPIC_LOG, ("DJI: " + i).getBytes());
            //publishMQTTTopic(bytes, i);
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
            //videoFeed = VideoFeeder.getInstance().getPrimaryVideoFeed();

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

    public void takePhoto() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Camera");
            return;
        }

        if(!camera.isConnected()) {
            HSCloudBridge.getInstance().sendDebug("Camera disconnect");
            return;
        }

        camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    HSCloudBridge.getInstance().sendDebug("Take a photo");
                } else {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }

    public void startRecord() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Camera");
            return;
        }

        if(!camera.isConnected()) {
            HSCloudBridge.getInstance().sendDebug("Camera disconnect");
            return;
        }

        camera.startRecordVideo(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    HSCloudBridge.getInstance().sendDebug("Start record");
                } else {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }

    public void stopRecord() {
        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Camera");
            return;
        }

        if(!camera.isConnected()) {
            HSCloudBridge.getInstance().sendDebug("Camera disconnect");
            return;
        }

        camera.stopRecordVideo(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    HSCloudBridge.getInstance().sendDebug("Stop record");
                } else {
                    HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                }
            }
        });
    }
}
