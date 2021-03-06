/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.emailcommon.utility;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaFile;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.android.emailcommon.Logging;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.EmailContent.Attachment;
import com.android.emailcommon.provider.EmailContent.AttachmentColumns;
import com.android.emailcommon.provider.EmailContent.Message;
import com.android.emailcommon.provider.EmailContent.MessageColumns;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AttachmentUtilities {

    public static final String FORMAT_RAW = "RAW";
    public static final String FORMAT_THUMBNAIL = "THUMBNAIL";

    /* SPRD: support pop3 feature about saved attachment to external, modify for Bug651415
     *@{
     */
    // For attachment in eml file.
    public static final String EML_ATTACHMENT_PROVIDER = "com.android.email.provider.eml.attachment";
    /*@}*/

    public static class Columns {
        public static final String _ID = "_id";
        public static final String DATA = "_data";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String SIZE = "_size";
    }

    private static final String[] ATTACHMENT_CACHED_FILE_PROJECTION = new String[] {
            AttachmentColumns.CACHED_FILE
    };

    /**
     * The MIME type(s) of attachments we're willing to send via attachments.
     *
     * Any attachments may be added via Intents with Intent.ACTION_SEND or ACTION_SEND_MULTIPLE.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_SEND_INTENT_TYPES = new String[] {
        "*/*",
    };
    /**
     * The MIME type(s) of attachments we're willing to send from the internal UI.
     *
     * NOTE:  At the moment it is not possible to open a chooser with a list of filter types, so
     * the chooser is only opened with the first item in the list.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_SEND_UI_TYPES = new String[] {
        "image/*",
        "video/*",
    };
    /**
     * The MIME type(s) of attachments we're willing to view.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_VIEW_TYPES = new String[] {
        "*/*",
    };
    /**
     * The MIME type(s) of attachments we're not willing to view.
     */
    public static final String[] UNACCEPTABLE_ATTACHMENT_VIEW_TYPES = new String[] {
    };
    /**
     * The MIME type(s) of attachments we're willing to download to SD.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_DOWNLOAD_TYPES = new String[] {
        "*/*",
    };
    /**
     * The MIME type(s) of attachments we're not willing to download to SD.
     */
    public static final String[] UNACCEPTABLE_ATTACHMENT_DOWNLOAD_TYPES = new String[] {
    };
    /**
     * Filename extensions of attachments we're never willing to download (potential malware).
     * Entries in this list are compared to the end of the lower-cased filename, so they must
     * be lower case, and should not include a "."
     */
    public static final String[] UNACCEPTABLE_ATTACHMENT_EXTENSIONS = new String[] {
        // File types that contain malware
        "ade", "adp", "bat", "chm", "cmd", "com", "cpl", "dll", "exe",
        "hta", "ins", "isp", "jse", "lib", "mde", "msc", "msp",
        "mst", "pif", "scr", "sct", "shb", "sys", "vb", "vbe",
        "vbs", "vxd", "wsc", "wsf", "wsh",
        // File types of common compression/container formats (again, to avoid malware)
        /* SPRD modify for bug 632545 remove .zie from protential malware list{@ */
        /* Original code is as follow
         * "zip", "gz", "z", "tar", "tgz", "bz2",
         * */
        "gz", "z", "tar", "tgz", "bz2",
        /* @} */
    };
    /**
     * Filename extensions of attachments that can be installed.
     * Entries in this list are compared to the end of the lower-cased filename, so they must
     * be lower case, and should not include a "."
     */
    public static final String[] INSTALLABLE_ATTACHMENT_EXTENSIONS = new String[] {
        "apk",
    };

    /* SPRD: Modify for bug747489 @{ */
    public static final String[] SPECIAL_MEDIA_FILE_EXTENSIONS = new String[] {
            "3gp", "3g2"
    };
    /* @} */

    /**
     * The maximum size of an attachment we're willing to download (either View or Save)
     * Attachments that are base64 encoded (most) will be about 1.375x their actual size
     * so we should probably factor that in. A 5MB attachment will generally be around
     * 6.8MB downloaded but only 5MB saved.
     */
    public static final int MAX_ATTACHMENT_DOWNLOAD_SIZE = (5 * 1024 * 1024);
    /**
     * The maximum size of an attachment we're willing to upload (measured as stored on disk).
     * Attachments that are base64 encoded (most) will be about 1.375x their actual size
     * so we should probably factor that in. A 5MB attachment will generally be around
     * 6.8MB uploaded.
     */
    public static final int MAX_ATTACHMENT_UPLOAD_SIZE = (5 * 1024 * 1024);

    private static Uri sUri;
    public static Uri getAttachmentUri(long accountId, long id) {
        if (sUri == null) {
            sUri = Uri.parse(Attachment.ATTACHMENT_PROVIDER_URI_PREFIX);
        }
        return sUri.buildUpon()
                .appendPath(Long.toString(accountId))
                .appendPath(Long.toString(id))
                .appendPath(FORMAT_RAW)
                .build();
    }

    // exposed for testing
    public static Uri getAttachmentThumbnailUri(long accountId, long id, long width, long height) {
        if (sUri == null) {
            sUri = Uri.parse(Attachment.ATTACHMENT_PROVIDER_URI_PREFIX);
        }
        return sUri.buildUpon()
                .appendPath(Long.toString(accountId))
                .appendPath(Long.toString(id))
                .appendPath(FORMAT_THUMBNAIL)
                .appendPath(Long.toString(width))
                .appendPath(Long.toString(height))
                .build();
    }

    /**
     * Return the filename for a given attachment.  This should be used by any code that is
     * going to *write* attachments.
     *
     * This does not create or write the file, or even the directories.  It simply builds
     * the filename that should be used.
     */
    public static File getAttachmentFilename(Context context, long accountId, long attachmentId) {
        return new File(getAttachmentDirectory(context, accountId), Long.toString(attachmentId));
    }

    /**
     * Return the directory for a given attachment.  This should be used by any code that is
     * going to *write* attachments.
     *
     * This does not create or write the directory.  It simply builds the pathname that should be
     * used.
     */
    public static File getAttachmentDirectory(Context context, long accountId) {
        return context.getExternalFilesDir(accountId + ".db_att");
    }

    /**
     * Helper to convert unknown or unmapped attachments to something useful based on filename
     * extensions. The mime type is inferred based upon the table below. It's not perfect, but
     * it helps.
     *
     * <pre>
     *                   |---------------------------------------------------------|
     *                   |                  E X T E N S I O N                      |
     *                   |---------------------------------------------------------|
     *                   | .eml        | known(.png) | unknown(.abc) | none        |
     * | M |-----------------------------------------------------------------------|
     * | I | none        | msg/rfc822  | image/png   | app/abc       | app/oct-str |
     * | M |-------------| (always     |             |               |             |
     * | E | app/oct-str |  overrides  |             |               |             |
     * | T |-------------|             |             |-----------------------------|
     * | Y | text/plain  |             |             | text/plain                  |
     * | P |-------------|             |-------------------------------------------|
     * | E | any/type    |             | any/type                                  |
     * |---|-----------------------------------------------------------------------|
     * </pre>
     *
     * NOTE: Since mime types on Android are case-*sensitive*, return values are always in
     * lower case.
     *
     * @param fileName The given filename
     * @param mimeType The given mime type
     * @return A likely mime type for the attachment
     */
    public static String inferMimeType(final String fileName, final String mimeType) {
        String resultType = null;
        String fileExtension = getFilenameExtension(fileName);
        boolean isTextPlain = "text/plain".equalsIgnoreCase(mimeType);
        if ("eml".equals(fileExtension)) {
            resultType = "message/rfc822";
            /* SPRD modify for bug676408{@ */
        } else if ("vcs".equals(fileExtension)) {
            resultType = "text/x-vcalendar";
        } else {
            /* @} */
            boolean isGenericType =
                    isTextPlain || "application/octet-stream".equalsIgnoreCase(mimeType);
            // If the given mime type is non-empty and non-generic, return it
            if (isGenericType || TextUtils.isEmpty(mimeType)) {
                if (!TextUtils.isEmpty(fileExtension)) {
                    //sprd modify for bug525132
                    resultType = getAttachmentExtensionMimeType(fileName);
                    if(TextUtils.isEmpty(resultType)){
                        // Otherwise, try to find a mime type based upon the file extension
                        resultType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
                        if (TextUtils.isEmpty(resultType)) {
                            // Finally, if original mimetype is text/plain, use it; otherwise synthesize
                            resultType = isTextPlain ? mimeType : "application/" + fileExtension;
                        }
                    }
                }
            } else {
                resultType = mimeType;
                //sprd modify for bug525132
                if ("flv".equalsIgnoreCase(fileExtension) || "f4v".equalsIgnoreCase(fileExtension)) {
                    resultType = "video/*";
                //sprd modify for bug533684
                } else if ("opus".equalsIgnoreCase(fileExtension)) {
                    resultType = "audio/*";
                }
            }
        }

        // No good guess could be made; use an appropriate generic type
        if (TextUtils.isEmpty(resultType)) {
            resultType = isTextPlain ? "text/plain" : "application/octet-stream";
        }
        return resultType.toLowerCase();
    }

    /**
     * Extract and return filename's extension, converted to lower case, and not including the "."
     *
     * @return extension, or null if not found (or null/empty filename)
     */
    public static String getFilenameExtension(String fileName) {
        String extension = null;
        if (!TextUtils.isEmpty(fileName)) {
            int lastDot = fileName.lastIndexOf('.');
            if ((lastDot > 0) && (lastDot < fileName.length() - 1)) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }
        }
        return extension;
    }

    /**
     * Resolve attachment id to content URI.  Returns the resolved content URI (from the attachment
     * DB) or, if not found, simply returns the incoming value.
     *
     * @param attachmentUri
     * @return resolved content URI
     *
     * TODO:  Throws an SQLite exception on a missing DB file (e.g. unknown URI) instead of just
     * returning the incoming uri, as it should.
     */
    public static Uri resolveAttachmentIdToContentUri(ContentResolver resolver, Uri attachmentUri) {
        Cursor c = resolver.query(attachmentUri,
                new String[] { Columns.DATA },
                null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    final String strUri = c.getString(0);
                    if (strUri != null) {
                        return Uri.parse(strUri);
                    }
                }
            } finally {
                c.close();
            }
        }
        return attachmentUri;
    }

    /**
     * In support of deleting a message, find all attachments and delete associated attachment
     * files.
     * @param context
     * @param accountId the account for the message
     * @param messageId the message
     */
    public static void deleteAllAttachmentFiles(Context context, long accountId, long messageId) {
        Uri uri = ContentUris.withAppendedId(Attachment.MESSAGE_ID_URI, messageId);
        Cursor c = context.getContentResolver().query(uri, Attachment.ID_PROJECTION,
                null, null, null);
        /* UNISOC: Modify for bug1194583 @{ */
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    long attachmentId = c.getLong(Attachment.ID_PROJECTION_COLUMN);
                    File attachmentFile = getAttachmentFilename(context, accountId, attachmentId);
                    // Note, delete() throws no exceptions for basic FS errors (e.g. file not found)
                    // it just returns false, which we ignore, and proceed to the next file.
                    // This entire loop is best-effort only.
                    /* UNISOC: Modify for bug1208439 @{ */
                    if (!attachmentFile.delete()) {
                        LogUtils.e(Logging.LOG_TAG, "Failed to delete attachment file " + attachmentFile.getName());
                    }
                    /* @} */
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        /* @} */
    }

    /**
     * In support of deleting a message, find all attachments and delete associated cached
     * attachment files.
     * @param context
     * @param accountId the account for the message
     * @param messageId the message
     */
    public static void deleteAllCachedAttachmentFiles(Context context, long accountId,
            long messageId) {
        final Uri uri = ContentUris.withAppendedId(Attachment.MESSAGE_ID_URI, messageId);
        final Cursor c = context.getContentResolver().query(uri, ATTACHMENT_CACHED_FILE_PROJECTION,
                null, null, null);
        /* UNISOC: Modify for bug1194583 @{ */
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    final String fileName = c.getString(0);
                    if (!TextUtils.isEmpty(fileName)) {
                        final File cachedFile = new File(fileName);
                        // Note, delete() throws no exceptions for basic FS errors (e.g. file not found)
                        // it just returns false, which we ignore, and proceed to the next file.
                        // This entire loop is best-effort only.
                        /* UNISOC: Modify for bug1208439 @{ */
                        if (!cachedFile.delete()) {
                            LogUtils.e(Logging.LOG_TAG, "Failed to delete cached file " + cachedFile.getName());
                        }
                        /* @} */
                    }
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        /* @} */
    }

    /**
     * In support of deleting a mailbox, find all messages and delete their attachments.
     *
     * @param context
     * @param accountId the account for the mailbox
     * @param mailboxId the mailbox for the messages
     */
    public static void deleteAllMailboxAttachmentFiles(Context context, long accountId,
            long mailboxId) {
        Cursor c = context.getContentResolver().query(Message.CONTENT_URI,
                Message.ID_COLUMN_PROJECTION, MessageColumns.MAILBOX_KEY + "=?",
                new String[] { Long.toString(mailboxId) }, null);
        /* UNISOC: Modify for bug1194583 @{ */
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    long messageId = c.getLong(Message.ID_PROJECTION_COLUMN);
                    deleteAllAttachmentFiles(context, accountId, messageId);
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        /* @} */
    }

    /**
     * In support of deleting or wiping an account, delete all related attachments.
     *
     * @param context
     * @param accountId the account to scrub
     */
    public static void deleteAllAccountAttachmentFiles(Context context, long accountId) {
        File[] files = getAttachmentDirectory(context, accountId).listFiles();
        if (files == null) return;
        for (File file : files) {
            boolean result = file.delete();
            if (!result) {
                LogUtils.e(Logging.LOG_TAG, "Failed to delete attachment file " + file.getName());
            }
        }
    }

    private static long copyFile(InputStream in, OutputStream out) throws IOException {
        long size = IOUtils.copy(in, out);
        in.close();
        out.flush();
        out.close();
        return size;
    }

    /* SPRD: support pop3 feature about saved attachment to external.
     *@{
     */
    /**
     * Copy attachment form internal to external.Do it in background.
     *
     * @param context
     * @param uri
     * @param deleteSource if we need to delete the cache file after copy completed
     * @param updateDb If we need to update the attachment info in db
     * @param CopyAttachmentCallback when copy completed, do something, if null, do nothing
     */
    public static void copyAttachmentFromInternalToExternal(final Context context,
            final Uri attUri, final Uri contentUri, final boolean deleteSource,
            final boolean updateDb, final CopyAttachmentCallback cb) {
        new EmailAsyncTask<Void, Void, String>(null /* no cancel */) {
            @Override
            protected String doInBackground(Void... params) {
                String resultUri = null;
                ///for attachment in eml file, we need process it in this special way. @{
                /* SPRD: Modify for bug788697 @{ */
                if ((attUri != null && attUri.toString().contains(EML_ATTACHMENT_PROVIDER))) {
                    ContentValues cv = new ContentValues();
                    cv.put(UIProvider.AttachmentColumns.STATE, UIProvider.AttachmentState.SAVED);
                    cv.put(UIProvider.AttachmentColumns.DESTINATION, UIProvider.AttachmentDestination.EXTERNAL);
                    /* SPRD: Modify for 698730 {@ */
                    Uri uri = attUri.buildUpon()
                            .appendQueryParameter("update_db", updateDb ? "1" : "0").build();
                    if (context.getContentResolver().update(uri, cv, null, null) == 1) {
                        resultUri = attUri.toString();
                    }
                    /* @} */
                /* @} */
                /// @}
                } else if (attUri != null) {  //UNISOC: Modify for bug1208439
                    long id = Long.parseLong(attUri.getLastPathSegment());
                    EmailContent.Attachment dbAttachment = EmailContent.Attachment.restoreAttachmentWithId(context, id);
                    /* SPRD: Modify for bug445517 {@ */
                    if(dbAttachment == null){
                        return resultUri;
                    }
                    /* @} */
                    try {
                        InputStream in = context.getContentResolver().openInputStream(contentUri);
                        dbAttachment.mUiDestination = UIProvider.AttachmentDestination.EXTERNAL;
                        resultUri = saveAttachment(context, in, dbAttachment, updateDb);
                        File internalAttachment = AttachmentUtilities.getAttachmentFilename(context, dbAttachment.mAccountKey, id);
                        if (deleteSource) {
                            if (internalAttachment != null && !internalAttachment.delete()) {
                                LogUtils.d(LogUtils.TAG, " copyAttachmentFromInternalToExternal : delete raw attachment failed. %s",
                                        dbAttachment.mFileName);
                            }
                        }
                        LogUtils.d(LogUtils.TAG, " copyAttachmentFromInternalToExternal : copy internal attachment to external. %s",
                                dbAttachment.mFileName);
                    } catch (IOException ioe) {
                        LogUtils.w(LogUtils.TAG, " IO exception when copy internal attachment to external.");
                    }
                }
                return resultUri;
            }

            @Override
            protected void onSuccess(String resultUri) {
                if (null != cb) {
                    cb.onCopyCompleted(resultUri);
                }
            }
        } .executeParallel((Void []) null);
    }

    /**
     * Wrap the original saveAttachment
     * @param context
     * @param in
     * @param attachment
     */
    public static void saveAttachment(Context context, InputStream in, Attachment attachment) {
        saveAttachment(context, in, attachment, true);
    }
    /*@}*/

    /**
     * Save the attachment to its final resting place (cache or sd card)
     */
    /* SPRD: support pop3 feature about saved attachment to external.
     * original code:
    public static void saveAttachment(Context context, InputStream in, Attachment attachment) {
     *@{
     */
    public static String saveAttachment(Context context, InputStream in, Attachment attachment,final boolean updateDb) {
    /*@}*/
        final Uri uri = ContentUris.withAppendedId(Attachment.CONTENT_URI, attachment.mId);
        final ContentValues cv = new ContentValues();
        final long attachmentId = attachment.mId;
        final long accountId = attachment.mAccountKey;
        String contentUri = null;
        final long size;
        /* UNISOC: Modify for bug1208439 @{ */
        OutputStream resolverOutputStream = null;
        OutputStream outputStream = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            if (attachment.mUiDestination == UIProvider.AttachmentDestination.CACHE) {
                Uri attUri = getAttachmentUri(accountId, attachmentId);
                resolverOutputStream = resolver.openOutputStream(attUri);
                size = copyFile(in, resolverOutputStream);
                contentUri = attUri.toString();
            } else if (Utility.isExternalStorageMounted()) {
                if (TextUtils.isEmpty(attachment.mFileName)) {
                    // TODO: This will prevent a crash but does not surface the underlying problem
                    // to the user correctly.
                    LogUtils.w(Logging.LOG_TAG, "Trying to save an attachment with no name: %d",
                            attachmentId);
                    throw new IOException("Can't save an attachment with no name");
                }

                /* SPRD: support pop3 feature about saved attachment to external.
                 *@{
                 */
                if(!updateDb){
                    File downloadFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/" + attachment.mFileName);
                    if (downloadFile.exists()) {
                        return Uri.fromFile(downloadFile).toString();
                    }
                }
                /*@}*/

                File downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                /* UNISOC: Modify for bug1208439 @{ */
                if (!downloads.mkdirs()) {
                    LogUtils.e(Logging.LOG_TAG, "Failed to make directory : " + downloads.getName());
                }
                /* @} */
                // SPRD: Modify for bug625452 illegal filename issue
                File file;
                try {
                    cv.put(AttachmentColumns.FLAGS, attachment.mFlags
                            & (~Attachment.FLAG_RENAMED_TO_LEGAE));
                    file = Utility.createUniqueFile(downloads, attachment.mFileName);
                    /* UNISOC: Modify for bug1154403 ???insert data {@ */
                    Uri externalUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    ContentValues initValues = new ContentValues();
                    initValues.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    initValues.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                    context.getContentResolver().insert(externalUri, initValues);
                    /* @} */
                } catch (IOException e) {
                    String newFileName = "Attachment_" + attachmentId + "."
                            + getFilenameExtension(attachment.mFileName);
                    LogUtils.w(Logging.LOG_TAG,
                            "Rename Attachment Name Due to IOException Old Name:"
                                    + attachment.mFileName + " New Name:" + newFileName);
                    file = Utility.createUniqueFile(downloads, newFileName);
                    cv.put(AttachmentColumns.FILENAME, newFileName);
                    cv.put(AttachmentColumns.FLAGS, attachment.mFlags
                            | Attachment.FLAG_RENAMED_TO_LEGAE);
                }
                /* @} */
                outputStream = new FileOutputStream(file);
                size = copyFile(in, outputStream);
                String absolutePath = file.getAbsolutePath();
                // Although the download manager can scan media files, scanning only happens
                // after the user clicks on the item in the Downloads app. So, we run the
                // attachment through the media scanner ourselves so it gets added to
                // gallery / music immediately.
                /* UNISOC: Modify for bug1154403 ???remove the scanFile{@ */
                /*MediaScannerConnection.scanFile(context, new String[] {absolutePath},
                        null, null);*/
                /* @} */
                final String mimeType = TextUtils.isEmpty(attachment.mMimeType) ?
                        "application/octet-stream" :
                        attachment.mMimeType;
                try {
                    DownloadManager dm =
                            (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    /* SPRD: support pop3 feature about saved attachment to external.
                     * original code:
                    long id = dm.addCompletedDownload(attachment.mFileName, attachment.mFileName,
                            false,
                            mimeType, absolutePath, size,
                            true);
                     *@{
                     */
                    /* SPRD:bug629619 always show notification when use dm.addCompletedDownload
                     * original bug608146 bug425212 bug1208439 @{*/
                    dm.addCompletedDownload(file.getName(), attachment.mFileName,
                            true /* use media scanner */,
                            mimeType, absolutePath, size,
                            true /* show notification */);
                    /* @}*/

                    /*
                     * SPRD modify for bug729388 Authority download requires ACCESS ALLDOWNLOAD
                     * permision whose protection level has changed from normal in AndroidN to
                     * signature in AndrodO
                     */
                    contentUri = Uri.fromFile(file).toString();
                    /* @} */
                } catch (final IllegalArgumentException e) {
                    LogUtils.d(LogUtils.TAG, e, "IAE from DownloadManager while saving attachment");
                    throw new IOException(e);
                }
            } else {
                LogUtils.w(Logging.LOG_TAG,
                        "Trying to save an attachment without external storage?");
                throw new IOException();
            }

            /* SPRD: support pop3 feature about saved attachment to external.
             * original code:
            cv.put(AttachmentColumns.SIZE, size);
            cv.put(AttachmentColumns.CONTENT_URI, contentUri);
            cv.put(AttachmentColumns.UI_STATE, UIProvider.AttachmentState.SAVED);
             *@{
             */
            // Update the attachment
            cv.put(AttachmentColumns.SIZE, size);
            /// update content uri
            attachment.setContentUri(contentUri);
            cv.put(AttachmentColumns.CONTENT_URI, contentUri);
            cv.put(AttachmentColumns.UI_STATE, UIProvider.AttachmentState.SAVED);
            /// also update destination
            cv.put(AttachmentColumns.UI_DESTINATION, attachment.mUiDestination);
            /*@}*/
        } catch (IOException e) {
            // Handle failures here...
            // SPRD: Modify for bug625452 illegal filename issue
            LogUtils.e(Logging.LOG_TAG, e, "Mark Attachment State FAILED e:" + e.getMessage());
            /* @} */
            cv.put(AttachmentColumns.UI_STATE, UIProvider.AttachmentState.FAILED);
        } finally {
            if (resolverOutputStream != null) {
                try {
                    resolverOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        /* @} */
        /* SPRD: support pop3 feature about saved attachment to external.
         * original code:
        context.getContentResolver().update(uri, cv, null, null);
         *@{
         */
        if (updateDb) {
            context.getContentResolver().update(uri, cv, null, null);
        }
        LogUtils.i(Logging.LOG_TAG,"saveAttachment uri :%s" , contentUri);
        return contentUri;
        /*@}*/
    }

    /* SPRD: Modify for bug448259,  mov attachment cannot be downloaded.
     *@{
     */
    /**
     * return the uri to the caller when copy is completed
     */
    public interface CopyAttachmentCallback {
        void onCopyCompleted(String uri);
    }
    /*@}*/

    /**
     * SPRD BUG525062: get mime type by system class MediaFile's api.
     */
    public static String getAttachmentExtensionMimeType(String fileName) {
        String mimeType = MediaFile.getMimeTypeForFile(fileName);
        return mimeType != null ? mimeType : "";
    }

    /* SPRD: Modify for bug747489 @{ */
    public static boolean isSpecialMediaFile(String fileName) {
        String extension = getFilenameExtension(fileName);
        return !TextUtils.isEmpty(extension)
                && Utility.arrayContains(SPECIAL_MEDIA_FILE_EXTENSIONS, extension);
    }
    /* @} */
}
