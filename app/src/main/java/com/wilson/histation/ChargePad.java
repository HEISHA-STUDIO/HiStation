package com.wilson.histation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.MAVLink.DLink.msg_command_int;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_RESULT;

class ChargePad {
    public static final byte BAR_STATUS_UNKNOWN = 0;
    public static final byte BAR_STATUS_UNLOCKED = 1;
    public static final byte BAR_STATUS_LOCKED = 2;
    public static final byte CHARGE_STATUS_UNCHARGE= 0;
    public static final byte CHARGE_STATUS_STARTING = 1;
    public static final byte CHARGE_STATUS_CHARGING_FAST = 2;
    public static final byte CHARGE_STATUS_CHARGING_SLOW = 3;
    public static final byte CHARGE_STATUS_COMPLETE = 4;

    public byte barStatus = BAR_STATUS_UNKNOWN;
    private byte chargeStatus = CHARGE_STATUS_UNCHARGE;
    private int chargeCurrent = 0;
    private int chargeVoltage = 0;
    private boolean bLive = true;
    private long lastUpdatedTimeUs = 0;

    private static final ChargePad ourInstance = new ChargePad();

    static ChargePad getInstance() {
        return ourInstance;
    }

    private SerialPortService serialPortService = null;

    private ChargePad() {
        Intent intent = new Intent(MApplication.app.getApplicationContext(), SerialPortService.class);
        MApplication.app.getApplicationContext().bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serialPortService = ((SerialPortService.CustomBinder)service).getService();

                serialPortService.setOnDataCallback(new SerialPortService.OnDataCallback() {
                    @Override
                    public void onDataReceived(byte[] data, int len) {
                        //Log.i(MApplication.LOG_TAG, "DATA: " + len);
                        handleSerialData(data, len);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void handleSerialData(byte[] data, int len) {
        //HSCloudBridge.getInstance().sendDebug("Data: " + len);

        for(int i=0; i<len; i++) {
            int temp = 0xFF & data[i];
            decoder(temp);
        }

        if(isLive()) {
            //HSCloudBridge.getInstance().sendDebug("Chargepad: " + barStatus + " " + chargeStatus + " " + chargeVoltage + " " + chargeCurrent);
        }
    }

    private int decoder_state = 0;
    private int[] decoder_buffer = new int[13];
    private int decoder_receive = 0;

    private void decoder(int data) {
        switch (decoder_state) {
            case 0:
                if(data == 0x55) {
                    decoder_state = 1;
                    decoder_buffer[0] = data;
                    decoder_receive = 1;
                }
                break;
            case 1:
                if(data == 0xAA) {
                    decoder_state = 2;
                    decoder_receive = 2;
                    decoder_buffer[1] = data;
                } else {
                    HSCloudBridge.getInstance().sendDebug("Head Error");
                    MavlinkHub.getInstance().sendText("ChargePad Head Error");
                    decoder_state = 0;
                    decoder_receive = 0;
                }
                break;
            case 2:
                decoder_buffer[decoder_receive++] = data;
                if(decoder_receive == 13) {
                    int check = 0;
                    for(int j=0; j<12; j++) {
                        check += decoder_buffer[j];
                    }

                    if((0xFF&check) == (int)(0xFF & decoder_buffer[12])) {
                        chargeStatus = (byte)decoder_buffer[4];
                        barStatus = (byte)decoder_buffer[3];
                        chargeVoltage = decoder_buffer[8] * 256 + decoder_buffer[7];
                        chargeCurrent = decoder_buffer[10] * 256 + decoder_buffer[9];
                        lastUpdatedTimeUs = System.currentTimeMillis();
                        bLive = true;
                    } else {
                        HSCloudBridge.getInstance().sendDebug("Check Error");
                        MavlinkHub.getInstance().sendText("ChargePad Check Error");
                    }
                    decoder_receive = 0;
                    decoder_state = 0;
                }
                break;

        }
    }

    private void sendData(byte[] buf) {
        if (serialPortService != null) {
            serialPortService.sendSerialPort(buf, buf.length);
        }
    }

    public byte getBarStatus() {
        return barStatus;
    }
    public byte getChargeStatus() {
        return chargeStatus;
    }
    public boolean isLive() {

        if((System.currentTimeMillis()-lastUpdatedTimeUs) > 5000) {
            bLive = false;
        }
        return bLive;
    }
    public int getChargeCurrent() {
        return chargeCurrent;
    }
    public int getChargeVoltage() {
        return chargeVoltage;
    }

    public void lock() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x04;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x03;

        sendData(data);
    }

    public void unlock() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x05;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x04;

        sendData(data);
    }

    public void turn_on_off_rc() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x03;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x02;

        sendData(data);
    }

    public void turn_on_off_drone() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x02;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x01;

        sendData(data);
    }

    public void openCanapy() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x0B;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x0A;

        sendData(data);
    }

    public void closeCanapy() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x0A;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x09;

        sendData(data);
    }

    public void charge() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x06;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x05;

        sendData(data);
    }

    public void unCharge() {
        byte[] data = new byte[8];

        data[0] = (byte)0x55;
        data[1] = (byte)0xAA;
        data[2] = (byte)0x07;
        data[3] = (byte)0x00;
        data[4] = (byte)0x00;
        data[5] = (byte)0x00;
        data[6] = (byte)0x00;
        data[7] = (byte)0x06;

        sendData(data);
    }

    public void handCommand(msg_command_int cmd) {
        switch (cmd.command) {
            case MAV_CMD.MAV_CMD_PAD_LOCK:
                handPadLock(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_UNLOCK:
                handPadUnLock(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_ON_RC:
                handTurnOnRC(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC:
                handTurnOffRC(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE:
                handTurnOnDrone(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE:
                handTurnOffDrone(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN:
                handOpenCanapy(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE:
                handCloseCanapy(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE:
                handTurnOnCharge(null);
                break;
            case MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE:
                handTurnOffCharge(null);
                break;
        }
    }

    public void handPadLock(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if (barStatus == BAR_STATUS_LOCKED) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
            else
                listener.completed();
            MApplication.commandBusy = false;
            return;
        }

        lock();

        new Thread(new Runnable() {
            @Override
            public void run() {
                long timeStart = System.currentTimeMillis();

                while(true) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if(ChargePad.getInstance().getBarStatus() == ChargePad.BAR_STATUS_LOCKED) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }
                        break;
                    }

                    if(System.currentTimeMillis() - timeStart > 15000) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_LOCK, (short)MAV_RESULT.MAV_RESULT_FAILED);
                        } else {
                            listener.failed("Timeout");
                        }
                        break;
                    }
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handPadUnLock(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if (barStatus == BAR_STATUS_UNLOCKED) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
            else
                listener.completed();
            MApplication.commandBusy = false;
            return;
        }

        unlock();

        new Thread(new Runnable() {
            @Override
            public void run() {
                long timeStart = System.currentTimeMillis();

                while(true) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if(ChargePad.getInstance().getBarStatus() == ChargePad.BAR_STATUS_UNLOCKED) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }
                        break;
                    }

                    if(System.currentTimeMillis() - timeStart > 15000) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_UNLOCK, (short)MAV_RESULT.MAV_RESULT_FAILED);
                        } else {
                            listener.failed("Timeout");
                        }
                        break;
                    }
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handTurnOnRC(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_DENIED);
            } else {
                listener.rejected("Busy");
            }
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if(RemoteControllerProxy.getInstance().isLive()) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
            } else {
                listener.completed();
            }
            MApplication.commandBusy = false;
            return;
        }

        turn_on_off_rc();

        new Thread(new Runnable() {
            @Override
            public void run() {
                long timeStart = System.currentTimeMillis();

                while(true) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if(RemoteControllerProxy.getInstance().isLive()) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }
                        break;
                    }

                    if(System.currentTimeMillis() - timeStart > 40000) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_RC, (short)MAV_RESULT.MAV_RESULT_FAILED);
                        } else {
                            listener.failed("Timeout");
                        }
                        break;
                    }
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handTurnOffRC(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_DENIED);
            } else {
                listener.rejected("Busy");
            }
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if(!RemoteControllerProxy.getInstance().isLive()) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
            } else {
                listener.completed();
            }
            MApplication.commandBusy = false;
            return;
        }

        turn_on_off_rc();

        new Thread(new Runnable() {
            @Override
            public void run() {
                long timeStart = System.currentTimeMillis();

                while(true) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if(!RemoteControllerProxy.getInstance().isLive()) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }
                        break;
                    }

                    if(System.currentTimeMillis() - timeStart > 15000) {
                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_RC, (short)MAV_RESULT.MAV_RESULT_FAILED);
                        } else {
                            listener.failed("Timeout");
                        }
                        break;
                    }
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handTurnOnDrone(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            } else {
                listener.rejected("Busy");
            }
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if(!RemoteControllerProxy.getInstance().isLive()) {
            if(FlightControllerProxy.getInstance().getExpectedOn()) {
                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.accepted();
                }
                MApplication.commandBusy = false;
                return;
            } else {
                turn_on_off_drone();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        FlightControllerProxy.getInstance().setExpectedOn(true);

                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }

                        MApplication.commandBusy = false;
                    }
                }).start();
            }
        } else {
            if(FlightControllerProxy.getInstance().isLive()) {
                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }
                MApplication.commandBusy = false;
                return;
            }

            turn_on_off_drone();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    long timeStart = System.currentTimeMillis();

                    while(true) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        FlightControllerProxy.getInstance().setExpectedOn(true);

                        if(FlightControllerProxy.getInstance().isLive()) {
                            if(listener == null) {
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                            } else {
                                listener.completed();
                            }
                            break;
                        }

                        if(System.currentTimeMillis() - timeStart > 30000) {
                            if(listener == null) {
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_DRONE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            } else {
                                listener.failed("Timeout");
                            }
                            break;
                        }
                    }

                    MApplication.commandBusy = false;
                }
            }).start();
        }
    }

    public void handTurnOffDrone(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null) {
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            } else {
                listener.rejected("Busy");
            }
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        if(!RemoteControllerProxy.getInstance().isLive()) {
            if(!FlightControllerProxy.getInstance().getExpectedOn()) {
                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }
                MApplication.commandBusy = false;
                return;
            } else {
                turn_on_off_drone();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        FlightControllerProxy.getInstance().setExpectedOn(false);

                        if(listener == null) {
                            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                        } else {
                            listener.completed();
                        }

                        MApplication.commandBusy = false;
                    }
                }).start();
            }
        } else {
            if(!FlightControllerProxy.getInstance().isLive()) {
                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }
                MApplication.commandBusy = false;
                return;
            }

            turn_on_off_drone();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    long timeStart = System.currentTimeMillis();

                    while(true) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        FlightControllerProxy.getInstance().setExpectedOn(false);

                        if(!FlightControllerProxy.getInstance().isLive()) {
                            if(listener == null) {
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                            } else {
                                listener.completed();
                            }
                            break;
                        }

                        if(System.currentTimeMillis() - timeStart > 30000) {
                            if(listener == null) {
                                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_DRONE, (short)MAV_RESULT.MAV_RESULT_FAILED);
                            } else {
                                listener.failed("Timeout");
                            }
                            break;
                        }
                    }

                    MApplication.commandBusy = false;
                }
            }).start();
        }
    }

    public void handOpenCanapy(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        openCanapy();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_OPEN, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handCloseCanapy(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        closeCanapy();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_CANOPY_CLOSE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handTurnOnCharge(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        charge();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_ON_CHARGE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }

    public void handTurnOffCharge(final CommandResultListener listener) {
        if(MApplication.commandBusy) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Busy");
            return;
        }
        MApplication.commandBusy = true;

        if(!isLive()) {
            if(listener == null)
                MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE, (short)MAV_RESULT.MAV_RESULT_DENIED);
            else
                listener.rejected("Charge pad not detected");
            MApplication.commandBusy = false;
            return;
        }

        if(listener == null)
            MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE, (short)MAV_RESULT.MAV_RESULT_ACCEPTED);
        else
            listener.accepted();

        unCharge();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if(listener == null) {
                    MavlinkHub.getInstance().sendCommandAck(MAV_CMD.MAV_CMD_PAD_TURN_OFF_CHARGE, (short)MAV_RESULT.MAV_RESULT_SUCCESS);
                } else {
                    listener.completed();
                }

                MApplication.commandBusy = false;
            }
        }).start();
    }
}
