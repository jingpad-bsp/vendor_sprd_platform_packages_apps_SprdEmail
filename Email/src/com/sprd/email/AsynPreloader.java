
package com.sprd.email;

import android.content.Context;
import android.text.format.DateUtils;

public class AsynPreloader {
    public static void preload(final Context context) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                DateUtils.getRelativeTimeSpanString(context,
                        0);
            }
        }).start();
    }
}
