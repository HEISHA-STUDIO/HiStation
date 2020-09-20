package com.wilson.histation;

import com.heisha.HttpRequest.Device;
import com.heisha.HttpRequest.RequestCallback;
import com.heisha.MQTTConnect.MQTTManager;
import com.heisha.MQTTConnect.MessageHandlerCallBack;

class HSCloudBridge {
    private static final HSCloudBridge ourInstance = new HSCloudBridge();

    static HSCloudBridge getInstance() {
        return ourInstance;
    }

    private static final String MD5_SALT        = "heisha20190409";
    private static final String DEVICE_NAME     = DeviceUtil.getCPUSerial();
    private static final String DEVICE_KEY      = MD5Util.encryptSalt(DEVICE_NAME, MD5_SALT);
    private static final String HS_SERVER       = "http://118.190.91.165:65530/devcfg";
    Device device = null;
    boolean isConnected = false;

    MessageHandlerCallBack callBack = new MessageHandlerCallBack() {
        @Override
        public void MQTTConnectionComplete() {
            MApplication.LOG("MQTT connect");
            mqttManager.subscribe(device.getTopic() + "-2");
            mqttManager.subscribe(device.getTopic() + "-t");
        }

        @Override
        public void receiveMessageSuccess(String s, byte[] bytes) {
            if(s.equals(device.getTopic() + "-2")) {
                if(mavLinkListener != null) {
                    mavLinkListener.onMessage(bytes);
                }
            } else if(s.equals(device.getTopic() + "-t")) {
                if(testListener != null) {
                    testListener.onMessage(new String(bytes));
                }
            }

        }

        @Override
        public void sendMessageSuccess() {

        }

        @Override
        public void MQTTConnectionLost(Throwable throwable) {
            MApplication.LOG("MQTT disconnect");
        }
    };
    MQTTManager mqttManager = null;


    private HSCloudBridge() {
    }

    public void connect() {

        if(device == null) {
            device = new Device(DEVICE_NAME, DEVICE_KEY);
            MApplication.LOG(DEVICE_NAME);
        }

        device.request(HS_SERVER, new RequestCallback() {
            @Override
            public void onFinish() {
                String url = "tcp://" + device.getIp() + ":" + device.getPort();
                //MApplication.LOG(url);
                if(mqttManager == null) {
                    mqttManager = new MQTTManager(url);
                    mqttManager.setMessageHandlerCallBack(callBack);
                    mqttManager.connect(false, 20, 2);
                    isConnected = true;
                }
            }

            @Override
            public void onError() {
                MApplication.LOG("Error");
            }
        });
    }

    public void publicTopic(String topic, byte[] data) {
        if(mqttManager != null) {
            synchronized (mqttManager) {
                if (mqttManager.isConnected()) {
                    mqttManager.publish(topic, data, false, 0);
                }
            }
        }
    }

    public void sendDebug(String message) {
        if(device != null) {
            publicTopic(device.getTopic() + "-d", message.getBytes());
        }
    }

    public String   getTopic() {
        if(isConnected)
            return device.getTopic();
        else
            return null;
    }

    public interface MavLinkListener {
        void onMessage(byte[] data);
    }
    private MavLinkListener mavLinkListener = null;
    public void setMavLinkListener(MavLinkListener listener) {
        mavLinkListener = listener;
    }

    public interface TestListener {
        void onMessage(String msg);
    }
    private TestListener testListener = null;
    public void setTestListener(TestListener listener) { testListener = listener; }
}
