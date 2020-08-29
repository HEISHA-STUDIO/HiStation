package com.wilson.histation;

import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;

class MediaFileManager {
    private static final MediaFileManager ourInstance = new MediaFileManager();

    static MediaFileManager getInstance() {
        return ourInstance;
    }

    private MediaFileManager() {
    }

    List<MediaFile> INFiles = new ArrayList<>();

    public void handleFileListRequest() {
        final Camera camera = MApplication.getCameraInstance();

        if(camera != null) {
            camera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(djiError == null) {
                        HSCloudBridge.getInstance().sendDebug("Download mode");
                        final MediaManager mediaManager = camera.getMediaManager();
                        if(mediaManager != null) {
                            mediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE, new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError == null) {
                                        INFiles.clear();
                                        INFiles = mediaManager.getInternalStorageFileListSnapshot();
                                        HSCloudBridge.getInstance().sendDebug("Count: " + INFiles.size());
                                        fetchRawFile(INFiles.get(1));
                                    } else {
                                        HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                                    }
                                }
                            });
                        }
                    } else {
                        HSCloudBridge.getInstance().sendDebug(djiError.getDescription());
                    }
                }
            });
        } else {
            HSCloudBridge.getInstance().sendDebug("Camera not found");
        }
    }

    private void fetchRawFile(MediaFile file) {
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File sdCard = Environment.getExternalStorageDirectory();
                File newFolder = new File(sdCard + File.separator + "IMAGE_DIR");
                if(!newFolder.exists()){
                    boolean isSuccess = newFolder.mkdirs();
                }

                filePath = newFolder.toString();
                fileName = file.getFileName();

                file.fetchFileData(newFolder, null, new DownloadListener<String>() {
                    @Override
                    public void onStart() {
                        HSCloudBridge.getInstance().sendDebug("Start");
                    }

                    @Override
                    public void onRateUpdate(long l, long l1, long l2) {
                        HSCloudBridge.getInstance().sendDebug("L: " + l + " : " + l1 + " : " + l2);
                    }

                    @Override
                    public void onProgress(long l, long l1) {
                        //MQTTService.getInstance().publishTopic(MApplication.LOG_TAG, ("L: " + l + ":" + l1).getBytes());
                        //HSCloudBridge.getInstance().sendDebug("L: " + l + ":" + l1);
                    }

                    @Override
                    public void onSuccess(String s) {
                        HSCloudBridge.getInstance().sendDebug("Success");
                        new Thread(fileTransferSession).start();
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        HSCloudBridge.getInstance().sendDebug("Fail");
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    String  filePath;
    String  fileName;
    Runnable fileTransferSession = new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            File file = new File(filePath, fileName);

            if(file.exists()) {
                HSCloudBridge.getInstance().sendDebug("Start Transfer");

                int bufSize = 1024*1000;
                byte[] buf = new byte[bufSize];
                int readSize = -1;

                try {
                    RandomAccessFile randomAccessFile = new RandomAccessFile(filePath + File.separator + fileName, "r");

                    long pages = randomAccessFile.length() / bufSize;
                    long current = 0;

                    HSCloudBridge.getInstance().sendDebug("Pages: " + pages);

                    while (true) {
                        readSize = randomAccessFile.read(buf);

                        if(readSize < bufSize) {
                            break;
                        }

                        for(int i=0; i<1000;i++) {
                            RawFilePackage rawFilePackage = new RawFilePackage();
                            rawFilePackage.total = 1000;
                            rawFilePackage.seq = (short)i;
                            rawFilePackage.len = 1024;
                            System.arraycopy(buf, 1024*i, rawFilePackage.payload, 0, 1024);
                            HSCloudBridge.getInstance().publicTopic("DATA", rawFilePackage.pack());
                        }

                        HSCloudBridge.getInstance().sendDebug("Page: " + current++);
                    }

                    HSCloudBridge.getInstance().sendDebug("Finish");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    };

    public class RawFilePackage {
        public static final int PAYLOAD_LEN_MAX = 1024;

        public short    total;
        public short    seq;
        public short    len;

        public byte[]   payload;

        public RawFilePackage() {
            payload = new byte[PAYLOAD_LEN_MAX];
        }

        byte[] pack() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(output);


            try {
                dos.writeShort((int) total);
                dos.writeShort((int) seq);
                dos.writeShort((int) len);
                dos.write(payload);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return output.toByteArray();
        }
    }
}
