/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.content.Context;
import android.content.SharedPreferences;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.AlertDialog;

import com.android.emailcommon.utility.EmailAsyncTask;
import com.android.emailcommon.utility.Utility;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.google.common.annotations.VisibleForTesting;
import com.sprd.mail.RequestPermissionsActivity;
import com.sprd.mail.vip.VipMember;
import com.android.mail.utils.LogUtils;

import java.lang.ref.WeakReference;
import java.util.List;

public class MailPreferenceActivity extends PreferenceActivity {

    // SPRD:Delete account function from AccountSettings.
    private static final String LOG_TAG = "MailPreferenceActivity";

    public static final String PREFERENCE_FRAGMENT_ID = "preference_fragment_id";

    private static final int ACCOUNT_LOADER_ID = 0;
    private static final int VIP_LOADER_ID = 1;
    protected static final int VIP_HEAD_ID = 0x138815;

    private int mVipNumber = 0;
    private VipMemberCountObserver mCountObserver;

    // SPRD:Delete account function from AccountSettings.
    public static final String DELETE_EMAIL_ADDRESS = "delete_email";

    private WeakReference<GeneralPrefsFragment> mGeneralPrefsFragmentRef;

    private Cursor mAccountsCursor;

    /* SPRD: Modify for bug749153 @{ */
    private static Activity mLastActivity = null;
    /* @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD modify for bug729039{@ */
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        /* @} */

        /* SPRD: Modify for bug749153 @{ */
        if (ActivityManager.isUserAMonkey()) {
            if (mLastActivity != null) {
                mLastActivity.finish();
            }
            mLastActivity = this;
        }
        /* @} */

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Hide the app icon.
            actionBar.setIcon(android.R.color.transparent);
            actionBar.setDisplayUseLogoEnabled(false);
        }

        getLoaderManager().initLoader(ACCOUNT_LOADER_ID, null, new AccountLoaderCallbacks());
        registerVipCountObserver();
    }

    // SPRD:Delete account function from AccountSettings. @{
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences mSharedPreferences =
                this.getSharedPreferences(DELETE_EMAIL_ADDRESS, Context.MODE_PRIVATE);
        String mDeleteAccountAddress = mSharedPreferences.getString("delete_email_address", null);
        LogUtils.d(LOG_TAG,"mDeleteAccountAddress = " + mDeleteAccountAddress);

        if(mDeleteAccountAddress != null) {
            mSharedPreferences.edit().clear().apply();
            showCreateWaitingDialog();
        }
        if (this.getHeaders() != null) {
            this.invalidateHeaders();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterVipCountObserver();
    }

    private class VipMemberCountObserver extends ContentObserver {

        public VipMemberCountObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateVipMemberCount();
        }
    }

    private void unregisterVipCountObserver() {
        Context context = MailPreferenceActivity.this;
        if (context != null && mCountObserver != null) {
            context.getContentResolver().unregisterContentObserver(mCountObserver);
            mCountObserver = null;
        }
    }

    private void registerVipCountObserver() {
        Context context = MailPreferenceActivity.this;
        if (context != null && mCountObserver == null) {
            mCountObserver = new VipMemberCountObserver(Utility.getMainThreadHandler());
            context.getContentResolver().registerContentObserver(VipMember.NOTIFIER_URI, true,
                    mCountObserver);
            updateVipMemberCount();
        }
    }

    public static class CreateWaitingDialogFragment extends DialogFragment {
        public static final String TAG = "CreateWaitingDialogFragment";
        public CreateWaitingDialogFragment() {}

        public static CreateWaitingDialogFragment newInstance() {
            return new CreateWaitingDialogFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            /// Show "delete account waiting..." dialog
            Context context = getActivity();
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.account_delete_dlg_title)
                    .setMessage(context.getString(R.string.account_delete_waiting)).create();
        }
    }

    protected void showCreateWaitingDialog() {
        CreateWaitingDialogFragment.newInstance()
                .show(getFragmentManager(), CreateWaitingDialogFragment.TAG);
    }

    protected void dismissCreateWaitingDialog() {
        final DialogFragment f = (DialogFragment)
                getFragmentManager().findFragmentByTag(CreateWaitingDialogFragment.TAG);
        if (f != null) {
            f.dismissAllowingStateLoss();
        }
    }
    //@}

    private void updateVipMemberCount() {
        new EmailAsyncTask<Void, Void, Integer>(null) {
            private static final int ERROR_RESULT = -1;
            @Override
            protected Integer doInBackground(Void... params) {
                Context context = MailPreferenceActivity.this;
                if (context == null) {
                    return ERROR_RESULT;
                }

                return VipMember.countVipMembersWithAccountId(context,
                        com.android.emailcommon.provider.Account.ACCOUNT_ID_COMBINED_VIEW);
            }

            @Override
            protected void onSuccess(Integer result) {
                if (result != ERROR_RESULT) {
                    mVipNumber = result;
                    /* SPRD: Modify for bug709479 @{ */
                    invalidateHeaders();
                    /* @} */
                } else {
                    LogUtils.e(LogUtils.TAG, "Failed to get the count of the VIP member");
                }
            }
        }.executeParallel();
    }

    private class AccountLoaderCallbacks implements LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(MailPreferenceActivity.this,
                    MailAppProvider.getAccountsUri(), UIProvider.ACCOUNTS_PROJECTION,
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mAccountsCursor = data;
            invalidateHeaders();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mAccountsCursor = null;
            invalidateHeaders();
        }
    }

    @VisibleForTesting
    GeneralPrefsFragment getGeneralPrefsFragment() {
        return mGeneralPrefsFragmentRef != null ? mGeneralPrefsFragmentRef.get() : null;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof GeneralPrefsFragment) {
            mGeneralPrefsFragmentRef =
                    new WeakReference<GeneralPrefsFragment>((GeneralPrefsFragment) fragment);
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        // TODO: STOPSHIP fix Email to use the PublicPreferenceActivity trampoline
        return true;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);

        if (mVipNumber >= 0) {
            final Header header = new Header();
            header.title = this.getString(R.string.vip_members_count, mVipNumber);
            header.id = VIP_HEAD_ID;

            target.add(header);
        }

        if (mAccountsCursor != null && mAccountsCursor.moveToFirst()) {
            do {
                final Account account = Account.builder().buildFrom(mAccountsCursor);
                // TODO: This will no longer be needed when the Combined view is moved to Unified
                if (!account.supportsCapability(AccountCapabilities.VIRTUAL_ACCOUNT)) {
                    final Header header = new Header();
                    if (TextUtils.isEmpty(account.getDisplayName()) ||
                            TextUtils.equals(account.getDisplayName(), account.getEmailAddress())) {
                        // No (useful) display name, just use the email address
                        header.title = account.getEmailAddress();
                    } else {
                        header.title = account.getDisplayName();
                        header.summary = account.getEmailAddress();
                    }
                    header.fragment = account.settingsFragmentClass;
                    final Bundle accountBundle = new Bundle(1);
                    accountBundle.putString(MailAccountPrefsFragment.ARG_ACCOUNT_EMAIL,
                            account.getEmailAddress());
                    header.fragmentArguments = accountBundle;

                    target.add(header);
                }
            } while (mAccountsCursor.moveToNext());
        }
        // SPRD:Delete account function from AccountSettings.
        dismissCreateWaitingDialog();
        onBuildExtraHeaders(target);
    }

    /**
     * Override this in a subclass to add extra headers besides "General Settings" and accounts
     * @param target List of headers to mutate
     */
    public void onBuildExtraHeaders(List<Header> target) {
    }
}
