package com.wilson.histation;

import com.MAVLink.enums.VIDEO_STREAMING_SOURCE;

import java.util.ArrayList;

class HSVideoFeeder {
    private static final HSVideoFeeder ourInstance = new HSVideoFeeder();

    static HSVideoFeeder getInstance() {
        return ourInstance;
    }

    public int videoSource  = VIDEO_STREAMING_SOURCE.UNKNOWN;
    private AvcEncoder encoder;
    ArrayList<byte[]> encDataList = new ArrayList<byte[]>();

    Runnable senderRun = new Runnable() {
        @Override
        public void run() {
            while (true) {
                boolean empty = false;
                byte[] encData = null;

                synchronized (encDataList) {
                    if(encDataList.size() == 0) {
                        empty = true;
                    } else {
                        encData = encDataList.remove(0);
                    }
                }

                if(empty) {
                    try{
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                //HSCloudBridge.getInstance().sendDebug("Video");
                if(HSCloudBridge.getInstance().isConnected) {
                    //MApplication.LOG("T3: " + encData.length);
                    publishMQTTTopic(encData, encData.length);
                }
                /*try {
                    DatagramPacket packet = new DatagramPacket(encData, encData.length, address, port);
                    udpSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
            }
        }
    };

    private HSVideoFeeder() {
        encoder = new AvcEncoder();
        encoder.init(640,480,30,500000);

        Thread thrd = new Thread(senderRun);
        thrd.start();
    }

    public void feedVideo(byte[] data) {
        if(videoSource != VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_DRONE_CAMERA) {
            return;
        }

        if(encDataList.size() > 100) {
            return;
        }

        if(data.length > 0) {
            synchronized (encDataList) {
                encDataList.add(data);
            }
        }
    }

    public void T3Encode(byte[] data) {
        if(videoSource != VIDEO_STREAMING_SOURCE.VIDEO_STREAMING_T3_CAMERA) {
            return;
        }

        if(encDataList.size() > 100) {
            return;
        }

        byte[] encData = encoder.offerEncoder(data);
        if(encData.length > 0) {
            synchronized (encDataList) {
                encDataList.add(encData);
            }
        }
    }

    public void publishMQTTTopic(byte[] data, int len) {

        MQTTVideoPacket.packet_index++;
        MQTTVideoPacket.p_index = 0;
        MQTTVideoPacket.timeStamp = System.currentTimeMillis();

        if(len <= MQTTVideoPacket.MQTT_BLOCK_SIZE) {
            MQTTVideoPacket packet = new MQTTVideoPacket();
            packet.index = MQTTVideoPacket.packet_index;
            packet.part = 1;
            packet.npart = MQTTVideoPacket.p_index++;
            packet.len = len;
            System.arraycopy(data, 0, packet.data, 0, len);
            packet.pts = MQTTVideoPacket.timeStamp - MQTTVideoPacket.timeFirst;

            byte[] buf = new byte[24 + packet.len];
            packet.pack(buf);
            HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic(), buf);
            //HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic(), "VIDEO".getBytes());
        } else {
            int n, i;

            if(len%MQTTVideoPacket.MQTT_BLOCK_SIZE > 0) {
                n = len / MQTTVideoPacket.MQTT_BLOCK_SIZE + 1;
            } else {
                n = len / MQTTVideoPacket.MQTT_BLOCK_SIZE;
            }

            for (i=0;i<n;i++) {
                MQTTVideoPacket packet = new MQTTVideoPacket();
                packet.index = MQTTVideoPacket.packet_index;
                packet.part = n;
                packet.npart = MQTTVideoPacket.p_index++;
                packet.pts = MQTTVideoPacket.timeStamp - MQTTVideoPacket.timeFirst;

                if(i == n-1) {
                    packet.len = len - i*MQTTVideoPacket.MQTT_BLOCK_SIZE;
                    System.arraycopy(data, i*MQTTVideoPacket.MQTT_BLOCK_SIZE, packet.data, 0, packet.len);
                } else {
                    packet.len = MQTTVideoPacket.MQTT_BLOCK_SIZE;
                    System.arraycopy(data, i*MQTTVideoPacket.MQTT_BLOCK_SIZE, packet.data, 0, packet.len);
                }

                byte[] buf = new byte[24 + packet.len];
                packet.pack(buf);
                HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic(), buf);
                //HSCloudBridge.getInstance().publicTopic(HSCloudBridge.getInstance().getTopic(), "VIDEO".getBytes());
            }
        }
    }
}
