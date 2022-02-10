package com.sprd.outofoffice;

import com.android.emailcommon.service.EmailServiceProxy;
import com.android.emailcommon.service.OofParams;
import com.android.emailcommon.utility.IntentUtilities;
import com.android.emailcommon.provider.Account;
import com.android.email.R;
import com.android.email.service.EmailServiceUtils;
import com.sprd.mail.RequestPermissionsActivity;
import com.sprd.outofoffice.AccountSettingsOutOfOfficeFragment.Callback;
import com.sprd.outofoffice.AutoReplyDialogFragment.OnAddAutoReplyCallback;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.view.MenuItem;

public class OofSettingsActivity extends Activity implements Callback, OnAddAutoReplyCallback {
    public static final String FRAGMENT_TAG = "AccountSettingsOutOfOfficeFragment";
    /* SPRD Modify for bug876532 {@ */
    public static final String OOF_SETTING_ACTION = "com.android.mail.intent.action.OOF_SETTING_ACTION";
    /* @} */
    /* SPRD modify for bug687291{@ */
    private static final String ACCOUNT = "account";
    /* @} */
    private static final String OOF_PARAMS = "oof_params";

    /* SPRD modify for bug702771{@ */
    private static final String CANCLE_DIALOG = "OofSettingsActivity_Calcle_Dialog";
    /* @} */

    /* SPRD modify for bug687291{@ */
    private Account mAccount;
    /* @} */
    private ActionBar mActionBar;
    public boolean mOkPressed;

    /* SPRD modify for bug693941{@ */
    private Handler mBackgroundHandler = null;
    private HandlerThread mBackgroudThread;
    private Handler mHandler = new Handler();
    /* @} */

    @Override
    public void onSettingFinished() {
        onBackPressed();
    }

    /* SPRD modify for bug687291{@ */
    @Override
    public Account getAccount() {
        return mAccount;
    }
    /* @} */

    /* SPRD: Modify for bug 562472{@ */
    @Override
    public void onAddAutoReplyFinished(String replySumm) {
        AccountSettingsOutOfOfficeFragment fragment = (AccountSettingsOutOfOfficeFragment)
                getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment != null && isResumed()) {
            fragment.setDisplayAutoReplySumm(replySumm);
        }
    }
    /* @} */

    /* SPRD modify for bug687291{@ */
    public static Intent createIntent(Context context, Account account, Parcelable oofParams) {
        Intent i = new Intent();
        i.setAction(OOF_SETTING_ACTION);
        i.putExtra(ACCOUNT, account);
        i.putExtra(OOF_PARAMS, oofParams);
        return i;
    }
    /* @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD modify for bug659595 {@ */
        if (IntentUtilities.isIntentFromMailware(getIntent())) {
            finish();
            return;
        }
        /* @} */

        /* SPRD: Modify for bug738666 @{ */
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        /* @} */

        setContentView(R.layout.oof_settings_activity);

        Intent i = getIntent();
        /* SPRD modify for bug687291{@ */
        if (savedInstanceState == null) {
            mAccount = i.getParcelableExtra(ACCOUNT);
            OofParams oofParams = i.getParcelableExtra(OOF_PARAMS);
            if (mAccount != null && oofParams != null) {
                // First-time init; create fragment to embed in activity.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                Fragment newFragment = AccountSettingsOutOfOfficeFragment.newInstance(oofParams);
                ft.add(R.id.fragment_holder, newFragment, FRAGMENT_TAG);
                ft.commit();
            }
        } else {
            mAccount = savedInstanceState.getParcelable(ACCOUNT);
        }

        if (mAccount == null) {
            finish();
            return;
        }
        /* @} */

        mActionBar = getActionBar();
        // Configure action bar.
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        mActionBar.setDisplayShowTitleEnabled(true);
        mActionBar.setTitle(R.string.account_settings_oof_label);
        mBackgroudThread = new HandlerThread("Backgroud_Thread") {
            protected void onLooperPrepared() {
                mBackgroundHandler = new Handler(mBackgroudThread.getLooper());
            };
        };
        mBackgroudThread.start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // The app icon on the action bar is pressed.  Just emulate a back press.
            // TODO: this should navigate to the main screen, even if a sub-setting is open.
            // But we shouldn't just finish(), as we want to show "discard changes?" dialog
            // when necessary.
            onBackPressed();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        /* SPRD modify for bug687291, bug693941 bug702771{@ */
        if (mBackgroundHandler == null) {
            return;
        }

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                long accountId = mAccount.getId();
                final EmailServiceProxy proxy = EmailServiceUtils
                        .getServiceForAccount(OofSettingsActivity.this, accountId);
                try {
                    proxy.stopOof(accountId);
                } catch (RemoteException e) {
                    Log.d("OofSettingsActivity", "stopOof Error");
                }

                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        AccountSettingsOutOfOfficeFragment fragment = (AccountSettingsOutOfOfficeFragment)
                                getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
                        /* SPRD Modify for bug637322 {@ */
                        boolean isEnabled = fragment != null && fragment.getSaveButtonStatus();
                        /* @} */

                        if (isEnabled && !mOkPressed) {
                            FragmentManager fm = getFragmentManager();
                            AlertDialogFragment adf = (AlertDialogFragment) fm
                                    .findFragmentByTag(CANCLE_DIALOG);
                            if (adf == null) {
                                adf = AlertDialogFragment.newInstance(
                                        R.string.account_settings_oof_cancel,
                                        R.string.account_settings_oof_cancel_summary);
                                /* SPRD: Modify for bug 748603  @{ */
                                adf.showAllowingStateLoss(getFragmentManager(),
                                        AccountSettingsOutOfOfficeFragment.CANCEL_ALERT_TAG);
                                /* @} */
                            }
                        } else {
                            finish();
                        }
                    }
                });
            }
        });
        /* @} */
    }

    public static class AlertDialogFragment extends DialogFragment {
        private static final String TITLE_ID = "titleId";
        private static final String MSG_ID = "messageId";

        private int mTitleId;
        private int mMessageId;

        public AlertDialogFragment() {
        }

        public AlertDialogFragment(int titleId, int messageId) {
            super();
            this.mTitleId = titleId;
            this.mMessageId = messageId;
        }

        public static AlertDialogFragment newInstance(int titleId, int messageId) {
            final AlertDialogFragment dialog = new AlertDialogFragment(titleId, messageId);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
            if (savedInstanceState != null) {
                mTitleId = savedInstanceState.getInt(TITLE_ID);
                mMessageId = savedInstanceState.getInt(MSG_ID);
            }
            dialogBuilder.setTitle(mTitleId)
            .setMessage(mMessageId)
            .setPositiveButton(R.string.okay_action,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    OofSettingsActivity activity = (OofSettingsActivity) getActivity();
                    /* SPRD: Modify for 705724  {@ */
                    if (activity != null){
                        activity.mOkPressed = true;
                        activity.onBackPressed();
                    }
                   /* @} */
                }
            })
            .setNegativeButton(R.string.cancel_action,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            AlertDialog alertDialog = dialogBuilder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            return alertDialog;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(TITLE_ID, mTitleId);
            outState.putInt(MSG_ID, mMessageId);
            super.onSaveInstanceState(outState);
        }
    }

    /* SPRD modify for bug687291{@ */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ACCOUNT, mAccount);
    }
    /* @} */
}
