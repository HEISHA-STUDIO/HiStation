package com.wilson.histation;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vividsolutions.jts.noding.snapround.MCIndexPointSnapper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.sdkmanager.DJISDKInitEvent;
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
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkAndRequestPermissions();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*Intent intent = getPackageManager()
                        .getLaunchIntentForPackage(getApplication().getPackageName());
                PendingIntent restartIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
                AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent); // 1秒钟后重启应用
                System.exit(0);*/

                MediaFileManager.getInstance().handleFileListRequest();
            }
        });

        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.requestNetwork(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                super.onAvailable(network);

                HSCloudBridge.getInstance().connect();
                HSCloudBridge.getInstance().setTestListener(testListener);
                HSCloudBridge.getInstance().setMavLinkListener(MavlinkHub.getInstance().mavLinkListener);
            }
        });

        //HSCloudBridge.getInstance().connect();
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

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
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

                        }

                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent baseComponent, BaseComponent baseComponent1) {

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

                if(message.equals("Status")) {
                    updateStatus();
                } else if(message.equals("Battery")) {
                    updateBattery();
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
}
