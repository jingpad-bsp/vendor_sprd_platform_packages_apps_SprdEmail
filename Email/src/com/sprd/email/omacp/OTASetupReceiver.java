package com.sprd.email.omacp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
/**
 * The broadcast receiver.
 */
public class OTASetupReceiver extends BroadcastReceiver {
    public static final String OMACP_SETTING_ACTION = "com.android.EmailDataConfig";
    public static int RECEIVE_FLAG_EMAIL = 0x00000002;

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Log.d("OTASetupReceiver", " ***  receive:" + action + "*** ");

        if (OMACP_SETTING_ACTION.equals(action)) {
            Intent omacpIntent = new Intent(context, EmailOmacpService.class);
            omacpIntent.putExtra(Intent.EXTRA_INTENT, intent);
            context.startForegroundService(omacpIntent); //UNISOC: Modify for bug 1146879
            setResultCode(RECEIVE_FLAG_EMAIL);
        }
    }
}
