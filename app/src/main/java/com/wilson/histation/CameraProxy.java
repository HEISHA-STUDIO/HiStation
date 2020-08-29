package com.wilson.histation;

import com.MAVLink.enums.CAMERA_MODE;
import com.vividsolutions.jts.noding.snapround.MCIndexPointSnapper;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.remotecontroller.H;

class CameraProxy {
    private static final CameraProxy ourInstance = new CameraProxy();

    static CameraProxy getInstance() {
        return ourInstance;
    }

    private CameraProxy() {
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
