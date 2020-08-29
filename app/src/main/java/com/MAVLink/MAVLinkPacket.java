/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by the
 * java mavlink generator tool. It should not be modified by hand.
 */
        
package com.MAVLink;

import java.io.Serializable;
import com.MAVLink.Messages.MAVLinkPayload;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.DLink.CRC;

import com.MAVLink.DLink.*;

/**
* Common interface for all MAVLink Messages
* Packet Anatomy
* This is the anatomy of one packet. It is inspired by the CAN and SAE AS-4 standards.

* Byte Index  Content              Value       Explanation
* 0            Packet start sign  v1.0: 0xFE   Indicates the start of a new packet.  (v0.9: 0x55)
* 1            Payload length      0 - 255     Indicates length of the following payload.
* 2            Packet sequence     0 - 255     Each component counts up his send sequence. Allows to detect packet loss
* 3            System ID           1 - 255     ID of the SENDING system. Allows to differentiate different MAVs on the same network.
* 4            Component ID        0 - 255     ID of the SENDING component. Allows to differentiate different components of the same system, e.g. the IMU and the autopilot.
* 5            Message ID          0 - 255     ID of the message - the id defines what the payload means and how it should be correctly decoded.
* 6 to (n+6)   Payload             0 - 255     Data of the message, depends on the message id.
* (n+7)to(n+8) Checksum (low byte, high byte)  ITU X.25/SAE AS-4 hash, excluding packet start sign, so bytes 1..(n+6) Note: The checksum also includes MAVLINK_CRC_EXTRA (Number computed from message fields. Protects the packet from decoding a different version of the same packet but with different variables).

* The checksum is the same as used in ITU X.25 and SAE AS-4 standards (CRC-16-CCITT), documented in SAE AS5669A. Please see the MAVLink source code for a documented C-implementation of it. LINK TO CHECKSUM
* The minimum packet length is 8 bytes for acknowledgement packets without payload
* The maximum packet length is 263 bytes for full payload
*
*/
public class MAVLinkPacket implements Serializable {
    private static final long serialVersionUID = 2095947771227815314L;

    public static final int MAVLINK_STX = 254;

    /**
    * Message length. NOT counting STX, LENGTH, SEQ, SYSID, COMPID, MSGID, CRC1 and CRC2
    */
    public final int len;

    /**
    * Message sequence
    */
    public int seq;

    /**
    * ID of the SENDING system. Allows to differentiate different MAVs on the
    * same network.
    */
    public int sysid;

    /**
    * ID of the SENDING component. Allows to differentiate different components
    * of the same system, e.g. the IMU and the autopilot.
    */
    public int compid;

    /**
    * ID of the message - the id defines what the payload means and how it
    * should be correctly decoded.
    */
    public int msgid;

    /**
    * Data of the message, depends on the message id.
    */
    public MAVLinkPayload payload;

    /**
    * ITU X.25/SAE AS-4 hash, excluding packet start sign, so bytes 1..(n+6)
    * Note: The checksum also includes MAVLINK_CRC_EXTRA (Number computed from
    * message fields. Protects the packet from decoding a different version of
    * the same packet but with different variables).
    */
    public CRC crc;

    public MAVLinkPacket(int payloadLength){
        len = payloadLength;
        payload = new MAVLinkPayload(payloadLength);
    }

    /**
    * Check if the size of the Payload is equal to the "len" byte
    */
    public boolean payloadIsFilled() {
        return payload.size() >= len;
    }

    /**
    * Update CRC for this packet.
    */
    public void generateCRC(){
        if(crc == null){
            crc = new CRC();
        }
        else{
            crc.start_checksum();
        }
        
        crc.update_checksum(len);
        crc.update_checksum(seq);
        crc.update_checksum(sysid);
        crc.update_checksum(compid);
        crc.update_checksum(msgid);

        payload.resetIndex();

        final int payloadSize = payload.size();
        for (int i = 0; i < payloadSize; i++) {
            crc.update_checksum(payload.getByte());
        }
        crc.finish_checksum(msgid);
    }

    /**
    * Encode this packet for transmission.
    *
    * @return Array with bytes to be transmitted
    */
    public byte[] encodePacket() {
        byte[] buffer = new byte[6 + len + 2];
        
        int i = 0;
        buffer[i++] = (byte) MAVLINK_STX;
        buffer[i++] = (byte) len;
        buffer[i++] = (byte) seq;
        buffer[i++] = (byte) sysid;
        buffer[i++] = (byte) compid;
        buffer[i++] = (byte) msgid;

        final int payloadSize = payload.size();
        for (int j = 0; j < payloadSize; j++) {
            buffer[i++] = payload.payload.get(j);
        }

        generateCRC();
        buffer[i++] = (byte) (crc.getLSB());
        buffer[i++] = (byte) (crc.getMSB());
        return buffer;
    }

    /**
    * Unpack the data in this packet and return a MAVLink message
    *
    * @return MAVLink message decoded from this packet
    */
    public MAVLinkMessage unpack() {
        switch (msgid) {
                         
            case msg_heartbeat.MAVLINK_MSG_ID_HEARTBEAT:
                return  new msg_heartbeat(this);
                 
            case msg_sys_status.MAVLINK_MSG_ID_SYS_STATUS:
                return  new msg_sys_status(this);
                 
            case msg_gps_raw_int.MAVLINK_MSG_ID_GPS_RAW_INT:
                return  new msg_gps_raw_int(this);
                 
            case msg_attitude.MAVLINK_MSG_ID_ATTITUDE:
                return  new msg_attitude(this);
                 
            case msg_global_position_int.MAVLINK_MSG_ID_GLOBAL_POSITION_INT:
                return  new msg_global_position_int(this);
                 
            case msg_mission_item.MAVLINK_MSG_ID_MISSION_ITEM:
                return  new msg_mission_item(this);
                 
            case msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST:
                return  new msg_mission_request(this);
                 
            case msg_mission_request_list.MAVLINK_MSG_ID_MISSION_REQUEST_LIST:
                return  new msg_mission_request_list(this);
                 
            case msg_mission_count.MAVLINK_MSG_ID_MISSION_COUNT:
                return  new msg_mission_count(this);
                 
            case msg_mission_ack.MAVLINK_MSG_ID_MISSION_ACK:
                return  new msg_mission_ack(this);
                 
            case msg_manual_control.MAVLINK_MSG_ID_MANUAL_CONTROL:
                return  new msg_manual_control(this);
                 
            case msg_rc_channels_override.MAVLINK_MSG_ID_RC_CHANNELS_OVERRIDE:
                return  new msg_rc_channels_override(this);
                 
            case msg_command_int.MAVLINK_MSG_ID_COMMAND_INT:
                return  new msg_command_int(this);
                 
            case msg_command_ack.MAVLINK_MSG_ID_COMMAND_ACK:
                return  new msg_command_ack(this);
                 
            case msg_radio_status.MAVLINK_MSG_ID_RADIO_STATUS:
                return  new msg_radio_status(this);
                 
            case msg_mediafile_request_list.MAVLINK_MSG_ID_MEDIAFILE_REQUEST_LIST:
                return  new msg_mediafile_request_list(this);
                 
            case msg_mediafile_count.MAVLINK_MSG_ID_MEDIAFILE_COUNT:
                return  new msg_mediafile_count(this);
                 
            case msg_mediafile_request.MAVLINK_MSG_ID_MEDIAFILE_REQUEST:
                return  new msg_mediafile_request(this);
                 
            case msg_mediafile_information.MAVLINK_MSG_ID_MEDIAFILE_INFORMATION:
                return  new msg_mediafile_information(this);
                 
            case msg_mediafile_data_segment.MAVLINK_MSG_ID_MEDIAFILE_DATA_SEGMENT:
                return  new msg_mediafile_data_segment(this);
                 
            case msg_storage_information.MAVLINK_MSG_ID_STORAGE_INFORMATION:
                return  new msg_storage_information(this);
                 
            case msg_camera_information.MAVLINK_MSG_ID_CAMERA_INFORMATION:
                return  new msg_camera_information(this);
                 
            case msg_camera_settings.MAVLINK_MSG_ID_CAMERA_SETTINGS:
                return  new msg_camera_settings(this);
                 
            case msg_camera_image_captured.MAVLINK_MSG_ID_CAMERA_IMAGE_CAPTURED:
                return  new msg_camera_image_captured(this);
                 
            case msg_camera_capture_status.MAVLINK_MSG_ID_CAMERA_CAPTURE_STATUS:
                return  new msg_camera_capture_status(this);
                 
            case msg_statustext.MAVLINK_MSG_ID_STATUSTEXT:
                return  new msg_statustext(this);
            
            
            default:
                return null;
        }
    }

}
        
        