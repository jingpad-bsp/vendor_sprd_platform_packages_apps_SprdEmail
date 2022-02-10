/*
 * copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sprd.mail;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;
import android.widget.Toast;
import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;

/**
 * Activity that asks the user for all {@link #getDesiredPermissions} if any of
 * {@link #getRequiredPermissions} are missing.
 *
 * NOTE: As a result of b/22095159, this can behave oddly in the case where the final permission
 * you are requesting causes an application restart.
 */
public abstract class RequestPermissionsActivityBase extends Activity {
    public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";
    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;

    private Intent mPreviousActivityIntent;
    /* UNISOC: Modify for bug851496 {@ */
    private static Activity mActivity;
    private static final String BUGREPORT_MIMETYPE = "application/vnd.android.bugreport";
    /* @} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreviousActivityIntent = (Intent) getIntent().getExtras().get(PREVIOUS_ACTIVITY_INTENT);

        // Only start a requestPermissions() flow when first starting this activity the first time.
        // The process is likely to be restarted during the permission flow (necessary to enable
        // permissions) so this is important to track.
        if (savedInstanceState == null) {
            requestPermissions();
        }
    }

    /**
     * If any permissions the Email app needs are missing, open an Activity
     * to prompt the user for these permissions. Moreover, finish the current activity.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     */
    protected static boolean startPermissionActivity(Activity activity,
            String[] requiredPermissions, Class<?> newActivityClass) {
        if (!OsUtils.hasPermissions(activity, requiredPermissions)) {
            final Intent intent = new Intent(activity,  newActivityClass);
            intent.putExtra(PREVIOUS_ACTIVITY_INTENT, activity.getIntent());
            /* SPRD modify for bug616437 fd full issue, bug625716 permission toast issue */
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            /* @} */
            activity.startActivity(intent);
            /* UNISOC: Modify for bug851496 {@ */
            mActivity = activity;
            if (!BUGREPORT_MIMETYPE.equals(activity.getIntent().getType())) {
                activity.finish();
            }
            /* @} */
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        if (permissions != null && permissions.length > 0
                && OsUtils.isAllGranted(permissions, grantResults)) {
            if (mPreviousActivityIntent != null) {
                /* SPRD modify for bug516976, bug709634, bug709264 {@ */
                ClipData data = mPreviousActivityIntent.getClipData();
                String dataString = data == null ? "" : data.toString();
                String intentString = mPreviousActivityIntent.toString();
                if (data != null
                        &&
                        (dataString.contains("content://com.android.providers.downloads.documents")
                                || dataString
                                        .contains("content://com.android.externalstorage.documents")
                                || dataString
                                        .contains("content://com.android.providers.media.documents")
                                || dataString.contains("content://mms"))) {
                    mPreviousActivityIntent.setClipData(null);
                    mPreviousActivityIntent.replaceExtras((Bundle) null);
                    Toast.makeText(this, R.string.permission_change, Toast.LENGTH_LONG).show();
                    /* SPRD modify for bug524558 {@ */
                } else if (data == null
                        && intentString
                                .contains("com.android.email/.activity.setup.AccountSetupFinal")) {
                    Toast.makeText(this, R.string.permission_change_redo, Toast.LENGTH_LONG).show();
                } else if ((data == null
                        && intentString
                        .contains("com.android.email/com.android.mail.browse.EmlViewerActivity"))
                        || intentString.contains("com.sprd.mail.compose.ContactsPickerActivity")
                        || intentString.contains("com.sprd.mail.vip.activity.VipListActivity")) {
                    Toast.makeText(this, R.string.permission_change_redo, Toast.LENGTH_LONG).show();
                    finish();
                    overridePendingTransition(0, 0);
                    return;
                }
                /* @} */
                /* SPRD modify for bug516370 activity cannot entry */
                mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                startActivity(mPreviousActivityIntent);
            }
            finish();
            overridePendingTransition(0, 0);

        } else {
            Toast.makeText(this, R.string.missing_required_permission, Toast.LENGTH_SHORT).show();
            /* UNISOC: Modify for bug851496 {@ */
            if (mActivity != null && !mActivity.isDestroyed()
                    && BUGREPORT_MIMETYPE.equals(mActivity.getIntent().getType())) {
                mActivity.finish();
            }
            /* @} */
            finish();
        }
    }

    private void requestPermissions() {
        Trace.beginSection("requestPermissions");
        try {
            // Construct a list of missing permissions
            final ArrayList<String> unsatisfiedPermissions = new ArrayList<>();
            for (String permission : OsUtils.REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    unsatisfiedPermissions.add(permission);
                }
            }
            /* SPRD: Add for bug552436 @{ */
            if (unsatisfiedPermissions.size() == 0) {
                LogUtils.d(LogTag.getLogTag(),"Request permission activity was called even"
                        + " though all permissions are satisfied.");
                onRequestPermissionsResult(PERMISSIONS_REQUEST_ALL_PERMISSIONS, new String[]{OsUtils.REQUIRED_PERMISSIONS[0]},
                        new int[]{PackageManager.PERMISSION_GRANTED});
                return;
            }
            /* }@ */
            requestPermissions(
                    unsatisfiedPermissions.toArray(new String[unsatisfiedPermissions.size()]),
                    PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        /* SPRD: Add for bug577266 @{ */
        } catch (RuntimeException e) {
            LogUtils.d(LogTag.getLogTag(), e, "Couldn't request permissions");
        /* }@ */
        } finally {
            Trace.endSection();
        }
    }

}

