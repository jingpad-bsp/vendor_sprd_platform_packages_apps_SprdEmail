package com.sprd.drm;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentProviderClient;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.FileDescriptor;

import libcore.io.IoUtils;

public class EmailUriUtil {
    private static String TAG = "EmailUriUtil";

    public static String getPath(final Context context, final Uri uri) {
        if (uri == null) {
            return null;
        }
        // DocumentProvider
        Log.d(TAG, " uri = " + uri);
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            Log.d(TAG,"entent getPath isExternalStorageDocument type = " + type + "  docId = " + docId);

            final StorageManager storage = context.getSystemService(StorageManager.class);
            VolumeInfo info = null;
            if ("primary".equalsIgnoreCase(type)) {
                info = storage.findVolumeById(VolumeInfo.ID_EMULATED_INTERNAL);
            } else {
                info = storage.findVolumeByUuid(type);
            }
            final int userId = UserHandle.myUserId();
            String path = null;
            if (info != null) {
                File file = info.getPathForUser(userId);
                if (file != null) {
                    path = file.getPath();
                }
                Log.d(TAG,"getPath VolumeInfo path = " + path);
            }

            if (split.length > 1) {
                path = path + "/" + split[1];
            }
            return path;
        }
        // DownloadsProvider
        else if (isDownloadsDocument(uri)) {
            /*final String id = DocumentsContract.getDocumentId(uri);
            final Uri contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/all_downloads"),  //Bug 1010130 public_downloads -> all_downloads
                    Long.valueOf(id));
            Log.d(TAG, " isDownloadsDocument contentUri = " + contentUri);
            return getDataColumn(context, contentUri, null, null);*/

            /* UNISOC: Modify for bug 1256364{@ */
            File file = null;
            ParcelFileDescriptor pFd = null;
            ContentResolver resolver = context.getContentResolver();
            try (ContentProviderClient client =  resolver.acquireUnstableContentProviderClient(uri)) {
                if(client == null){
                    Log.w(TAG, "Unknown URI: " +  uri);
                    return null;
                }
                pFd = client.openFile(uri, "r", null);
                if(pFd == null){
                    Log.w(TAG, "Can't open file: " +  uri);
                    return null;
                }
                FileDescriptor fd = pFd.getFileDescriptor();
                if(fd == null){
                    Log.w(TAG, "Can't get fd");
                    return null;
                }
                file = pFd.getFile(fd);
                if(file == null){
                    Log.w(TAG, "Can't get file ");
                    return null;
                }
            } catch(IOException | RemoteException e) {
                Log.d(TAG,"Exception " + e);
                return null;
            } finally {
                IoUtils.closeQuietly(pFd);
            }
            return file.getPath();
            /* @}  */
        }
        // MediaProvider
        else if (isMediaDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];
            Log.d(TAG, " isMediaDocument type = " + type);
            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            final String selection = "_id=?";
            final String[] selectionArgs = new String[]{split[1]};
            return getDataColumn(context, contentUri, selection, selectionArgs);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            Log.d(TAG, " isFileSheme ");
            return uri.getPath();
        }
        Log.d(TAG, " return null ");
        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri,
                                       String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } catch (Exception e) {
            if ("com.sprd.fileexplorer.fileProvider".equals(uri.getAuthority())) {
                if (uri.getPath() != null && uri.getPath().startsWith("/storage_root")) {
                    return uri.getPath().replaceFirst("/storage_root", "");
                }
                return uri.getPath();
            }
            throw e;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri
                .getAuthority());
    }
}
