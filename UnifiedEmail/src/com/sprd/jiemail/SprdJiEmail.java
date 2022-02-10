package com.sprd.jiemail;

import android.content.Intent;

import com.android.mail.utils.LogUtils;

import android.content.ComponentName;
import android.content.ActivityNotFoundException;

import android.app.Activity;

public class SprdJiEmail {

    private static final String TAG = "SprdJiEmail";

    static SprdJiEmail sInstance;

    private SprdJiEmail(){
    }

    public static SprdJiEmail getInstance(){
        if (sInstance == null) {
            sInstance = new SprdJiEmail();
        }
        LogUtils.d(TAG, "getInstance. sInstance = "+sInstance);
        return sInstance;
    }

    public void startJiEmailActivity(Activity mActivity) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        ComponentName componentName = new ComponentName("cn.richinfo.automail",
                "cn.richinfo.automail.ui.activity.TransparentActivity");
        intent.setComponent(componentName);
        if (intent.resolveActivity(mActivity.getPackageManager()) != null) {
            try {
                mActivity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                LogUtils.e(TAG, " no application can handle the URL: %s" + e);
            }
        }
    }
}
