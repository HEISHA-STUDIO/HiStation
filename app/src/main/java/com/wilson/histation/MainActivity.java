package com.wilson.histation;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;

import com.MAVLink.enums.CAMERA_MODE;
import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dji.common.error.DJIError;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.battery.Battery;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
    };
    private List<String> missingPermission = new ArrayList<>();
    private static final int REQUEST_PERMISSION_CODE = 12345;

    Camera camera;
    SurfaceHolder previewHolder;
    byte[] previewBuffer;
    protected SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            startCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stopCamera();
        }
    };
    protected Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            camera.addCallbackBuffer(previewBuffer);
            //MApplication.LOG("T3: " + data.length);
            HSVideoFeeder.getInstance().T3Encode(data);
        }
    };

    protected TextureView mVideoSurface = null;
    protected DJICodecManager djiCodecManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("HiStation (V"+BuildConfig.VERSION_NAME+")");
        toolbar.setLogo(R.mipmap.ic_launcher);
        setSupportActionBar(toolbar);

        checkAndRequestPermissions();

        initUI();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = getPackageManager()
                        .getLaunchIntentForPackage(getApplication().getPackageName());
                PendingIntent restartIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
                AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent); // 1秒钟后重启应用
                System.exit(0);
            }
        });

        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.requestNetwork(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                super.onAvailable(network);

                HSCloudBridge.getInstance().connect();
                HSCloudBridge.getInstance().setTestListener(testListener);
                HSCloudBridge.getInstance().setMavLinkListener(MavlinkHub.getInstance().mavLinkListener);
                MissionPlanner.getInstance();
                ChargePad.getInstance();
            }
        });

        //HSCloudBridge.getInstance().connect();
    }

    private void initUI() {
        SurfaceView surfaceView = (SurfaceView)findViewById(R.id.SurfaceViewPlay);
        previewHolder = surfaceView.getHolder();
        previewHolder.addCallback(surfaceHolderCallback);

        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);

        mVideoSurface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if(djiCodecManager == null) {
                    djiCodecManager = new DJICodecManager(MainActivity.this, surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    private void startCamera() {
        int cam_width = 640;
        int cam_height = 480;
        previewHolder.setFixedSize(cam_width, cam_height);

        int stride = (int)Math.ceil(cam_width/16.0f) * 16;
        int cStride = (int)Math.ceil(cam_width/32.0f) * 16;
        final int frameSize = stride * cam_height;
        final int qFrameSize = cStride * cam_height / 2;
        previewBuffer = new byte[frameSize + qFrameSize * 2];

        try {
            camera = Camera.open(0);
            camera.setPreviewDisplay(previewHolder);
            Camera.Parameters params = camera.getParameters();
            params.setPictureSize(cam_width, cam_height);
            params.setPreviewFormat(ImageFormat.YV12);
            camera.setParameters(params);
            camera.addCallbackBuffer(previewBuffer);
            camera.setPreviewCallbackWithBuffer(previewCallback);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void stopCamera() {
        if(camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            //startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //showToast("Need to grant the permissions!");
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            //startSDKRegistration();
            showToast("Reboot needed");
        } else {
            //showToast("Missing permissions!!!");
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

    private ArrayList<String> testBuf = new ArrayList<>();
    private Runnable testProcess = new Runnable() {
        @Override
        public void run() {
            while (true) {
                boolean empty = false;
                String message = "";

                synchronized (testBuf) {
                    if(testBuf.size() == 0)
                        empty = true;
                    else
                        message = testBuf.remove(0);
                }

                if(empty) {
                    try{
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                if(message.equals("Gimbal")) {
                    GimbaProxy.getInstance().moving(10,0);
                }else if (message.equals("SET_IMAGE")){
                    LocalTester.getInstance().testSetPhotoMode();
                } else if(message.equals("SET_VIDEO")) {
                    LocalTester.getInstance().testSetVideoMode();
                } else if(message.equals("TAKE_PHOTO")) {
                    LocalTester.getInstance().testTakePhoto();
                } else if(message.equals("START_VIDEO")) {
                    LocalTester.getInstance().testStartRecord();
                } else if(message.equals("STOP_VIDEO")) {
                    LocalTester.getInstance().testStopRecord();
                } else if(message.equals("T3")) {
                    LocalTester.getInstance().testT3Streaming();
                } else if(message.equals("DRONE")) {
                    LocalTester.getInstance().testDroneStreaming();
                } else if(message.equals("LOGIN")) {
                    login();
                } else if(message.equals("LOCKED")) {
                    ChargePad.getInstance().barStatus = ChargePad.BAR_STATUS_LOCKED;
                } else if(message.equals("UNLOCKED")) {
                    ChargePad.getInstance().barStatus = ChargePad.BAR_STATUS_UNLOCKED;
                }else if(message.equals("SD")) {
                    LocalTester.getInstance().testSetStorageSD();
                } else if(message.equals("Internal")) {
                    LocalTester.getInstance().testSetStorageInternal();
                } else if(message.equals("SD_LIST")) {
                    LocalTester.getInstance().testSDList();
                } else if(message.equals("IN_LIST")) {
                    LocalTester.getInstance().testInternalList();
                } else if(message.equals("SD_INFO")) {
                    LocalTester.getInstance().testSDMediaFileReqeust();
                } else if(message.equals("IN_INFO")) {
                    LocalTester.getInstance().testINMediaFileRequest();
                }
            }
        }
    };
    private Thread threadTest = null;

    private HSCloudBridge.TestListener testListener = new HSCloudBridge.TestListener() {
        @Override
        public void onMessage(String msg) {

            synchronized (testBuf) {
                testBuf.add(msg);
            }

            if(threadTest == null) {
                threadTest = new Thread(testProcess);
                threadTest.start();
            }

        }
    };

    private void updateStatus() {
        if(MApplication.getProductInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Product");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS Product");
        }

        if(MApplication.getRemoteControllerInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE RC");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS RC");
            if(MApplication.getRemoteControllerInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("RC connected");
            } else {
                HSCloudBridge.getInstance().sendDebug("RC disconnected");
            }
        }

        if(MApplication.getCameraInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Camera");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS Camera");
            if(MApplication.getCameraInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("Camere connected");
            } else {
                HSCloudBridge.getInstance().sendDebug("Camera disconnected");
            }
        }

        if(MApplication.getFlightControllerInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE FC");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS FC");
            if(MApplication.getFlightControllerInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("FC Connected");
            } else {
                HSCloudBridge.getInstance().sendDebug("FC Disconncted");
            }
        }

        if(MApplication.getBatteryInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Battery");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS Battery");
            if(MApplication.getBatteryInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("Battery conncted");
            } else {
                HSCloudBridge.getInstance().sendDebug("Battery disconnted");
            }
        }

        if(MApplication.getGimbalInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Gimbal");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS Gimbal");
            if(MApplication.getGimbalInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("Gimbal connected");
            } else {
                HSCloudBridge.getInstance().sendDebug("Gimbal disconnected");
            }
        }

        if(MApplication.getAirLinkInstance() == null) {
            HSCloudBridge.getInstance().sendDebug("NONE Airlink");
        } else {
            HSCloudBridge.getInstance().sendDebug("HAS Airlink");
            if(MApplication.getAirLinkInstance().isConnected()) {
                HSCloudBridge.getInstance().sendDebug("Airlink connected");
            } else {
                HSCloudBridge.getInstance().sendDebug("Airlink disconnected");
            }
        }
    }

    private void updateBattery() {
        Battery battery = MApplication.getBatteryInstance();

        if(battery != null) {
            if(battery.isConnected()) {

            }
        }
    }

    private void login() {
        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        //showToast("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                    }
                });
    }
}
