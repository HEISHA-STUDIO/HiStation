package com.wilson.histation;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.pgyersdk.crash.PgyCrashManager;
import com.pgyersdk.update.DownloadFileListener;
import com.pgyersdk.update.PgyUpdateManager;
import com.pgyersdk.update.UpdateManagerListener;
import com.pgyersdk.update.javabean.AppBean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.realname.AircraftBindingState;
import dji.common.realname.AppActivationState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKManager;
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
    private AtomicBoolean isNetworkSetup = new AtomicBoolean(false);

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

    private AppActivationManager appActivationManager;
    private AppActivationState.AppActivationStateListener activationStateListener;
    private AircraftBindingState.AircraftBindingStateListener bindingStateListener;
    private String  activationState = "";
    private String  boundState = "";
    private String  new_version = "Latest";

    private AppBean _appBean = null;
    final int MAX_PROGRESS = 100;
    ProgressDialog progressDialog = null;

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

        //initActivateManager();

        initUpdate();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
        fab.setVisibility(View.INVISIBLE);

        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.requestNetwork(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                super.onAvailable(network);

                if(isNetworkSetup.compareAndSet(false, true)) {
                    initActivateManager();
                    HSCloudBridge.getInstance().connect();
                    HSCloudBridge.getInstance().setTestListener(testListener);
                    HSCloudBridge.getInstance().setMavLinkListener(MavlinkHub.getInstance().mavLinkListener);
                    MissionPlanner.getInstance();
                    ChargePad.getInstance();
                }
            }
        });

        //HSCloudBridge.getInstance().connect();
    }

    @Override
    public void onResume() {
        setUpListener();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        tearDownListener();
        super.onDestroy();
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

        initCrashHandle();
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
        } else if(id == R.id.action_login) {
            login();
            return true;
        } else if(id == R.id.action_update) {
            if(_appBean != null) {
                PgyUpdateManager.downLoadApk(_appBean.getDownloadURL());

                if(progressDialog == null) {
                    progressDialog = new ProgressDialog(this);
                }
                progressDialog.setProgress(0);
                progressDialog.setTitle("Downloading...");
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(MAX_PROGRESS);
                progressDialog.show();
            } else {
                showToast("Latest");
            }
        } else if(id == R.id.action_reboot) {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getApplication().getPackageName());
            PendingIntent restartIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
            AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent); // 1秒钟后重启应用
            System.exit(0);
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

                if(message.equals("SET_DOWNLOAD")) {
                    LocalTester.getInstance().testSetDownloadMode();
                } else if (message.equals("SET_IMAGE")){
                    LocalTester.getInstance().testSetPhotoMode();
                } else if(message.equals("SET_VIDEO")) {
                    LocalTester.getInstance().testSetVideoMode();
                } else if(message.equals("TAKE_PHOTO")) {
                    LocalTester.getInstance().testTakePhoto();
                } else if(message.equals("START_VIDEO")) {
                    LocalTester.getInstance().testStartRecord();
                } else if(message.equals("STOP_VIDEO")) {
                    LocalTester.getInstance().testStopRecord();
                } else if(message.equals("SD")) {
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
                } else if(message.equals("Preview")) {
                    LocalTester.getInstance().testSDPreviewRequest();
                } else if(message.equals("Raw")) {
                    LocalTester.getInstance().testSDRawFile();
                } else if(message.equals("Page")) {
                    LocalTester.getInstance().testSDRawPage();
                } else if(message.equals("T3")) {
                    LocalTester.getInstance().testT3Streaming();
                } else if(message.equals("FILE")) {
                    LocalTester.getInstance().testDownloadStreaming();
                } else if(message.equals("FORMAT")) {
                    LocalTester.getInstance().testSDFormat();
                } else if(message.equals("DRONE")) {
                    LocalTester.getInstance().testDroneStreaming();
                } else if(message.equals("ZOOMIN")) {
                    LocalTester.getInstance().testZoomIn();
                } else if(message.equals("ZOOMOUT")) {
                    LocalTester.getInstance().testZoomOut();
                } else if(message.equals("Stick")) {
                    LocalTester.getInstance().testEnableVirtualStick();
                } else if(message.equals("UP")) {
                    LocalTester.getInstance().testUP();
                } else if(message.equals("DOWN")) {
                    LocalTester.getInstance().testDown();
                } else if(message.equals("Forward")) {
                    LocalTester.getInstance().testForward();
                } else if(message.equals("Backward")) {
                    LocalTester.getInstance().testBackward();
                } else if(message.equals("Left")) {
                    LocalTester.getInstance().testLeft();
                } else if(message.equals("Right")) {
                    LocalTester.getInstance().testRight();
                } else if(message.equals("Yaw")) {
                    LocalTester.getInstance().testYaw();
                } else if(message.equals("Cancel")) {
                    LocalTester.getInstance().testCancelMission();
                } else if(message.equals("Restart")) {
                    LocalTester.getInstance().testRestart();
                } else if(message.equals("Start")) {
                    LocalTester.getInstance().testStart();
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

    private void login() {
        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        showToast("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        if(error != null) {
                            showToast(error.getDescription());
                        }
                    }
                });
    }

    private void initCrashHandle() {
        PgyCrashManager.register();
        PgyCrashManager.setIsIgnoreDefaultHander(true);
    }

    private void initActivateManager() {
        setUpListener();

        appActivationManager = DJISDKManager.getInstance().getAppActivationManager();

        if (appActivationManager != null) {
            appActivationManager.addAppActivationStateListener(activationStateListener);
            appActivationManager.addAircraftBindingStateListener(bindingStateListener);
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activationState = "" + appActivationManager.getAppActivationState();
                    boundState = "" + appActivationManager.getAircraftBindingState();
                    updateTitle();
                }
            });
        }
    }

    private void setUpListener() {
        // Example of Listener
        activationStateListener = new AppActivationState.AppActivationStateListener() {
            @Override
            public void onUpdate(final AppActivationState appActivationState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activationState = "" + appActivationState;
                        updateTitle();
                    }
                });
            }
        };

        bindingStateListener = new AircraftBindingState.AircraftBindingStateListener() {

            @Override
            public void onUpdate(final AircraftBindingState bindingState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boundState = "" + bindingState;
                        updateTitle();
                    }
                });
            }
        };
    }

    private void tearDownListener() {
        if (activationStateListener != null) {
            if(appActivationManager != null)
                appActivationManager.removeAppActivationStateListener(activationStateListener);
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activationState = "";
                    updateTitle();
                }
            });
        }
        if (bindingStateListener !=null) {
            if(appActivationManager != null)
                appActivationManager.removeAircraftBindingStateListener(bindingStateListener);
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boundState = "";
                    updateTitle();
                }
            });
        }
    }

    private void updateTitle() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        String title = "HiStation ("+BuildConfig.VERSION_NAME + ") - " + new_version;
        if(!boundState.equals("")) {
            title += " - " + boundState;
        }
        if(!activationState.equals("")) {
            title += " - " + activationState;
        }
        toolbar.setTitle(title);

        HSCloudBridge.getInstance().sendDebug(title);
    }

    private void initUpdate() {
        new PgyUpdateManager.Builder()
                .setForced(true)
                .setUserCanRetry(false)
                .setDeleteHistroyApk(true)
                .setUpdateManagerListener(new UpdateManagerListener() {
                    @Override
                    public void onNoUpdateAvailable() {
                        new_version = "Latest";
                        updateTitle();
                    }

                    @Override
                    public void onUpdateAvailable(AppBean appBean) {
                        _appBean = appBean;
                        new_version = appBean.getVersionName() + " Available";
                        updateTitle();
                    }

                    @Override
                    public void checkUpdateFailed(Exception e) {
                        showToast("Check update failed");
                    }
                }).setDownloadFileListener(new DownloadFileListener() {
            @Override
            public void downloadFailed() {
                showToast("Download failed");
                if(progressDialog != null) {
                    progressDialog.hide();
                    progressDialog = null;
                }
            }

            @Override
            public void downloadSuccessful(File file) {
                if(progressDialog != null) {
                    progressDialog.hide();
                    progressDialog = null;
                }
                PgyUpdateManager.installApk(file);
            }

            @Override
            public void onProgressUpdate(Integer... args) {
                progressDialog.setProgress(Integer.parseInt(String.valueOf(args[0])));
            }
        }).register();
    }
}
