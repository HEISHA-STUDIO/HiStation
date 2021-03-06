/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by the
 * java mavlink generator tool. It should not be modified by hand.
 */

// MESSAGE MEDIAFILE_COUNT PACKING
package com.MAVLink.DLink;
import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Messages.MAVLinkPayload;
        
/**
* The number of files of the SD card or the internal storage.
*/
public class msg_mediafile_count extends MAVLinkMessage{

    public static final int MAVLINK_MSG_ID_MEDIAFILE_COUNT = 151;
    public static final int MAVLINK_MSG_LENGTH = 3;
    private static final long serialVersionUID = MAVLINK_MSG_ID_MEDIAFILE_COUNT;


      
    /**
    * Count of mediafiles.
    */
    public int count;
      
    /**
    * The storage location, SD card or the internal storage.
    */
    public short storage_location;
    

    /**
    * Generates the payload for a mavlink message for a message of this type
    * @return
    */
    public MAVLinkPacket pack(){
        MAVLinkPacket packet = new MAVLinkPacket(MAVLINK_MSG_LENGTH);
        packet.sysid = 1;
        packet.compid = 1;
        packet.msgid = MAVLINK_MSG_ID_MEDIAFILE_COUNT;
              
        packet.payload.putUnsignedShort(count);
              
        packet.payload.putUnsignedByte(storage_location);
        
        return packet;
    }

    /**
    * Decode a mediafile_count message into this class fields
    *
    * @param payload The message to decode
    */
    public void unpack(MAVLinkPayload payload) {
        payload.resetIndex();
              
        this.count = payload.getUnsignedShort();
              
        this.storage_location = payload.getUnsignedByte();
        
    }

    /**
    * Constructor for a new message, just initializes the msgid
    */
    public msg_mediafile_count(){
        msgid = MAVLINK_MSG_ID_MEDIAFILE_COUNT;
    }

    /**
    * Constructor for a new message, initializes the message with the payload
    * from a mavlink packet
    *
    */
    public msg_mediafile_count(MAVLinkPacket mavLinkPacket){
        this.sysid = mavLinkPacket.sysid;
        this.compid = mavLinkPacket.compid;
        this.msgid = MAVLINK_MSG_ID_MEDIAFILE_COUNT;
        unpack(mavLinkPacket.payload);        
    }

        
    /**
    * Returns a string with the MSG name and data
    */
    public String toString(){
        return "MAVLINK_MSG_ID_MEDIAFILE_COUNT - sysid:"+sysid+" compid:"+compid+" count:"+count+" storage_location:"+storage_location+"";
    }
}
        