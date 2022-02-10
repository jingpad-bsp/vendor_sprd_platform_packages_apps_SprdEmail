
package com.sprd.drm;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.Uri;

import com.android.mail.R;
import com.android.mail.compose.AttachmentsView;
import com.android.mail.compose.AttachmentsView.AttachmentFailureException;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogUtils;
import com.android.mail.providers.Account;
import com.android.emailcommon.utility.AttachmentUtilities;
import android.drm.DrmStore;
import android.widget.Toast;

import android.os.Environment;
import android.drm.DrmManagerClient;
import android.os.SystemProperties;

public class EmailDrmUtils {
    private static final String TAG = "EmailDrmUtils";
    private static DrmManagerClient mDrmManagerClient;
    private static Object lock = new Object();
    private static EmailDrmUtils sInstance = null;//UNISOC: Modify for bug1208606

    public synchronized static EmailDrmUtils getInstance() {
        /* UNISOC: Modify for bug 1208606 {@ */
        if (sInstance == null) {
            sInstance = new EmailDrmUtils();
        }
        /* @}  */
        return sInstance;
    }

    private EmailDrmUtils() {
    }

    public boolean isSDFile(Context context, Uri uri) {
        boolean result = true;
        /* UNISOC: Modify for bug 1153358 {@ */
        String path = EmailUriUtil.getPath(context, uri);
        if (isDrmType(path, null)
                && !haveRightsForAction(path, DrmStore.Action.TRANSFER)) {
            LogUtils.d(TAG, "Enter ComposeActivity: isSDFile=false");
            Toast.makeText(context, context.getString(R.string.drm_protected_file),
                    Toast.LENGTH_LONG).show();
            result = false;
        }
         /* @}  */
        return result;
    }

    public boolean isDrmFile(String attName, String attType) {
        LogUtils.d(TAG, "Enter AttachmentTile: isDrmType=" + isDrmType(attName, attType));
        return isDrmType(attName, attType);
    }

    public boolean canOpenDrm(Context context, String attName, String attType) {
        LogUtils.d(TAG,
                "Enter MessageAttachmentBar: canOpenDrm =" + isDrmType(attName, attType));
        boolean result = true;
        if (isDrmType(attName, attType)) {
            Toast.makeText(context, context.getString(R.string.not_support_open_drm),
                    Toast.LENGTH_LONG).show();
            result = false;
        }
        return result;
    }

    public boolean drmPluginEnabled() {
        return true;
    }

    /* SPRD: Modify for bug513132 {@ */
    public long addAttachmentsXposed(final ComposeActivity composeActivity,
            final List<Attachment> attachments, final AttachmentsView mAttachmentsView,
            final Account mAccount) {
        long size = 0;
        AttachmentFailureException error = null;
        int drmNum = 0;
        final List<Attachment> mDrmMaybeForwardAttachments = new ArrayList<Attachment>();
        for (Attachment a : attachments) {
            if (isDrmType(a.getName(), a.getContentType())) {
                if (isAttachmentProviderUri(a.contentUri)) {
                    deleteFile(a.getName());
                    mDrmMaybeForwardAttachments.add(a);
                    continue;
                    /* SPRD:bug522288 modify begin @{ */
                } else if (isDrmType(a.contentUri)
                        && !haveRightsForAction(a.contentUri, DrmStore.Action.TRANSFER)) {
                    drmNum++;
                    continue;
                }
                /* @} */
            }
            try {
                size += mAttachmentsView.addAttachment(mAccount, a);
            } catch (AttachmentFailureException e) {
                error = e;
            }
        }

        if (mDrmMaybeForwardAttachments.size() > 0) {
            final Map<String, Object> data = new HashMap<String, Object>();
            data.put("size", size);
            data.put("drmNum", drmNum);
            data.put("completedCopyNum", 0);
            data.put("error", error);
            for (final Attachment a : mDrmMaybeForwardAttachments) {
                AttachmentUtilities.copyAttachmentFromInternalToExternal(composeActivity, a.uri,
                        a.contentUri, false, false,
                        new AttachmentUtilities.CopyAttachmentCallback() {
                            @Override
                            public void onCopyCompleted(String uri) {
                                data.put("completedCopyNum",
                                        ((int) data.get("completedCopyNum")) + 1);
                                /* SPRD:bug522288 modify begin @{ */
                                if (uri != null && isDrmType(Uri.parse(uri))
                                        && !haveRightsForAction(Uri.parse(uri),
                                                DrmStore.Action.TRANSFER)) {
                                    data.put("drmNum", ((int) data.get("drmNum")) + 1);
                                    /* @} */
                                } else {
                                    try {
                                        long size = mAttachmentsView.addAttachment(mAccount, a);
                                        data.put("size", ((long) data.get("size")) + size);
                                    } catch (AttachmentFailureException e) {
                                        data.put("error", e);
                                    }
                                }
                                if ((int) data.get(
                                        "completedCopyNum") == mDrmMaybeForwardAttachments.size()) {
                                    addAttachmentsToast((int) data.get("drmNum"), data.get("error"),
                                            composeActivity, attachments);
                                }
                            }
                        });
            }
        } else {
            addAttachmentsToast(drmNum, error, composeActivity, attachments);
        }
        return size;
    }

    private void addAttachmentsToast(int drmNum, Object error, ComposeActivity composeActivity,
            List<Attachment> attachments) {
        if (drmNum > 0) {
            composeActivity.showErrorToast(composeActivity.getString(R.string.not_add_drm_file));
        }

        if (error != null) {
            LogUtils.i(TAG, "Error adding attachment: " + error);
            if (attachments.size() > 1) {
                composeActivity.showAttachmentTooBigToast(
                        com.android.mail.R.string.too_large_to_attach_multiple);
            } else {
                composeActivity.showAttachmentTooBigToast(
                        ((AttachmentFailureException) error).getErrorRes());
            }
        }
    }

    private boolean deleteFile(String fileName) {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS + "/" + fileName);
        if (downloads.exists()) {
            return downloads.delete();
        }
        return false;
    }

    private static boolean isAttachmentProviderUri(Uri uri) {
        return "com.android.email.attachmentprovider".equals(uri.getAuthority());
    }

    /* @} */

    public boolean isDrmEnabled() {
        String prop = SystemProperties.get("drm.service.enabled");
        return prop != null && prop.equals("true");
    }

    public boolean isDrmType(Uri uri) {
        boolean result = false;
        if (mDrmManagerClient != null) {
            try {
                /* SPRD:bug522288 modify begin @{ */
                if (mDrmManagerClient.canHandle(uri, null) && mDrmManagerClient.getMetadata(uri) != null) {
                    result = true;
                }
                /* @} */
            } catch (IllegalArgumentException ex) {
                LogUtils.e(TAG, "canHandle called with wrong parameters %s \n", ex);
            } catch (IllegalStateException ex) {
                LogUtils.e(TAG, "DrmManagerClient didn't initialize properly %s \n", ex);
            /* SPRD:bug611315 modify begin @{ */
            } catch (UnsupportedOperationException ex) {
                LogUtils.e(TAG, "UnsupportedOperationException %s \n", ex);
            }
            /* @} */
        }
        return result;
    }

    public boolean isDrmType(String path, String mimeType) {
        boolean result = false;
        if (mDrmManagerClient != null) {
            try {
                if (mDrmManagerClient.canHandle(path, mimeType)) {
                    result = true;
                }
            } catch (IllegalArgumentException ex) {
                LogUtils.e(TAG, "canHandle called with wrong parameters %s \n", ex);
            } catch (IllegalStateException ex) {
                LogUtils.e(TAG, "DrmManagerClient didn't initialize properly %s \n", ex);
            } catch (UnsupportedOperationException ex) {
                LogUtils.e(TAG,"UnsupportedOperationException %s \n", ex);
            }
        }
        return result;
    }

    /* UNISOC: Modify for bug 1153358 {@ */
    public boolean haveRightsForAction(String path, int action) {
        boolean result = false;
        try {
            if (mDrmManagerClient.canHandle(path, null)) {
                result = (mDrmManagerClient.checkRightsStatus(path, action)
                        == DrmStore.RightsStatus.RIGHTS_VALID);
            }
        } catch (Exception e) {
            // Ignore exception and assume it is OK to forward file.
            LogUtils.e(TAG, "Exception happens in haveRightsForAction. %s \n", e);
        }
        return result;
    }
     /* @}  */

    public boolean haveRightsForAction(Uri uri, int action) {
        boolean result = false;
        try {
            if (mDrmManagerClient.canHandle(uri, null)) {
                result = (mDrmManagerClient.checkRightsStatus(uri, action)
                        == DrmStore.RightsStatus.RIGHTS_VALID);
            }
        } catch (Exception e) {
            // Ignore exception and assume it is OK to forward file.
            LogUtils.e(TAG, "Exception happens in haveRightsForAction. %s \n", e);
        }
        return result;
    }

    /* UNISOC: Modify for bug1179218 {@ */
    public void sendContext(Context context) {
        synchronized (lock) {
            if (mDrmManagerClient == null) {
                mDrmManagerClient = new DrmManagerClient(context.getApplicationContext());
            }
        }
    }
    /* @} */
}
