package com.sprd.outofoffice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.email.R;
import com.android.email.service.EmailServiceUtils;
import com.android.mail.utils.LogUtils;

import com.android.emailcommon.mail.ServerCommandInfo;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.service.EmailServiceProxy;
import com.android.emailcommon.service.OofParams;
import com.android.emailcommon.utility.EmailAsyncTask;
import com.android.emailcommon.utility.Utility;

public class OofGetWaitingFragment extends Fragment {
    public static final String TAG = "OofGetWaitingFragment";
    public static final String BUNDLE_KEY = "OofGetWaitingFragment.accountId";

    private final static long DEFAULT_ID = -1;
    // State
    private final static int STATE_START = Integer.MIN_VALUE;
    private int mState = STATE_START;

    // Support for UI
    public boolean mAttached;
    private boolean mPaused;
    private WaitingSaveOofDialog mCheckingDialog;
    private Account mAccount;
    private Context mContext;
    private long mAccountId = DEFAULT_ID;
    private AlertDialog mAlertDialog = null;
    /* SPRD: modify for bug 706136 @{ */
    private Handler mHandler;
    private AccountObserver mObserver;
    /* @} */

    // Support for AsyncTask and account checking
    OofTask mOofCheckTask;
    OofParams mOofParams;

    public OofGetWaitingFragment() {
        super();
    }

    public OofGetWaitingFragment(long accountId) {
        mAccountId = accountId;
    }

    /**
     * Create a retained, invisible fragment that checks accounts
     *
     * @param mode incoming or outgoing
     */
    public static OofGetWaitingFragment newInstance(long accountId, Fragment parentFragment) {
        OofGetWaitingFragment f = new OofGetWaitingFragment(accountId);
        f.setTargetFragment(parentFragment, 0);
        return f;
    }

    /**
     * Fragment initialization.  Because we never implement onCreateView, and call
     * setRetainInstance here, this creates an invisible, persistent, "worker" fragment.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        if (savedInstanceState != null) {
            mAccountId = savedInstanceState.getLong(BUNDLE_KEY);
        }
        /* SPRD: modify for bug 706136 @{ */
        mHandler = new Handler();
        mObserver = new AccountObserver(mHandler);
        /* @} */
    }
    /* SPRD: modify for bug 706136 @{ */
    class AccountObserver extends ContentObserver {
        public AccountObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    checkAccountValidity();
                }
            }).start();
        }
    }

    private void checkAccountValidity() {
        mAccount = Account.restoreAccountWithId(mContext, mAccountId);
        if (mAccount == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.finish();
                    }
                }
            });
        }
    }
    /* @} */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong(BUNDLE_KEY, mAccountId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    /**
     * This is called when the Fragment's Activity is ready to go, after
     * its content view has been installed; it is called both after
     * the initial fragment creation and after the fragment is re-attached
     * to a new activity.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAttached = true;
        /* SPRD: modify for bug 706316 @{ */
        mContext = getActivity();
        /* @} */

        // If this is the first time, start the AsyncTask
        if (mOofCheckTask == null) {
            mOofCheckTask = (OofTask)
                    new OofTask().executeParallel();
            LogUtils.d(TAG, this + " ### onActivityCreated  #### "
                    + "  " + mOofCheckTask);
        }
    }

    /**
     * When resuming, restart the progress/error UI if necessary by re-reporting previous values
     */
    @Override
    public void onResume() {
        super.onResume();
        mPaused = false;
        if (mState != STATE_START) {
            reportProgress(mState);
        }
        /* SPRD: modify for bug 706136 @{ */
        if (mAccount != null && mContext != null ) {
            mContext.getContentResolver().registerContentObserver(mAccount.getUri(), true,
                    mObserver);
        }
        /* @} */
    }

    @Override
    public void onPause() {
        mPaused = true;
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        super.onPause();
        /* SPRD: modify for bug 706136 @{ */
        if (mAccount != null && mContext != null ) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
        /* @} */
    }

    /**
     * This is called when the fragment is going away.  It is NOT called
     * when the fragment is being propagated between activity instances.
     */
    @Override
    public void onDestroy() {
        Utility.cancelTaskInterrupt(mOofCheckTask);
        mOofCheckTask = null;
        super.onDestroy();
    }

    /**
     * This is called right before the fragment is detached from its current activity instance.
     * All reporting and callbacks are halted until we reattach.
     */
    @Override
    public void onDetach() {
        /* SPRD: Modify for bug839458 @{ */
        recoverAndDismissCheckingDialog();
        /* @} */
        super.onDetach();
        mAttached = false;
    }

    private void setupWaitingDialog(FragmentManager fm) {
        // Display a normal progress message
        mCheckingDialog = (WaitingSaveOofDialog) fm.findFragmentByTag(WaitingSaveOofDialog.TAG);
        if (mCheckingDialog == null) {
            mCheckingDialog = WaitingSaveOofDialog.newInstance(this);
            fm.beginTransaction()
                    .add(mCheckingDialog, WaitingSaveOofDialog.TAG)
                    .commitAllowingStateLoss();
        }
    }
    /**
     * The worker (AsyncTask) will call this (in the UI thread) to report progress.  If we are
     * attached to an activity, update the progress immediately;  If not, simply hold the
     * progress for later.
     * @param newState The new progress state being reported
     */
    private void reportProgress(int newState) {
        mState = newState;
        // If we are attached, create, recover, and/or update the dialog
        if (mAttached && !mPaused) {
            final FragmentManager fm = getFragmentManager();
            switch (newState) {
                case STATE_START:
                    setupWaitingDialog(fm);
                    break;
                // we just catch network error at here, if server not support Oof or something else,
                // alert when save Oof.
                case ServerCommandInfo.OofInfo.NETWORK_SHUT_DOWN:
                   /* if (UiUtilities.isWifiOnly(mContext)) {
                            showAlertDialog(fm, R.string.unable_to_connect,
                                R.string.need_wifi_connection_prompt);
                    } else {*/
                        showAlertDialog(fm, R.string.unable_to_connect,
                            R.string.need_connection_prompt);
                    //}
                    break;
                case ServerCommandInfo.OofInfo.SYNC_OOF_UNINITIALIZED:
                    // Account may not initialized
                    showAlertDialog(fm, R.string.account_settings_oof_get_status_fail,
                            R.string.account_settings_oof_get_status_fail_summary);
                    break;
                default:
                    if (mOofParams != null) {
                        int oofState = mOofParams.getOofState();
                        if (oofState < 0) {
                            showAlertDialog(fm, R.string.not_support_oof,
                                    R.string.need_switch_oof_prompt);
                            return;
                        }
                    }
                    // immediately terminate, clean up, and report back
                    // 1. get rid of progress dialog (if any)
                    recoverAndDismissCheckingDialog();
                    // 2. report OK back to target fragment or activity
                    // If the fragment in background just save check_status and send call on onResume.
                    // or send the call back immediately.
                    startOofActivity();
                    // 3. exit self
                    /* SPRD: Modify for bug676340 @{
                     * fm.popBackStack();
                     */
                    removeWaitingFragment(fm);
                    /* @} */
                    break;
            }
        }
    }

    /**
     * Show the alert dialog when get status failed
     */
    private void showAlertDialog(final FragmentManager fm, int titeId, int messageId) {
        recoverAndDismissCheckingDialog();
        AlertDialog.Builder bld = new AlertDialog.Builder(mContext);
        bld.setTitle(titeId);
        bld.setMessage(messageId);
        bld.setPositiveButton(R.string.okay_action,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                    int which) {
                /* SPRD: Modify for bug676340 @{
                 * fm.popBackStack();
                 */
                removeWaitingFragment(fm);
                /* @} */
            }
        });
        bld.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                /* SPRD: Modify for bug676340 @{
                 * fm.popBackStack();
                 */
                removeWaitingFragment(fm);
                /* @} */
            }
        });
        mAlertDialog = bld.create();
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.show();
    }

    /* SPRD: Modify for bug676340 ,693606 @{ */
    private void removeWaitingFragment(final FragmentManager fm) {
        Fragment WaitingFragment = fm.findFragmentByTag(TAG);
        if (WaitingFragment != null) {
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            try{
                fragmentTransaction.remove(WaitingFragment);
                fragmentTransaction.commitAllowingStateLoss();
                fm.executePendingTransactions();
            } catch (IllegalStateException e) {
                // Log instead of crash
                LogUtils.e(TAG, e, "removeWaitingFragment:  java.lang.IllegalStateException: Activity has been destroyed");
            }
        }
    }
    /* @} */

    /**
     * Recover and dismiss the progress dialog fragment
     */
    private void recoverAndDismissCheckingDialog() {
        mCheckingDialog = (WaitingSaveOofDialog)
                    getFragmentManager().findFragmentByTag(WaitingSaveOofDialog.TAG);
        if (mCheckingDialog != null) {
            mCheckingDialog.dismissAllowingStateLoss();
            mCheckingDialog = null;
        }
    }

    /**
     * This is called when the user clicks "cancel" on the progress dialog.  Shuts everything
     * down and dismisses everything.
     * This should cause us to remain in the current screen (not accepting the settings)
     */
    private void onCheckingDialogCancel() {
        // 1. kill the checker
        Utility.cancelTaskInterrupt(mOofCheckTask);
        mOofCheckTask = null;
        // 2. kill self with no report - this is "cancel"
        Log.d(TAG, "onCheckingDialogCancel");

        finished();
    }

    /** Kill self if not already killed. */
    private void finished() {
        FragmentManager fm = getFragmentManager();
        if (fm == null && getTargetFragment() != null) {
            fm = getTargetFragment().getFragmentManager();
        }
        if (fm != null) {
            LogUtils.d(TAG, "Kill current fragment if not already killed (finish)");
            /* SPRD: Modify for bug676340 @{
             * fm.popBackStack(this.getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
             */
            removeWaitingFragment(fm);
            /* @} */
        }
    }

    /**
     * This AsyncTask does the actual account checking
     *
     * TODO: It would be better to remove the UI complete from here (the exception->string
     * conversions).
     */
    private void startOofActivity() {
        if (mAccount == null) {
            return;
        }
        mState = STATE_START;
        /* SPRD modify for bug687291{@ */
        Intent intent = OofSettingsActivity.createIntent(mContext,
                mAccount, mOofParams);
        /* @} */
        startActivity(intent);
    }
    /**
     * Sprd: Task for update out of office settings.
     *
     */
    private class OofTask extends EmailAsyncTask<Void, Void, Integer> {

        public OofTask() {
            super(null);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            reportProgress(STATE_START);

            /* SPRD: Modify for bug676340 avoid anr. @{ */
            mAccount = Account.restoreAccountWithId(mContext, mAccountId);
            /* @} */
            /* SPRD: Modify for bug706136  @{ */
            if(mAccount != null ){
                final EmailServiceProxy proxy = EmailServiceUtils
                        .getServiceForAccount(mContext, mAccount.mId);
                try {
                    mOofParams = proxy.syncOof(mAccount.mId, null, true);
                } catch (RemoteException e) {
                    Log.d(TAG, "syncOof is Error.");
                }
            }
            /* @} */
            if (mOofParams == null || mOofParams.getmStatus() > ServerCommandInfo.OofInfo.SET_OR_SAVE_SUCCESS) {
                Log.d(TAG, "get oof status faild. mOofParams = " + mOofParams);
                return ServerCommandInfo.OofInfo.SYNC_OOF_UNINITIALIZED;
            }
            return mOofParams.getmStatus();
        }

        @Override
        protected void onSuccess(Integer result) {
            mState = result;
            if (mAttached) {
                reportProgress(result);
            }
        }
    }

    /**
     * Sprd: DialogFragment for waiting update out of office settings.
     *
     */
    public static class WaitingSaveOofDialog extends DialogFragment {
        public static final String TAG = "WaitingSaveOofDialog";

        public static WaitingSaveOofDialog newInstance(OofGetWaitingFragment  parentFragment) {
            final WaitingSaveOofDialog dialog = new WaitingSaveOofDialog();
            dialog.setTargetFragment(parentFragment, 0);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final ProgressDialog progressDialog = new ProgressDialog(context);
            progressDialog.setIndeterminate(true);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(context
                            .getString(R.string.account_settings_oof_get_status)
                            + "...");
            return progressDialog;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            OofGetWaitingFragment  parentFragment = (OofGetWaitingFragment) getTargetFragment();
            parentFragment.onCheckingDialogCancel();
            super.onCancel(dialog);
        }
    }
}
