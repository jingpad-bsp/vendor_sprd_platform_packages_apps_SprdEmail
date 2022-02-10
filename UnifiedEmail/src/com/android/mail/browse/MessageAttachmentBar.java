/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.core.text.BidiFormatter;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.emailcommon.utility.AttachmentUtilities;
import com.android.emailcommon.utility.EmailAsyncTask;
import com.android.emailcommon.utility.Utility;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.EmailContent.AttachmentColumns;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.ui.AccountFeedbackActivity;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;
import com.android.mail.utils.Utils;
import com.sprd.drm.EmailDrmUtils;

import com.sprd.mail.DownloadConfirmDialogFragment.DownloadConfirmDialogType;
import com.sprd.mail.DownloadConfirmInterface;
import android.widget.Toast;

/**
 * View for a single attachment in conversation view. Shows download status and allows launching
 * intents to act on an attachment.
 *
 */
/* SPRD: common format attachment feature.
 * original code:
public class MessageAttachmentBar extends FrameLayout implements OnClickListener,
        OnMenuItemClickListener, AttachmentViewInterface {
 *@{
 */
public class MessageAttachmentBar extends FrameLayout implements OnClickListener,
        OnMenuItemClickListener, AttachmentViewInterface, DownloadConfirmInterface {
/*@}*/

    private Attachment mAttachment;
    private TextView mTitle;
    private TextView mSubTitle;
    private String mAttachmentSizeText;
    private String mDisplayType;
    private ProgressBar mProgress;
    private PopupMenu mPopup;
    private ImageView mOverflowButton;

    private final AttachmentActionHandler mActionHandler;
    private boolean mSaveClicked;
    private Account mAccount;

    /* SPRD: Modify for bug728500 @{ */
    private BidiFormatter mBidiFormatter;
    /* @} */

    /* SPRD: common format attachment feature.
     *@{
     */
    // Corresponding to DENY_MALWARE of AttachmentInfo
    public static final int FLAG_DENY_MALWARE = 1<<20;
    // Corresponding to DENY_WIFIONLY of AttachmentInfo
    public static final int FLAG_DENY_WIFIONLY = 1<<21;
    // Corresponding to DENY_NOINTENT of AttachmentInfo
    public static final int FLAG_DENY_NOINTENT = 1<<22;
    // Corresponding to DENY_NOSIDELOAD of AttachmentInfo
    public static final int FLAG_DENY_NOSIDELOAD = 1<<23;
    // Corresponding to DENY_APKINSTALL of AttachmentInfo
    public static final int FLAG_DENY_APKINSTALL = 1<<24;
    // Corresponding to DENY_POLICY of AttachmentInfo
    public static final int FLAG_DENY_POLICY = 1<<25;
    private final Context mContext;
    /*@}*/

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * Boolean used to tell whether extra option 1 should always be hidden.
     * Currently makes sure that there is no conversation because that state
     * means that we're in the EML viewer.
     */
    private boolean mHideExtraOptionOne;


    public MessageAttachmentBar(Context context) {
        this(context, null);
    }

    public MessageAttachmentBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        /* SPRD: common format attachment feature.
         *@{
         */
        mContext = context;
        /*@}*/
        mActionHandler = new AttachmentActionHandler(context, this);
    }

    public void initialize(FragmentManager fragmentManager) {
        mActionHandler.initialize(fragmentManager);
    }

    public static MessageAttachmentBar inflate(LayoutInflater inflater, ViewGroup parent) {
        MessageAttachmentBar view = (MessageAttachmentBar) inflater.inflate(
                R.layout.conversation_message_attachment_bar, parent, false);
        return view;
    }

    /**
     * Render or update an attachment's view. This happens immediately upon instantiation, and
     * repeatedly as status updates stream in, so only properties with new or changed values will
     * cause sub-views to update.
     */
    public void render(Attachment attachment, Account account, ConversationMessage message,
            boolean loaderResult, BidiFormatter bidiFormatter) {
        // get account uri for potential eml viewer usage
        mAccount = account;

        /* SPRD: Modify for bug728500 @{ */
        mBidiFormatter = bidiFormatter;
        /* @} */

        final Attachment prevAttachment = mAttachment;
        mAttachment = attachment;
        if (mAccount != null) {
            mActionHandler.setAccount(mAccount.getEmailAddress());
        }
        mActionHandler.setMessage(message);
        mActionHandler.setAttachment(mAttachment);
        mHideExtraOptionOne = message.getConversation() == null;

        // reset mSaveClicked if we are not currently downloading
        // So if the download fails or the download completes, we stop
        // showing progress, etc
        mSaveClicked = !attachment.isDownloading() ? false : mSaveClicked;

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s flags=%d", attachment.getName(), attachment.state,
                attachment.destination, attachment.downloadedSize, attachment.contentUri,
                attachment.getContentType(), attachment.flags);

        final String attachmentName = attachment.getName();
        if ((attachment.flags & Attachment.FLAG_DUMMY_ATTACHMENT) != 0) {
            mTitle.setText(R.string.load_attachment);
        } else if (prevAttachment == null
                || !TextUtils.equals(attachmentName, prevAttachment.getName())) {
            mTitle.setText(attachmentName);
        }

        if (prevAttachment == null || attachment.size != prevAttachment.size) {
            mAttachmentSizeText = bidiFormatter.unicodeWrap(
                    AttachmentUtils.convertToHumanReadableSize(getContext(), attachment.size));
            mDisplayType = bidiFormatter.unicodeWrap(
                    AttachmentUtils.getDisplayType(getContext(), attachment));
            updateSubtitleText();
        }

        /*SPRD: Modify for bug744520, origin bug629474 bug625452 illegal filename issue {@*/
        if ((attachment.flags & Attachment.FLAG_RENAMED_TO_LEGAE) != 0
                && attachment.state == AttachmentState.SAVED) {

            EmailAsyncTask<Void, Void, Void> task = new EmailAsyncTask<Void, Void, Void>(null) {

                protected Void doInBackground(Void... params) {
                    long id = Long.parseLong(attachment.uri.getLastPathSegment());
                    EmailContent.Attachment att = EmailContent.Attachment
                            .restoreAttachmentWithId(mContext, id);

                    if (att != null && (att.mFlags & Attachment.FLAG_RENAMED_TO_LEGAE) != 0) {
                        ContentValues cv = new ContentValues();
                        Uri uri = ContentUris.withAppendedId(EmailContent.Attachment.CONTENT_URI,
                                id);
                        cv.put(AttachmentColumns.FLAGS,
                                attachment.flags & (~Attachment.FLAG_RENAMED_TO_LEGAE));
                        mContext.getContentResolver().update(uri, cv, null, null);

                    }
                    return null;
                }

                protected void onSuccess(Void result) {
                    Toast.makeText(mContext, R.string.attachment_rename_to_legal,
                            Toast.LENGTH_SHORT).show();
                }
            };
            task.executeParallel();
        }
        /* @} */

        mActionHandler.updateStatus(loaderResult);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitle = (TextView) findViewById(R.id.attachment_title);
        mSubTitle = (TextView) findViewById(R.id.attachment_subtitle);
        mProgress = (ProgressBar) findViewById(R.id.attachment_progress);
        mOverflowButton = (ImageView) findViewById(R.id.overflow);

        setOnClickListener(this);
        mOverflowButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        onClick(v.getId(), v);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        mPopup.dismiss();
        return onClick(item.getItemId(), null);
    }

    /* bug598087 popup menu dismiss when enter multi window mode.
     * @{
     */
    public void popupMenuDismiss() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
    }
    /* @} */

    /* SPRD: common format attachment feature.
     *@{
     */
    @Override
    public void saveAttachment() {
        if (mAttachment.canSave()) {
            mActionHandler.startDownloadingAttachment(AttachmentDestination.CACHE); //UNISOC: Modify for bug 1167532
            mSaveClicked = true;
            Analytics.getInstance().sendEvent(
                    "save_attachment", Utils.normalizeMimeType(mAttachment.getContentType()),
                    "attachment_bar", mAttachment.size);
        }
    }

    @Override
    public void downloadAgain() {
        /* SPRD: bug611475 modify begin @{ */
        if (mAttachment.isPresentLocally() && mContext != null && ((Activity) mContext).isResumed()) {
            mActionHandler.showDownloadingDialog();
            mActionHandler.startRedownloadingAttachment(mAttachment);
            Analytics.getInstance().sendEvent("redownload_attachment",
                    Utils.normalizeMimeType(mAttachment.getContentType()), "attachment_bar",
                    mAttachment.size);
        }
        /* @} */
    }

    @Override
    public void saveDummyAttachment() {
        /* SPRD: bug611475 modify begin @{ */
        if (mContext != null && ((Activity) mContext).isResumed()) {
            mActionHandler.showDownloadingDialog();
            mActionHandler.setViewOnFinish(false);
            mActionHandler.startDownloadingAttachment(AttachmentDestination.CACHE);
        }
        /* @} */
    }

    @Override
    public void showAndDownloadAttachment(int destination) {
        /* SPRD: bug611475 modify begin @{ */
        if (mContext != null && ((Activity) mContext).isResumed()) {
            mActionHandler.showDownloadingDialog();
            mActionHandler.startDownloadingAttachment(destination);
        }
        /* @} */
    }

    public void showAttachment(int destination) {
        // If the caller requested that this attachments be saved to the external storage, we should
        // verify that the it was saved there.
        if (mAttachment.isPresentLocally() &&
                (destination == AttachmentDestination.CACHE ||
                        mAttachment.destination == destination)) {
            viewAttachment();
        } else {
            if (mAttachment.isAttachmentTooLargeAndNotWifi(mContext) && !isDownloadingAttachment()) {
                mActionHandler.showDownloadConfirmDialog(DownloadConfirmDialogType.TYPE_SHOW_ATTACHMENT, this, destination);
            } else {
                showAndDownloadAttachment(destination);
            }
        }
    }
    /*@}*/

    /* SPRD:Add for bug524988 {@ */
    private boolean isDownloadingAttachment() {
        if (mAttachment.isDownloading()
                || (mAttachment.downloadedSize > 0 && mAttachment.state != AttachmentState.SAVED)) {
            return true;
        }
        return false;
    }

    /* @} */

    private boolean onClick(final int res, final View v) {
        if (res == R.id.preview_attachment) {
            previewAttachment();
        } else if (res == R.id.save_attachment) {
            /* SPRD: common format attachment feature.
             * original code:
            if (mAttachment.canSave()) {
                mActionHandler.startDownloadingAttachment(AttachmentDestination.EXTERNAL);
                mSaveClicked = true;

                Analytics.getInstance().sendEvent(
                        "save_attachment", Utils.normalizeMimeType(mAttachment.getContentType()),
                        "attachment_bar", mAttachment.size);
            }
             *@{
             */
            if (mAttachment.isAttachmentTooLargeAndNotWifi(mContext) && !isDownloadingAttachment()) {
                mActionHandler.showDownloadConfirmDialog(DownloadConfirmDialogType.TYPE_SAVE_ATTACHMENT, this);
            } else {
                saveAttachment();
            }
            /*@}*/
        /* SPRD: support pop3 feature about saved attachment to external.
         *@{
         */
        } else if (res == R.id.download_to_external) {
            /* SPRD: Modify for 691987 {@ */
            if (!Utility.isStorageMounted()) {
                Toast.makeText(mContext, R.string.no_available_storage, Toast.LENGTH_SHORT).show();
                return true;
            }
            /* @} */
            if (Utility.fileExists(mContext, mAttachment.contentUri)) {
                AttachmentUtilities.copyAttachmentFromInternalToExternal(mContext,
                        mAttachment.uri, mAttachment.contentUri,false,false,
                        new AttachmentUtilities.CopyAttachmentCallback() {
                            @Override
                            public void onCopyCompleted(String uri) {
                                if (null == uri) {
                                    LogUtils.w(LOG_TAG, "download to external, Copy attachment failed, return a null uri");
                                    return;
                                } else {
                                    /* SPRD: modify for bug 518385 @{ */
                                    String savePath = getContext()
                                            .getString(R.string.save_to_where)
                                            + Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS).getPath();
                                    Toast.makeText(mContext, savePath, Toast.LENGTH_SHORT).show();
                                    /* @} */
                                    updateSubtitleText(); //UNISOC: Modify for bug 1151820
                                }
                            }
                        });
            }
        /*@}*/
        } else if (res == R.id.download_again) {
            /* SPRD: common format attachment feature.
             * original code:
            if (mAttachment.isPresentLocally()) {
                mActionHandler.showDownloadingDialog();
                mActionHandler.startRedownloadingAttachment(mAttachment);

                Analytics.getInstance().sendEvent("redownload_attachment",
                        Utils.normalizeMimeType(mAttachment.getContentType()), "attachment_bar",
                        mAttachment.size);
            }
             *@{
             */
            if (mAttachment.isAttachmentTooLargeAndNotWifi(mContext) && !isDownloadingAttachment()) {
                mActionHandler.showDownloadConfirmDialog(DownloadConfirmDialogType.TYPE_DOWNLOAD_AGAIN, this);
            } else {
                downloadAgain();
            }
            /*@}*/
        } else if (res == R.id.cancel_attachment) {
            mActionHandler.cancelAttachment();
            mSaveClicked = false;

            Analytics.getInstance().sendEvent(
                    "cancel_attachment", Utils.normalizeMimeType(mAttachment.getContentType()),
                    "attachment_bar", mAttachment.size);
        } else if (res == R.id.attachment_extra_option1) {
            mActionHandler.handleOption1();
        } else if (res == R.id.overflow) {
            // If no overflow items are visible, just bail out.
            // We shouldn't be able to get here anyhow since the overflow
            // button should be hidden.
            if (shouldShowOverflow()) {
                if (mPopup == null) {
                    mPopup = new PopupMenu(getContext(), v);
                    mPopup.getMenuInflater().inflate(R.menu.message_footer_overflow_menu,
                            mPopup.getMenu());
                    mPopup.setOnMenuItemClickListener(this);
                }

                final Menu menu = mPopup.getMenu();
                menu.findItem(R.id.preview_attachment).setVisible(shouldShowPreview());
                menu.findItem(R.id.save_attachment).setVisible(shouldShowSave());
                menu.findItem(R.id.download_again).setVisible(shouldShowDownloadAgain());
                menu.findItem(R.id.attachment_extra_option1).setVisible(shouldShowExtraOption1());
                /* SPRD: support pop3 feature about saved attachment to external.
                 *@{
                 */
                menu.findItem(R.id.download_to_external).setVisible(shouldShowToExternal());
                /*@}*/
                /* SPRD: Modify for bug741482 @{ */
                menu.findItem(R.id.cancel_attachment).setVisible(shouldShowCancel());
                /* @} */
                mPopup.show();
            }
        } else {
            /* SPRD:bug477579 add to deal with drm files function. @{ */
            if (!EmailDrmUtils.getInstance().canOpenDrm(getContext(), mAttachment.getName(),
                    mAttachment.getContentType())) {
                return true;
            }
            /* @} */
            // Handles clicking the attachment
            // in any area that is not the overflow
            // button or cancel button or one of the
            // overflow items.
            final String mime = Utils.normalizeMimeType(mAttachment.getContentType());
            final String action;

            if ((mAttachment.flags & Attachment.FLAG_DUMMY_ATTACHMENT) != 0) {
                // This is a dummy. We need to download it, but not attempt to open or preview.
                /* SPRD: common format attachment feature.
                 * original code:
                mActionHandler.showDownloadingDialog();
                mActionHandler.setViewOnFinish(false);
                mActionHandler.startDownloadingAttachment(AttachmentDestination.CACHE);
                 *@{
                 */
                if (mAttachment.isAttachmentTooLargeAndNotWifi(mContext)
                        && !isDownloadingAttachment()) {
                    mActionHandler.showDownloadConfirmDialog(DownloadConfirmDialogType.TYPE_SAVE_DUMMY_ATTACHMENT, this);
                } else {
                    saveDummyAttachment();
                }
                /*@}*/

                action = null;
            }
            // If we can install, install.
            else if (MimeType.isInstallable(mAttachment.getContentType())) {
                // Save to external because the package manager only handles
                // file:// uris not content:// uris. We do the same
                // workaround in
                // UiProvider#getUiAttachmentsCursorForUIAttachments()
                /* SPRD: common format attachment feature.
                 * original code:
                mActionHandler.showAttachment(AttachmentDestination.EXTERNAL);
                 *@{
                 */
                if (mAttachment.destination == AttachmentDestination.CACHE
                        && Utility.fileExists(mContext, mAttachment.contentUri)) {
                    /* UNISOC: Modify for bug1168406 {@ */
                    /* original code:
                   AttachmentUtilities.copyAttachmentFromInternalToExternal(
                           mContext, mAttachment.uri, mAttachment.contentUri,false,false,
                           new AttachmentUtilities.CopyAttachmentCallback() {
                               @Override
                               public void onCopyCompleted(String uri) {
                                   if (null == uri) {
                                       LogUtils.w(LOG_TAG, "APK Copy attachment failed, return a null uri");
                                       return;
                                   }

                                   Uri currentUri = mAttachment.contentUri;
                                   mAttachment.contentUri = Uri.parse(uri);
                                   viewAttachment();
                                   mAttachment.contentUri = currentUri;
                               }
                           });
                           */
                    viewAttachment();
                    /*@}*/
                } else {
                    showAttachment(AttachmentDestination.CACHE);//UNISOC: Modify for bug 1178690
                }
                /*@}*/

                action = "attachment_bar_install";
            }
            // If we can view or play with an on-device app,
            // view or play.
            else if (MimeType.isViewable(
                    getContext(), mAttachment.contentUri, mAttachment.getContentType())) {
                /* SPRD: common format attachment feature.
                 * original code:
                mActionHandler.showAttachment(AttachmentDestination.CACHE);
                 *@{
                 */
                showAttachment(AttachmentDestination.CACHE);
                /*@}*/

                action = "attachment_bar";
            }
            // If we can only preview the attachment, preview.
            else if (mAttachment.canPreview()) {
                previewAttachment();

                action = null;
            }
            // Otherwise, if we cannot do anything, show the info dialog.
            else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                int dialogMessage = R.string.no_application_found;
                builder.setTitle(R.string.more_info_attachment)
                       .setMessage(dialogMessage)
                       .show();

                action = "attachment_bar_no_viewer";
            }

            if (action != null) {
                Analytics.getInstance()
                        .sendEvent("view_attachment", mime, action, mAttachment.size);
            }
        }

        return true;
    }

    /* SPRD: Modify for bug741482 @{ */
    private boolean shouldShowPreview() {
        // state could be anything
        return mAttachment.canPreview() && !shouldShowCancel();
    }

    private boolean shouldShowSave() {
        /* UNISOC: Modify for bug1151596 {@ */
        return mAttachment.canSave() && !mAttachment.isPresentLocally()
                && !mSaveClicked  && !shouldShowCancel();
        /* @} */
    }

    /* SPRD: support pop3 feature about saved attachment to external.
     *@{
     */
    private boolean shouldShowToExternal() {
        return mAttachment.state == AttachmentState.SAVED && mAttachment.destination == AttachmentDestination.CACHE && !shouldShowCancel();
    }
    /*@}*/

    private boolean shouldShowDownloadAgain() {
        // implies state == SAVED || state == FAILED
        // and the attachment supports re-download
        /* UNISOC: Modify for bug1151596 {@ */
        return mAttachment.supportsDownloadAgain() && mAttachment.isDownloadFinishedOrFailed()
                && !shouldShowCancel() && !mAttachment.isSavedToExternal();
        /* @} */
    }

    private boolean shouldShowExtraOption1() {
        return !mHideExtraOptionOne &&
                mActionHandler.shouldShowExtraOption1(mAccount.getType(),
                        mAttachment.getContentType()) && !shouldShowCancel();
    }

    /* SPRD: Modify for bug788697 @{ */
    private boolean shouldShowOverflow() {
        return shouldShowPreview() || shouldShowSave() || shouldShowDownloadAgain() ||
                shouldShowExtraOption1() || shouldShowCancel() || shouldShowToExternal();
    }
    /* @} */

    private boolean shouldShowCancel() {
        return mAttachment.isDownloading();
    }
    /* @} */

    @Override
    public void viewAttachment() {
        if (mAttachment.contentUri == null) {
            LogUtils.e(LOG_TAG, "viewAttachment with null content uri");
            return;
        }

        /* SPRD: Modify for bug495644, bug651173 messages with rfc822 part inside can not be parsed issue{@ */
        final String contentType = mAttachment.getContentType();
        if (TextUtils.isEmpty(mAttachment.getName()) && !MimeType.isEmlMimeType(contentType)) {
            LogUtils.e(LOG_TAG, "viewAttachment with null name");
            return;
        }
        /* @} */

        /* SPRD: Modify for bug581130 {@ */
        if (!Utility.fileExists(mContext, mAttachment.contentUri)) {
            LogUtils.e(LOG_TAG, "file not exist");
            Toast.makeText(mContext, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        /* @} */

        /* SPRD: Modify for bug728500, 747489 @{ */
        if (AttachmentUtilities.isSpecialMediaFile(mAttachment.getName())) {
            String displayType = mBidiFormatter != null ? mBidiFormatter
                    .unicodeWrap(AttachmentUtils.getDisplayType(mContext, mAttachment)) : "";
            if (!TextUtils.isEmpty(displayType) && !displayType.equals(mDisplayType)) {
                LogUtils.i(LOG_TAG, "displayType is changed, recall updateSubtitleText");
                mDisplayType = displayType;
                updateSubtitleText();
            }
        }
        /* @} */

        Intent intent = new Intent(Intent.ACTION_VIEW);
        /* SPRD: Modify for bug531196 {@ */
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Utils.setIntentDataAndTypeAndNormalize(
                intent, mAttachment.contentUri, contentType);
        /* SPRD: Modify for bug617101 {@ */
        /*
        *if (!TextUtils.isEmpty(contentType) && !contentType.contains("audio/")) {
        *   intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        *}
        */
        /* @} */
        /* @} */
        // For EML files, we want to open our dedicated
        // viewer rather than let any activity open it.
        if (MimeType.isEmlMimeType(contentType)) {
            intent.setPackage(getContext().getPackageName());
            intent.putExtra(AccountFeedbackActivity.EXTRA_ACCOUNT_URI,
                    mAccount != null ? mAccount.uri : null);
        }

        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException  e) {
            // couldn't find activity for View intent
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            int dialogMessage = R.string.no_application_found;
            builder.setTitle(R.string.more_info_attachment)
                   .setMessage(dialogMessage)
                   .show();
            LogUtils.e(LOG_TAG, e, "Couldn't find Activity for intent");
        }
    }

    private void previewAttachment() {
        /* SPRD:bug477579 add to deal with drm files function. @{ */
        if (!EmailDrmUtils.getInstance().canOpenDrm(getContext(), mAttachment.getName(),
                mAttachment.getContentType())) {
            return;
        }
        /* @} */
        if (mAttachment.canPreview()) {
            final Intent previewIntent =
                    new Intent(Intent.ACTION_VIEW, mAttachment.previewIntentUri);
            previewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            getContext().startActivity(previewIntent);

            Analytics.getInstance().sendEvent(
                    "preview_attachment", Utils.normalizeMimeType(mAttachment.getContentType()),
                    null, mAttachment.size);
        }
    }

    @Override
    public void onUpdateStatus() {
        updateSubtitleText();
    }

    @Override
    public void updateProgress(boolean showProgress) {
        if (mAttachment.isDownloading()) {
            mProgress.setMax(mAttachment.size);
            mProgress.setProgress(mAttachment.downloadedSize);
            mProgress.setIndeterminate(!showProgress);
            mProgress.setVisibility(VISIBLE);
            mSubTitle.setVisibility(INVISIBLE);
        } else {
            mProgress.setVisibility(INVISIBLE);
            mSubTitle.setVisibility(VISIBLE);
        }
    }

    private void updateSubtitleText() {
        // TODO: make this a formatted resource when we have a UX design.
        // not worth translation right now.
        final StringBuilder sb = new StringBuilder();
        if (mAttachment.state == AttachmentState.FAILED) {
            /* SPRD: common format attachment feature.
             * original code:
            sb.append(getResources().getString(R.string.download_failed));
             *@{
             */
            if ((mAttachment.flags & FLAG_DENY_MALWARE) != 0) {
                sb.append(getResources().getString(R.string.download_deny_malware));
            } else if ((mAttachment.flags & FLAG_DENY_WIFIONLY) != 0) {
                sb.append(getResources().getString(R.string.download_deny_wifionly));
            } else if ((mAttachment.flags & FLAG_DENY_NOINTENT) != 0) {
                sb.append(getResources().getString(R.string.download_deny_nointent));
            } else if ((mAttachment.flags & FLAG_DENY_NOSIDELOAD) != 0) {
                sb.append(getResources().getString(R.string.download_deny_nosideload));
            } else if ((mAttachment.flags & FLAG_DENY_APKINSTALL) != 0) {
                sb.append(getResources().getString(R.string.download_deny_apkinstall));
            } else if ((mAttachment.flags & FLAG_DENY_POLICY) != 0) {
                sb.append(getResources().getString(R.string.download_deny_policy));
            } else {
                sb.append(getResources().getString(R.string.download_failed));
            }
            /*@}*/
        } else {
            if (mAttachment.isSavedToExternal()) {
                sb.append(getResources().getString(R.string.saved, mAttachmentSizeText));
            } else {
                sb.append(mAttachmentSizeText);
            }
            if (mDisplayType != null) {
                sb.append(' ');
                sb.append(mDisplayType);
            }
        }
        mSubTitle.setText(sb.toString());
    }
}
