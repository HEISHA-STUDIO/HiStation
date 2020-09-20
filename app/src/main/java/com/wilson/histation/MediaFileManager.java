package com.wilson.histation;

import android.graphics.Bitmap;
import android.os.Environment;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.DLink.msg_mediafile_count;
import com.MAVLink.DLink.msg_mediafile_data_segment;
import com.MAVLink.DLink.msg_mediafile_download_progress;
import com.MAVLink.DLink.msg_mediafile_information;
import com.MAVLink.DLink.msg_mediafile_request;
import com.MAVLink.DLink.msg_mediafile_request_list;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.enums.CAMERA_STORAGE_LOCATION;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.enums.MEDIAFILE_DOWNLOAD_STAGE;
import com.MAVLink.enums.MEDIAFILE_REQUEST_TYPE;
import com.MAVLink.enums.MEDIATYPE;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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

import static com.MAVLink.DLink.msg_mediafile_request.MAVLINK_MSG_ID_MEDIAFILE_REQUEST;
import static com.MAVLink.DLink.msg_mediafile_request_list.MAVLINK_MSG_ID_MEDIAFILE_REQUEST_LIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_STORAGE_FORMAT;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_SET_STORAGE_LOCATION;

class MediaFileManager {
    private static final MediaFileManager ourInstance = new MediaFileManager();

    static MediaFileManager getInstance() {
        return ourInstance;
    }

    private MediaFileManager() {
    }

    List<MediaFile> INFiles = new ArrayList<>();
    List<MediaFile> SDFiles = new ArrayList<>();

    private void fetchRawFile(final int index, final MediaFile file) {
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File sdCard = Environment.getExternalStorageDirectory();
                File newFolder = new File(sdCard + File.separator + "IMAGE_DIR");
                if(!newFolder.exists()){
                    boolean isSuccess = newFolder.mkdirs();
                }

                filePath = newFolder.toString();
                fileName = file.getFileName();
                fileIndex = index;

                file.fetchFileData(newFolder, null, new DownloadListener<String>() {
                    @Override
                    public void onStart() {
                        HSCloudBridge.getInstance().sendDebug("Start");
                    }

                    @Override
                    public void onRateUpdate(long l, long l1, long l2) {
                        //HSCloudBridge.getInstance().sendDebug("L: " + l + " : " + l1 + " : " + l2);

                        int percent = (int)(l1 * 1.0 / l * 100);
                        if(percent > 99 ) {
                            percent = 99;
                        }
                        sendDownloadProgress(MEDIAFILE_DOWNLOAD_STAGE.DOWNLOAD_FROM_DRONE, percent, file);
                    }

                    @Override
                    public void onProgress(long l, long l1) {
                        //MQTTService.getInstance().publishTopic(MApplication.LOG_TAG, ("L: " + l + ":" + l1).getBytes());
                        //HSCloudBridge.getInstance().sendDebug("L: " + l + ":" + l1);
                    }

                    @Override
                    public void onSuccess(String s) {
                        HSCloudBridge.getInstance().sendDebug("Success");
                        sendDownloadProgress(MEDIAFILE_DOWNLOAD_STAGE.DOWNLOAD_FROM_DRONE, 100, file);
                        sendFileInformation(index, file);
                        //new Thread(fileTransferSession).start();
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
    int     fileIndex;
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

    public void handleCommand(msg_command_int msg) {
        switch (msg.command) {
            case MAV_CMD_SET_STORAGE_LOCATION:
                handleSetStorageLocation(msg);
                break;
            case MAV_CMD_REQUEST_STORAGE_FORMAT:
                handleStorageFormat(msg);
                break;
        }
    }

    private void handleStorageFormat(msg_command_int msg) {
        Camera camera = MApplication.getCameraInstance();

        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        SettingsDefinitions.StorageLocation location;

        if(msg.param1 == CAMERA_STORAGE_LOCATION.SDCARD) {
            location = SettingsDefinitions.StorageLocation.SDCARD;
        } else if(msg.param1 == CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE) {
            location = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
        } else {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.formatStorage(location, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_REQUEST_STORAGE_FORMAT, (short) MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

    private void handleSetStorageLocation(msg_command_int msg) {
        Camera camera = MApplication.getCameraInstance();

        if(camera == null) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if(!camera.isConnected()) {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        SettingsDefinitions.StorageLocation location;

        if(msg.param1 == CAMERA_STORAGE_LOCATION.SDCARD) {
            location = SettingsDefinitions.StorageLocation.SDCARD;
        } else if(msg.param1 == CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE) {
            location = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
        } else {
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_ACCEPTED);

        camera.setStorageLocation(location, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD_SET_STORAGE_LOCATION, (short) MAV_RESULT.MAV_RESULT_FAILED);
                    MavlinkHub.getInstance().sendText(djiError.getDescription());
                }
            }
        });
    }

    public void handleMavlinkMessage(MAVLinkPacket packet) {
        switch (packet.msgid) {
            case MAVLINK_MSG_ID_MEDIAFILE_REQUEST_LIST:
                handleMediafileRequestList(packet);
                break;
            case MAVLINK_MSG_ID_MEDIAFILE_REQUEST:
                handleMediafileRequest(packet);
                break;
        }
    }

    private void handleMediafileRequestList(MAVLinkPacket packet) {
        msg_mediafile_request_list msg = (msg_mediafile_request_list)packet.unpack();

        final SettingsDefinitions.StorageLocation location;

        if(msg.storage_location == CAMERA_STORAGE_LOCATION.SDCARD) {
            location = SettingsDefinitions.StorageLocation.SDCARD;
        } else {
            location = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
        }

        Camera camera = MApplication.getCameraInstance();
        if(camera == null) {
            HSCloudBridge.getInstance().sendDebug("NO Camera");
            return;
        }
        if(!camera.isConnected()) {
            HSCloudBridge.getInstance().sendDebug("NO Camera");
            return;
        }

        final MediaManager mediaManager = camera.getMediaManager();
        mediaManager.refreshFileListOfStorageLocation(location, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    msg_mediafile_count msg = new msg_mediafile_count();
                    if(location == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
                        INFiles.clear();
                        INFiles = mediaManager.getInternalStorageFileListSnapshot();
                        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
                        msg.count = INFiles.size();
                    } else {
                        SDFiles.clear();
                        SDFiles = mediaManager.getSDCardFileListSnapshot();
                        msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;
                        msg.count = SDFiles.size();
                    }
                    HSCloudBridge.getInstance().sendDebug(msg.toString());
                    MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
                } else {
                    msg_mediafile_count  msg = new msg_mediafile_count();
                    if(location == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
                        INFiles.clear();
                        msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
                        msg.count = 0;
                    } else {
                        SDFiles.clear();
                        msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;
                        msg.count = 0;
                    }
                    HSCloudBridge.getInstance().sendDebug(msg.toString());
                    MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
                }
            }
        });
    }

    private void handleMediafileRequest(MAVLinkPacket packet) {
        msg_mediafile_request msg = (msg_mediafile_request)packet.unpack();

        MediaFile file;
        if(msg.storage_location == CAMERA_STORAGE_LOCATION.SDCARD) {
            if(msg.index >= SDFiles.size()) {
                return;
            }
            file = SDFiles.get(msg.index);
        } else {
            if(msg.index >= INFiles.size()) {
                return;
            }
            file = INFiles.get(msg.index);
        }

        if(file == null) {
            return;
        }

        switch (msg.request_type) {
            case MEDIAFILE_REQUEST_TYPE.INFORMATION:
                sendFileInformation(msg.index, file);
                break;
            case MEDIAFILE_REQUEST_TYPE.THUMBNAIL:
                fetchThumbnail(msg.index, file, msg.storage_location, msg.request_type);
                break;
            case MEDIAFILE_REQUEST_TYPE.PREVIEW:
                HSCloudBridge.getInstance().sendDebug("Preview");
                fetchPreview(msg.index, file, msg.storage_location,msg.request_type);
                break;
            case MEDIAFILE_REQUEST_TYPE.RAW:
                //fileIndex = msg.index;
                //fileLocation = msg.storage_location;
                //fileSize = file.getFileSize();

                fetchRawFile(msg.index, file);
                break;
            case MEDIAFILE_REQUEST_TYPE.PAGE:
                fetchRawFilePage(msg.index, msg.page_index, file);
                break;
        }
    }

    private void fetchRawFilePage(int index, long page, MediaFile file) {
        if (index != fileIndex) {
            HSCloudBridge.getInstance().sendDebug("File index mismatch");
            return;
        }

        File handle = new File(filePath, fileName);

        if(page >= getPageCount(file)) {
            HSCloudBridge.getInstance().sendDebug("Over flow");
            return;
        }

        if (handle.exists()) {
            HSCloudBridge.getInstance().sendDebug("Start Transfer");

            int bufSize = 1024 * 1000;

            if(page == getPageCount(file) - 1)
                bufSize = (int)(file.getFileSize() - 1024000 * page);

            byte[] buf = new byte[bufSize];
            int readSize;

            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(filePath + File.separator + fileName, "r");
                randomAccessFile.seek(1024000 * page);
                readSize = randomAccessFile.read(buf);

                HSVideoFeeder.getInstance().mediaFileFeed(buf);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendFileInformation(int index, MediaFile file) {
        msg_mediafile_information msg = new msg_mediafile_information();

        msg.setFile_Name(file.getFileName());
        msg.setCreated_At(file.getDateCreated());
        msg.file_size = file.getFileSize();
        SettingsDefinitions.StorageLocation location = file.getStorageLocation();
        if(location.equals(SettingsDefinitions.StorageLocation.SDCARD)) {
            msg.storage_location = CAMERA_STORAGE_LOCATION.SDCARD;
        } else if(location.equals(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE)) {
            msg.storage_location = CAMERA_STORAGE_LOCATION.INTERNAL_STORAGE;
        } else {
            msg.storage_location = CAMERA_STORAGE_LOCATION.UNKNOWN;
        }

        msg.index = index;
        HSCloudBridge.getInstance().sendDebug("index: " + file.getIndex());

        if(file.getMediaType().equals(MediaFile.MediaType.JPEG)) {
            msg.file_type = MEDIATYPE.JPEG;
        } else {
            msg.file_type = MEDIATYPE.MP4;
        }

        msg.total_page = getPageCount(file);

        MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
        HSCloudBridge.getInstance().sendDebug(msg.toString());
    }

    int getPageCount(MediaFile file) {
        if(file == null)
            return 0;
        else {
            long fileSize = file.getFileSize();

            if(fileSize % 1024000 == 0) {
                return (int)fileSize / 1024000;
            } else {
                return (int)fileSize / 1024000 + 1;
            }
        }
    }

    private void sendSegments(int index, short locaiton, short type, byte[] data) {
        int size = data.length;

        msg_mediafile_data_segment msg = new msg_mediafile_data_segment();
        msg.index = index;
        msg.storage_location = locaiton;
        msg.request_type = type;

        if(size <= 200) {
            msg.total = 1;
            msg.seq = 0;
            msg.len = size;

            System.arraycopy(data, 0, msg.data, 0, msg.len);

            MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
            HSCloudBridge.getInstance().sendDebug(msg.toString());
        } else {
            int count, seq;

            if(size%200 > 0) {
                count = size / 200 + 1;
            } else {
                count = size / 200;
            }

            for(seq = 0; seq < count; seq++) {
                msg.total = count;
                msg.seq = seq;

                if(seq == count-1) {
                    msg.len = size - 200 * seq;
                } else {
                    msg.len = 200;
                }

                System.arraycopy(data, seq*200, msg.data, 0, msg.len);

                //MAVLinkPacket packet = msg.pack();
                MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
                HSCloudBridge.getInstance().sendDebug(msg.toString());
            }

            //MavLinkBridge.getInstance().sendMavlinkPacket(msg.pack());
        }
    }

    private void fetchThumbnail(final int index, final MediaFile file, final short location, final short type) {
        file.fetchThumbnail(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    Bitmap bitmap = file.getThumbnail();
                    ByteArrayOutputStream buf = null;
                    try{
                        buf = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, buf );

                        byte[] data = buf.toByteArray();

                        sendSegments(index, location, type, data);

                        try {
                            buf.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } finally {
                        try {
                            if(buf != null)
                                buf.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private void fetchPreview(final int index, final MediaFile file, final short location, final short type) {
        file.fetchPreview(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {
                    Bitmap bitmap = file.getPreview();
                    ByteArrayOutputStream buf = null;
                    try{
                        buf = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, buf );

                        byte[] data = buf.toByteArray();

                        sendSegments(index, location, type, data);

                        try {
                            buf.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } finally {
                        try {
                            if(buf != null)
                                buf.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private void sendDownloadProgress(int stage, int pct, MediaFile file) {
        msg_mediafile_download_progress msg = new msg_mediafile_download_progress();
        msg.setFile_Name(file.getFileName());
        msg.process = (short)stage;
        msg.percent = (short)pct;

        MavlinkHub.getInstance().sendMavlinkPacket(msg.pack());
        HSCloudBridge.getInstance().sendDebug(msg.toString());
    }
}
