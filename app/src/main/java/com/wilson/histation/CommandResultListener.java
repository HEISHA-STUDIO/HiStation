package com.wilson.histation;

public interface CommandResultListener {
    void accepted();
    void rejected(String reason);
    void completed();
    void failed(String reason);
}
