package com.wilson.histation;

public class MQTTVideoPacket {

    public static final int MQTT_BLOCK_SIZE = 1448;
    public static int packet_index = 0;
    public static int p_index = 0;
    public static long timeStamp = 0;
    public static long timeFirst = 0;

    public int  index;
    public int  part;
    public int  npart;
    public int  len;

    public byte[] data;

    public long pts;

    public MQTTVideoPacket() {
        data = new byte[MQTT_BLOCK_SIZE];
    }

    void pack(byte[] buf) {

        //System.arraycopy(index, 0, buf, 0, 4);
        buf[0] = (byte) (index & 0xFF);
        buf[1] = (byte) (index >> 8 & 0xFF);
        buf[2] = (byte) (index >> 16 & 0xFF);
        buf[3] = (byte) (index >> 24 & 0xFF);
        //System.arraycopy(part, 0, buf, 4, 4);
        buf[4] = (byte) (part & 0xFF);
        buf[5] = (byte) (part >> 8 & 0xFF);
        buf[6] = (byte) (part >> 16 & 0xFF);
        buf[7] = (byte) (part >> 24 & 0xFF);
        //System.arraycopy(npart, 0, buf, 8, 4);
        buf[8] = (byte) (npart & 0xFF);
        buf[9] = (byte) (npart >> 8 & 0xFF);
        buf[10] = (byte) (npart >> 16 & 0xFF);
        buf[11] = (byte) (npart >> 24 & 0xFF);
        //System.arraycopy(len, 0, buf, 12, 4);
        buf[12] = (byte) (len & 0xFF);
        buf[13] = (byte) (len >> 8 & 0xFF);
        buf[14] = (byte) (len >> 16 & 0xFF);
        buf[15] = (byte) (len >> 24 & 0xFF);
        System.arraycopy(data, 0, buf, 16, len);
        //System.arraycopy(pts, 0, buf, 16+len, 8);
        buf[16+len] = (byte) (pts & 0xFF);
        buf[16+len+1] = (byte) (pts >> 8 & 0xFF);
        buf[16+len+2] = (byte) (pts >> 16 & 0xFF);
        buf[16+len+3] = (byte) (pts >> 24 & 0xFF);
        buf[16+len+4] = (byte) (pts >> 32 & 0xFF);
        buf[16+len+5] = (byte) (pts >> 40 & 0xFF);
        buf[16+len+6] = (byte) (pts >> 48 & 0xFF);
        buf[16+len+7] = (byte) (pts >> 56 & 0xFF);
    }
}
