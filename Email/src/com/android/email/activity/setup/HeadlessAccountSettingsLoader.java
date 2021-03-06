package com.android.email.activity.setup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.utility.IntentUtilities;
import com.android.mail.utils.LogUtils;

/**
 * This activity is headless. It exists to load the Account object from  the given account ID and
 * then starts the {@link AccountServerSettingsActivity} activity with the incoming/outgoing
 * settings fragment
 */
public class HeadlessAccountSettingsLoader extends Activity {

    public static Uri getOutgoingSettingsUri(long accountId) {
        final Uri.Builder baseUri = Uri.parse("auth://" + EmailContent.EMAIL_PACKAGE_NAME +
                ".ACCOUNT_SETTINGS/outgoing/").buildUpon();
        IntentUtilities.setAccountId(baseUri, accountId);
        return baseUri.build();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent i = getIntent();
        final long accountID = IntentUtilities.getAccountIdFromIntent(i);
        /* SPRD: Add for bug492895{ */
        if (i.getData() == null) {
            LogUtils.i(LogUtils.TAG, "Invalid data: Uri is null!");
            finish();
            return;
        }
        /* @} */
        if (savedInstanceState == null) {
            new LoadAccountIncomingSettingsAsyncTask(getApplicationContext(),
                    "incoming".equals(i.getData().getLastPathSegment()))
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, accountID);
        }
    }

    /* SPRD: Add for bug516426{ */
    @Override
    protected void onStart() {
        super.onStart();
        // workaround for M,
        // reference https://code.google.com/p/android-developer-preview/issues/detail?id=2353
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setVisible(true);
        }
    }
    /* @} */

    /**
     * Asynchronously loads the Account object from its ID and then navigates to the AccountSettings
     * fragment.
     */
    private class LoadAccountIncomingSettingsAsyncTask extends AsyncTask<Long, Void, Account> {
        private final Context mContext;
        private final boolean mIncoming;

        private LoadAccountIncomingSettingsAsyncTask(Context context, boolean incoming) {
            mContext = context;
            mIncoming = incoming;
        }

        protected Account doInBackground(Long... params) {
            return Account.restoreAccountWithId(mContext, params[0]);
        }

        protected void onPostExecute(Account result) {
            // create an Intent to view a new activity
            final Intent intent;
            if (mIncoming) {
                intent = AccountServerSettingsActivity.getIntentForIncoming(mContext, result);
            } else {
                intent = AccountServerSettingsActivity.getIntentForOutgoing(mContext, result);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            mContext.startActivity(intent);

            finish();
         }
    }
}
