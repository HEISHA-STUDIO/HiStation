package com.wilson.histation;

import dji.sdk.gimbal.Gimbal;

class GimbaProxy {
    private static final GimbaProxy ourInstance = new GimbaProxy();

    static GimbaProxy getInstance() {
        return ourInstance;
    }

    private GimbaProxy() {

    }

}
