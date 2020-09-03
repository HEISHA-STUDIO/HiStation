package com.wilson.histation;

import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;

class FlightControllerProxy {
    private static final FlightControllerProxy ourInstance = new FlightControllerProxy();

    static FlightControllerProxy getInstance() {
        return ourInstance;
    }

    private FlightControllerProxy() {
    }

    private FlightControllerState.Callback callback = new FlightControllerState.Callback() {
        @Override
        public void onUpdate(FlightControllerState flightControllerState) {
            MavlinkHub.getInstance().sendGlobalPosition(flightControllerState);
            MavlinkHub.getInstance().sendGPSStatus(flightControllerState);
            MavlinkHub.getInstance().sendAttitude(flightControllerState);
            bExpectedOn = true;

            if(flightControllerState.getSatelliteCount() > 11) {
                bPositionOK = true;
            } else {
                bPositionOK = false;
            }

            LocationCoordinate3D locationCoordinate3D = flightControllerState.getAircraftLocation();
            if(locationCoordinate3D != null) {
                currentLocation.setLatitude((float) locationCoordinate3D.getLatitude());
                currentLocation.setLongitude((float) locationCoordinate3D.getLongitude());
                currentLocation.setAltitude(0);
            }

            if(!HSVideoFeeder.getInstance().videoSourceSetted) {
                autoSwitchVideoSource(flightControllerState);
            }
        }
    };

    private FlightController flightController = null;
    private boolean bExpectedOn = false;
    public boolean getExpectedOn() {
        return bExpectedOn;
    }
    public void setExpectedOn(boolean expectedOn) {
        bExpectedOn = expectedOn;
    }

    private boolean bPositionOK = false;
    private Location currentLocation = new Location();

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public boolean isPaused = false;

    public void setFlightControllerStatusCallback(FlightController fc) {

        if(flightController == null) {
            flightController = fc;
            flightController.setStateCallback(callback);
        }

        flightController.getFlightAssistant().setLandingProtectionEnabled(false, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

            }
        });
    }

    public boolean isLive() {
        FlightController flightController = MApplication.getFlightControllerInstance();

        if(flightController == null)
            return false;

        if(flightController.isConnected())
            return true;
        else
            return false;
    }

    public boolean isPositionOK() {
        if (isLive()) {
            return bPositionOK;
        } else {
            return false;
        }
    }

    public boolean isFlying() {
        if(flightController == null)
            return false;

        if(!flightController.isConnected())
            return false;

        FlightControllerState state = flightController.getState();

        if(state.isFlying())
            return true;

        return false;
    }

    public boolean isPaused() {
        return isPaused;
    }

    private void autoSwitchVideoSource(FlightControllerState flightControllerState) {
        if(isFlying()) {
            if(flightControllerState.getAircraftLocation().getAltitude() > 2) {
                HSVideoFeeder.getInstance().videoSource = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_DRONE_CAMERA;
            } else {
                HSVideoFeeder.getInstance().videoSource = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_T3_CAMERA;
            }
        } else {
            HSVideoFeeder.getInstance().videoSource = VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_T3_CAMERA;
        }
    }
}
