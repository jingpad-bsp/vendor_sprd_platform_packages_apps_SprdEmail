
package com.sprd.mail.vip.activity;

import com.android.mail.R;
import com.android.emailcommon.provider.Account;

import android.app.ActionBar;
import android.app.Activity;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;

import android.content.Intent;
import android.database.ContentObserver;

import android.os.Bundle;
import android.os.Handler;

import com.sprd.mail.RequestPermissionsActivity;
/**
 * Sprd : Used as VipFragment container, and user can launch contact's activity to add new vip
 * member.
 */
public class VipListActivity extends Activity {
    public static final String TAG = "VIP_Settings/VipListActivity";
    public static final String ACCOUNT_ID = "accountId";
    /* SPRD Modify for bug876532 {@ */
    public static final String VIP_LIST_ACITON = "com.android.mail.intent.action.EMAIL_VIP_ACTIVITY";
    /* @} */
    public static final String VIP_LIST_FRAGMENT = "vip_list_fragment";

    private ActionBar mActionBar;
    private long mAccountId;
    /** SPRD: use for observer accounts deleted or not @{ */
    private ContentObserver mAccountObserver;

    /** @} */

    public static Intent createIntent(Context context, long accountId) {
        Intent i = new Intent();
        i.setAction(VIP_LIST_ACITON);
        i.putExtra(ACCOUNT_ID, accountId);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD: Modify for bug709264 @{ */
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        /* @} */

        setContentView(R.layout.email_vip_activity);

        Intent i = getIntent();
        mAccountId = i.getLongExtra(ACCOUNT_ID, -1);
        if (savedInstanceState == null && mAccountId != -1) {
            // First-time init; create fragment to embed in activity.
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment newFragment = VipListFragment.newInstance(mAccountId);
            ft.add(R.id.fragment_placeholder, newFragment, VIP_LIST_FRAGMENT);
            ft.commit();
        }
        mActionBar = getActionBar();
        // Configure action bar.
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        mActionBar.setDisplayShowCustomEnabled(true);
        /**
         * SPRD: When all accounts has been deleted, the vip list activity will be finished and
         * login page will be displayed. @{
         */
        if (mAccountObserver == null) {
            mAccountObserver = new AccountContentObserver(null, this);
        }
        getContentResolver().registerContentObserver(Account.NOTIFIER_URI, true, mAccountObserver);
        /** @} */
    }

    @Override
    protected void onDestroy() {
        if (mAccountObserver != null) {
            getContentResolver().unregisterContentObserver(mAccountObserver);
            mAccountObserver = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        VipListFragment f = (VipListFragment) getFragmentManager().findFragmentByTag(
                VIP_LIST_FRAGMENT);

        if (f == null || !f.handleBackKey()) {
            super.onBackPressed();
        }
    }

    /**
     * SPRD: Observer invoked whenever account is deleted.
     */
    private class AccountContentObserver extends ContentObserver {
        private final Context mContext;

        public AccountContentObserver(Handler handler, Context context) {
            super(handler);
            mContext = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            int count = Account.count(mContext, Account.CONTENT_URI);
            if (count == 0) {
                VipListActivity.this.finish();
            }
        }
    }
}
