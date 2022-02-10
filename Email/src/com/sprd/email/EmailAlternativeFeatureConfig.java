
package com.sprd.email;

import android.content.Context;
import android.content.res.Resources;
import com.android.email.R;

public class EmailAlternativeFeatureConfig {

    private static boolean isExchageFixedInGuideSupport = false;
    private static boolean is139GuideSupport = false;
    /* UNISOC: Modify for bug1061370 {@ */
    private static boolean isAutoMailSupport = false;
    /* @} */

    public static void init(Context context) {
        Resources res = context.getResources();
        isExchageFixedInGuideSupport = res.getBoolean(R.bool.exchange_fixed_guide_support);
        is139GuideSupport = res.getBoolean(R.bool.guide_139_support);
        /* UNISOC: Modify for bug1061370 {@ */
        isAutoMailSupport = res.getBoolean(R.bool.auto_mail_support);
        /* @} */
    }

    public static boolean isExchangeFixedInGuideFeatureSupport() {
        return isExchageFixedInGuideSupport;
    }

    public static boolean is139GuideSupport() {
        return is139GuideSupport;
    }

    /* UNISOC: Modify for bug1061370 {@ */
    public static boolean isAutoMailSupport() {
        return isAutoMailSupport;
    }
    /* @} */
}
