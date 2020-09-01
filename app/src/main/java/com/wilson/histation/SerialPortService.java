package com.wilson.histation;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android_serialport_api.SerialPort;

public class SerialPortService extends Service {

    private SerialPort serialPort = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    private ReceiveThread receiveThread = null;
    private boolean isStart = false;

    @Override
    public void onCreate() {
        super.onCreate();
        openSerialPort();
    }

    @Override
    public void onDestroy() {
        closeSerialPort();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new CustomBinder();
    }

    public class CustomBinder extends Binder {
        public SerialPortService getService() { return SerialPortService.this; }
    }

    private class ReceiveThread extends Thread {

        @Override
        public void run() {
            super.run();

            while (isStart) {
                if (inputStream == null) {
                    return;
                }
                byte[] readData = new byte[1024];
                try {
                    int size = inputStream.read(readData);
                    if (size > 0) {
                        if (mOnDataCallback != null)
                        {
                            mOnDataCallback.onDataReceived(readData, size);
                            //Log.i(MApplication.LOG_TAG, "DATA IN");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    public void openSerialPort() {
        try {
            serialPort = new SerialPort(new File("/dev/ttyS4"), 57600, 0);
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            isStart = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        getSerialPort();
    }

    private void closeSerialPort() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            isStart = false;
            Log.i("SerialPort", "closeSerial");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendSerialPort(byte[] sendData, int size) {
        //Log.i("HEISHA", "SEND DATA");
        try {
            outputStream.write(sendData, 0, size);
            outputStream.flush();
            //Log.i("HEISHA", "SEND DATA");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getSerialPort() {
        if (receiveThread == null) {
            receiveThread = new ReceiveThread();
        }
        receiveThread.start();
    }

    public OnDataCallback  mOnDataCallback  = null;

    public void setOnDataCallback(OnDataCallback mOnDataCallback) {
        this.mOnDataCallback = mOnDataCallback;
    }

    public interface OnDataCallback {
        void onDataReceived(byte[] data, int len);
    }
}
