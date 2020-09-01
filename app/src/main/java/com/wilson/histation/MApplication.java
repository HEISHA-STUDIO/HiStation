package com.wilson.histation;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.secneo.sdk.Helper;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.airlink.AirLink;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.RemoteController;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import static dji.sdk.base.BaseProduct.ComponentKey.AIR_LINK;
import static dji.sdk.base.BaseProduct.ComponentKey.BATTERY;
import static dji.sdk.base.BaseProduct.ComponentKey.CAMERA;
import static dji.sdk.base.BaseProduct.ComponentKey.FLIGHT_CONTROLLER;
import static dji.sdk.base.BaseProduct.ComponentKey.GIMBAL;
import static dji.sdk.base.BaseProduct.ComponentKey.REMOTE_CONTROLLER;

public class MApplication extends Application {

    static final String TAG = "HEISHA";
    static void LOG(String log) {
        Log.i(TAG, android.os.Process.myTid() + ": " + log);
    }

    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private Handler mHandler;
    private static BaseProduct mProduct;
    public static Application  app;
    public static boolean commandBusy = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Helper.install(MApplication.this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler(Looper.getMainLooper());

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (permissionCheck == 0 && permissionCheck2 == 0)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                showToast("Register Success");
                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                showToast("Register sdk fails, please check the bundle id and network connection!");
                            }
                        }

                        @Override
                        public void onProductDisconnect() {
                            showToast("Product Disconnected");
                            notifyStatusChange();
                        }

                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            showToast("Product Connect");
                            notifyStatusChange();
                        }

                        //@Override
                        //public void onProductChanged(BaseProduct baseProduct) {

                       // }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent baseComponent, BaseComponent baseComponent1) {
                            if (baseComponent1 != null) {
                                if(componentKey.equals(REMOTE_CONTROLLER)) {
                                    HSCloudBridge.getInstance().sendDebug("RC online");
                                    RemoteControllerProxy.getInstance().setBatteryStateCallback((RemoteController)baseComponent1);
                                }
                                if(componentKey.equals(AIR_LINK)) {
                                    HSCloudBridge.getInstance().sendDebug("Telemetry online");
                                    //baseComponent1.setComponentListener((BaseComponent.ComponentListener)telemetry);
                                }
                                if(componentKey.equals(BATTERY)) {
                                    HSCloudBridge.getInstance().sendDebug("Battery online");
                                    BatteryProxy.getInstance().setCallback((Battery)baseComponent1);
                                    //baseComponent1.setComponentListener((BaseComponent.ComponentListener)battery);
                                }
                                if(componentKey.equals(GIMBAL)) {
                                    HSCloudBridge.getInstance().sendDebug("Gimbal online");
                                    //baseComponent1.setComponentListener((BaseComponent.ComponentListener)gimbal);
                                }
                                if(componentKey.equals(CAMERA)) {
                                    HSCloudBridge.getInstance().sendDebug("Camera online");
                                    CameraProxy.getInstance().setVideoDataListener((Camera)baseComponent1);
                                    //baseComponent1.setComponentListener((BaseComponent.ComponentListener)camera);
                                }
                                if(componentKey.equals(FLIGHT_CONTROLLER)) {
                                    HSCloudBridge.getInstance().sendDebug("FC online");
                                    FlightControllerProxy.getInstance().setFlightControllerStatusCallback((FlightController)baseComponent1);
                                    //baseComponent1.setComponentListener((BaseComponent.ComponentListener)flightController);
                                }
                            }
                        }

                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }

        app = this;
    }

    public static synchronized BaseProduct getProductInstance() {
        if(null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
        }
        return mProduct;
    }

    public static synchronized Camera getCameraInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getCamera();
        } else {
            return null;
        }
    }

    public static synchronized RemoteController getRemoteControllerInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getRemoteController();
        } else {
            return null;
        }
    }

    public static synchronized FlightController getFlightControllerInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getFlightController();
        } else {
            return null;
        }
    }

    public static synchronized Battery getBatteryInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getBattery();
        } else {
            return null;
        }
    }

    public static synchronized Gimbal getGimbalInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getGimbal();
        } else {
            return null;
        }
    }

    public static synchronized AirLink getAirLinkInstance() {
        if(getProductInstance() == null) return null;

        if(getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getAirLink();
        } else {
            return null;
        }
    }

    private void showToast(final String toastMsg) {

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });

    }

    private void notifyStatusChange() {

    }
}
