package com.wilson.histation;

import android.text.TextUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5加密工具类，不可逆
 * Created by Song on 2017/2/22.
 */

public class MD5Util {

    private MD5Util() {
        throw new UnsupportedOperationException("constrontor cannot be init");
    }

    /**
     * 字符串加密
     * @param data 原字符串
     * @return 加密后新字符串
     */
    public static String encryptStr(String data) {

        byte[] dataBytes = data.getBytes();
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(dataBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] resultBytes = md5.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : resultBytes) {
            if(Integer.toHexString(0xFF & b).length() == 1) {
                sb.append("0").append(Integer.toHexString(0xFF & b));
            } else {
                sb.append(Integer.toHexString(0xFF & b));
            }
        }

        return sb.toString();
    }

    /**
     * MD5加盐
     *
     * 方式：
     *  1. string + key(盐值) 然后MD5加密
     *  2. 用string明文的hashcode作为盐，然后MD5加密
     *  3. 随机生成一串字符串作为盐值，然后MD5加密
     *
     * 该方法采用 string + key
     * @param data
     * @param salt
     * @return
     */
    public static String encryptSalt(String data, String salt) {

        if(TextUtils.isEmpty(data)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] resultBytes = md5.digest((data + salt).getBytes());
            for (byte b : resultBytes) {
                if(Integer.toHexString(0xFF & b).length() == 1) {
                    sb.append("0").append(Integer.toHexString(0xFF & b));
                } else {
                    sb.append(Integer.toHexString(0xFF & b));
                }
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
