package com.wilson.histation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction() == Intent.ACTION_BOOT_COMPLETED) {
            Intent startIntent = new Intent(context, MainActivity.class);
            context.startActivity(startIntent);
        }
    }
}
