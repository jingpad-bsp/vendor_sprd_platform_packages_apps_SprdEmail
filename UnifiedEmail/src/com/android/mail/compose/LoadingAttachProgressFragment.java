
package com.android.mail.compose;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.android.email.R;
import com.android.mail.utils.LogUtils;

import com.android.mail.compose.ComposeActivity;
import com.android.mail.compose.AttachmentsView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.emailcommon.utility.EmailAsyncTask;
import com.sprd.drm.EmailDrmUtils;
import com.android.mail.analytics.Analytics;
import android.os.Environment;

/* SPRD: add  LoadingAttachProgressFragment for bug709539 @{ */
public class LoadingAttachProgressFragment extends Fragment {
    public static final String TAG = "LoadingAttachProgressFragment";
    public static final String URIMAP_KEY = "urimap_key";

    private ComposeActivity mActivity;
    private AttachmentsView mAttachmentsView;
    private LoadingAttachProgressTask mLoadingTask;
    private LoadingAttachProgressDialog mLoadingDialog;

    private HashMap<Integer, ArrayList<Uri>> mUriMap;

    public LoadingAttachProgressFragment() {
    }

    public LoadingAttachProgressFragment(HashMap<Integer, ArrayList<Uri>> uriMap) {
        mUriMap = uriMap;
    }

    public static LoadingAttachProgressFragment newInstance(
            HashMap<Integer, ArrayList<Uri>> uriMap) {
        final LoadingAttachProgressFragment fragment = new LoadingAttachProgressFragment(uriMap);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        // TODO Auto-generated method stub
        super.onAttach(activity);

        /* UNISOC: Modify for bug1230323 @{ */
        if (activity instanceof ComposeActivity) {
            mActivity = (ComposeActivity) activity;
        }
        /* @} */
        mAttachmentsView = mActivity.getAttachmentsView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onActivityCreated(savedInstanceState);

        if (mLoadingTask == null) {
            mLoadingTask = (LoadingAttachProgressTask) new LoadingAttachProgressTask()
                    .executeParallel();
        }
    }

    /* SPRD: Modify for bug855603 @{ */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtils.d(LogUtils.TAG, "LoadingAttach fragment onDestroy");

        dismissLoadingDialog();
        removeWaitingFragment();
    }
    /* @} */

    private void showLoadingDialog() {
        // Display a normal progress message
        FragmentManager fm = getFragmentManager();
        /* SPRD: Modify for bug702445 @{ */
        if (fm != null) {
            mLoadingDialog = (LoadingAttachProgressDialog) fm
                    .findFragmentByTag(LoadingAttachProgressDialog.TAG);
            if (mLoadingDialog == null) {
                mLoadingDialog = LoadingAttachProgressDialog.newInstance();
                fm.beginTransaction().add(mLoadingDialog, LoadingAttachProgressDialog.TAG)
                        .commitAllowingStateLoss();
            }
        }
        /* @} */
    }

    private void dismissLoadingDialog() {
        FragmentManager fm = getFragmentManager();
        /* SPRD: Modify for bug702445 @{ */
        if (fm != null) {
            mLoadingDialog = (LoadingAttachProgressDialog) fm
                    .findFragmentByTag(LoadingAttachProgressDialog.TAG);
            if (mLoadingDialog != null) {
                mLoadingDialog.dismissAllowingStateLoss();
                mLoadingDialog = null;
            }
        }
        /* @} */
    }

    private void removeWaitingFragment() {
        FragmentManager fm = getFragmentManager();
        /* SPRD: Modify for bug702445 @{ */
        if (fm != null) {
            Fragment WaitingFragment = fm.findFragmentByTag(TAG);
            if (WaitingFragment != null) {
                FragmentTransaction fragmentTransaction = fm.beginTransaction();
                if (fragmentTransaction != null && isResumed()) {
                    fragmentTransaction.remove(WaitingFragment);
                    fragmentTransaction.commitAllowingStateLoss();
                    fm.executePendingTransactions();
                }
            }
        }
        /* @} */
    }

    private class LoadingAttachProgressTask
            extends EmailAsyncTask<Void, Void, HashMap<Integer, List<Attachment>>> {

        public LoadingAttachProgressTask() {
            super(null);
        }

        @Override
        protected HashMap<Integer, List<Attachment>> doInBackground(Void... params) {
            final HashMap<Integer, ArrayList<Uri>> uriMaps = mUriMap;
            if (null == uriMaps || uriMaps.size() == 0) {
                return null;
            }

            showLoadingDialog();

            HashMap<Integer, List<Attachment>> attachmentsMap = new HashMap<Integer, List<Attachment>>();
            if (uriMaps.containsKey(ComposeActivity.TYPE_ADD)) {
                attachmentsMap.put(ComposeActivity.TYPE_ADD,
                        mActivity.handleAttachmentFromAdd(ComposeActivity.TYPE_ADD, uriMaps));
            } else {
                if (uriMaps.containsKey(ComposeActivity.TYPE_ATTACHMENTS)) {
                    attachmentsMap.put(ComposeActivity.TYPE_ATTACHMENTS,
                            mActivity.handleAttachmentsFromIntent(ComposeActivity.TYPE_ATTACHMENTS,
                                    uriMaps));
                }
                if (uriMaps.containsKey(ComposeActivity.TYPE_SEND_MULTIPLE)) {
                    attachmentsMap.put(ComposeActivity.TYPE_SEND_MULTIPLE,
                            mActivity.handleAttachmentsFromIntent(
                                    ComposeActivity.TYPE_SEND_MULTIPLE, uriMaps));
                } else if (uriMaps.containsKey(ComposeActivity.TYPE_SEND)) {
                    attachmentsMap.put(ComposeActivity.TYPE_SEND, mActivity
                            .handleAttachmentsFromIntent(ComposeActivity.TYPE_SEND, uriMaps));
                }
            }
            return attachmentsMap;
        }

        @Override
        protected void onCancelled(HashMap<Integer, List<Attachment>> attachmentsMap) {
            super.onCancelled(attachmentsMap);
            LogUtils.d(LogUtils.TAG, "load attachment cancell");

            dismissLoadingDialog();
            removeWaitingFragment();

            mActivity.setAttachmentsChanged(false);

            /* SPRD: Modify for bug 565076 {@ */
            mActivity.showErrorToast(getString(R.string.loaded_attachment_failed));
            /* @} */
            // close opend vcf fds avoid fd leakage.
            List<Attachment> attachments = null;
            if (mAttachmentsView != null) {
                // Judge whether attachments is null, avoid NPE
                if (null == attachmentsMap || attachmentsMap.size() == 0) {
                    return;
                }
                if (mUriMap.containsKey(ComposeActivity.TYPE_SEND)) {
                    attachments = attachmentsMap.get(ComposeActivity.TYPE_SEND);
                }
                if (attachments == null || attachments.size() == 0) {
                    return;
                }
            }
        }

        @Override
        protected void onSuccess(HashMap<Integer, List<Attachment>> attachmentsMap) {
            super.onSuccess(attachmentsMap);
            LogUtils.d(LogUtils.TAG, "load attachment success");

            dismissLoadingDialog();
            removeWaitingFragment();

            if (null == attachmentsMap || attachmentsMap.size() == 0) {
                return;
            }

            // Add attachments list to UI
            long totalSize = 0;
            if (attachmentsMap.containsKey(ComposeActivity.TYPE_ADD)) {
                totalSize = mActivity.addAttachments(attachmentsMap.get(ComposeActivity.TYPE_ADD));
                if (totalSize > 0) {
                    mActivity.setAttachmentsChanged(true);
                    mActivity.updateSaveUi();
                }
                return;
            } else {
                if (attachmentsMap.containsKey(ComposeActivity.TYPE_ATTACHMENTS)) {
                    totalSize += mActivity
                            .addAttachments(attachmentsMap.get(ComposeActivity.TYPE_ATTACHMENTS));
                }
                if (attachmentsMap.containsKey(ComposeActivity.TYPE_SEND_MULTIPLE)) {
                    totalSize = 0;
                    List<Attachment> attachments = attachmentsMap
                            .get(ComposeActivity.TYPE_SEND_MULTIPLE);
                    if (attachments != null && attachments.size() > 0) {
                        /* SPRD: modify for bug 520803 @{ */
                        if (EmailDrmUtils.getInstance().drmPluginEnabled()) {
                            totalSize += mActivity.addAttachmentsForDrm(attachments);
                        } else {
                            totalSize += mActivity.addAttachments(attachments);
                        }
                        /* }@ */
                    }

                } else if (attachmentsMap.containsKey(ComposeActivity.TYPE_SEND)) {
                    totalSize += mActivity
                            .addAttachments(attachmentsMap.get(ComposeActivity.TYPE_SEND));
                }
            }

            if (totalSize > 0) {
                mActivity.setAttachmentsChanged(true);
                mActivity.updateSaveUi();
                /* SPRD: Modify for bug 728394 @{ */
                if (mAttachmentsView != null) {
                    Analytics.getInstance().sendEvent("send_intent_with_attachments",
                            Integer.toString(mAttachmentsView.getAttachments().size()), null,
                            totalSize);
                }
                /* @} */
            }
        }
    }

    /**
     * Loading attachment Progress dialog
     */
    public static class LoadingAttachProgressDialog extends DialogFragment {
        public static final String TAG = "LoadingAttachProgressDialog";

        /**
         * Create a dialog for Loading attachment asynctask.
         */
        public LoadingAttachProgressDialog() {
            // TODO Auto-generated constructor stub
        }

        public static LoadingAttachProgressDialog newInstance() {
            final LoadingAttachProgressDialog dialog = new LoadingAttachProgressDialog();
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            ProgressDialog dialog = new ProgressDialog(context);
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.loading_attachment));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
    }
}
