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

package com.android.mail.providers;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import androidx.annotation.Nullable;
import android.content.ContentProvider;
import android.net.Uri;

import com.android.mail.R;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;

public class SearchRecentSuggestionsProvider extends ContentProvider {
    /*
     * String used to delimit different parts of a query.
     */
    public static final String QUERY_TOKEN_SEPARATOR = " ";

    // general database configuration and tables
    private SQLiteOpenHelper mOpenHelper;
    private static final String DATABASE_NAME = "suggestions.db";
    private static final String SUGGESTIONS_TABLE = "suggestions";

    private static final String QUERY =
            " SELECT _id" +
            "   , display1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 +
            "   , ? || query AS " + SearchManager.SUGGEST_COLUMN_QUERY +
            "   , ? AS " + SearchManager.SUGGEST_COLUMN_ICON_1 +
            " FROM " + SUGGESTIONS_TABLE +
            " WHERE display1 LIKE ?" +
            " ORDER BY date DESC";

    // Table of database versions.  Don't forget to update!
    // NOTE:  These version values are shifted left 8 bits (x 256) in order to create space for
    // a small set of mode bitflags in the version int.
    //
    // 1      original implementation with queries, and 1 or 2 display columns
    // 1->2   added UNIQUE constraint to display1 column
    // 2->3   <redacted> being dumb and accidentally upgraded, this should be ignored.
    private static final int DATABASE_VERSION = 3 * 256;

    private static final int DATABASE_VERSION_2 = 2 * 256;
    private static final int DATABASE_VERSION_3 = 3 * 256;

    private String mHistoricalIcon;

    /* SPRD:bug475886 add local search function @{ */
    protected Context mContext;
    private ArrayList<String> mFullQueryTerms;

    private final Object mDbLock = new Object();
    private boolean mClosed;

    public SearchRecentSuggestionsProvider(Context context) {
        mContext = context;
        mOpenHelper = new DatabaseHelper(mContext, DATABASE_VERSION);

        // The URI of the icon that we will include on every suggestion here.
        mHistoricalIcon = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/" + R.drawable.ic_history_24dp;
        mSuggestionsUri = Uri.parse("content://" + "com.android.mail.suggestionsprovider"
                + "/suggestions");
    }
    private Uri mSuggestionsUri;

    public SearchRecentSuggestionsProvider() {
        // TODO Auto-generated constructor stub
        super();
        mContext = getContext();
        mSuggestionsUri = Uri.parse("content://" + "com.android.mail.suggestionsprovider"
                + "/suggestions");
        mHistoricalIcon = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + "com.android.email" + "/" + R.drawable.ic_history_24dp;
    }
    /* @} */

    public void cleanup() {
        synchronized (mDbLock) {
            mOpenHelper.close();
            mClosed = true;
        }
    }

    /**
     * Builds the database.  This version has extra support for using the version field
     * as a mode flags field, and configures the database columns depending on the mode bits
     * (features) requested by the extending class.
     *
     * @hide
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, int newVersion) {
            super(context, DATABASE_NAME, null, newVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            final String create = "CREATE TABLE suggestions (" +
                    "_id INTEGER PRIMARY KEY" +
                    ",display1 TEXT UNIQUE ON CONFLICT REPLACE" +
                    ",query TEXT" +
                    ",date LONG" +
                    ");";
            db.execSQL(create);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // When checking the old version clear the last 8 bits
            oldVersion = oldVersion & ~0xff;
            newVersion = newVersion & ~0xff;
            if (oldVersion == DATABASE_VERSION_2 && newVersion == DATABASE_VERSION_3) {
                // Oops, didn't mean to upgrade this database. Ignore this upgrade.
                return;
            }
            db.execSQL("DROP TABLE IF EXISTS suggestions");
            onCreate(db);
        }
    }

    /**
     * Set the other query terms to be included in the user's query.
     * These are in addition to what is being looked up for suggestions.
     * @param terms
     */
    public void setFullQueryTerms(ArrayList<String> terms) {
        mFullQueryTerms = terms;
    }

    private @Nullable SQLiteDatabase getDatabase(boolean readOnly) {
        synchronized (mDbLock) {
            if (!mClosed) {
                /* SPRD: modify for bug 522251 @{ */
                try {
                    return readOnly ? mOpenHelper.getReadableDatabase() :
                            mOpenHelper.getWritableDatabase();
                }catch (SQLiteFullException e){
                    LogUtils.w(LogUtils.TAG, e.toString());
                }catch (SQLiteException e){
                    LogUtils.w(LogUtils.TAG, e.toString());
                }
                /* @{ */
            }
        }
        return null;
    }

    public Cursor query(String query) {
        final SQLiteDatabase db = getDatabase(true /* readOnly */);
        if (db != null) {
            final StringBuilder builder = new StringBuilder();
            if (mFullQueryTerms != null) {
                for (String token : mFullQueryTerms) {
                    builder.append(token).append(QUERY_TOKEN_SEPARATOR);
                }
            }

            final String[] args = new String[] {
                    builder.toString(), mHistoricalIcon, "%" + query + "%" };

            try {
                // db could have been closed due to cleanup, simply don't do anything.
                return db.rawQuery(QUERY, args);
            } catch (IllegalStateException e) {}
        }
        return null;
    }

    /**
     * We are going to keep track of recent suggestions ourselves and not depend on the framework.
     * Note that this writes to disk. DO NOT CALL FROM MAIN THREAD.
     */
    public void saveRecentQuery(String query) {
        final SQLiteDatabase db = getDatabase(false /* readOnly */);
        if (db != null) {
            ContentValues values = new ContentValues(3);
            values.put("display1", query);
            values.put("query", query);
            values.put("date", System.currentTimeMillis());
            // Note:  This table has on-conflict-replace semantics, so insert may actually replace
            try {
                // db could have been closed due to cleanup, simply don't do anything.
                db.insert(SUGGESTIONS_TABLE, null, values);
            } catch (IllegalStateException e) {}
        }
    }

    public void clearHistory() {
        final SQLiteDatabase db = getDatabase(false /* readOnly */);
        if (db != null) {
            try {
                // db could have been closed due to cleanup, simply don't do anything.
                db.delete(SUGGESTIONS_TABLE, null, null);
            } catch (IllegalStateException e) {}
        }
    }

    /* SPRD:bug475886 add local search function @{ */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri newUri = null;
        long rowId = -1;
        try {
            // db could have been closed due to cleanup, simply don't do anything.
            /* SPRD:bug503731 Coverity defect begin. @{ */
            final SQLiteDatabase db = getDatabase(false);
            if(db != null){
                rowId = db.insert(SUGGESTIONS_TABLE, null, values);
            }
           /* @} */
        } catch (IllegalStateException e) {
        }
        if (rowId > 0) {
            newUri = Uri.withAppendedPath(mSuggestionsUri, String.valueOf(rowId));
        }
        return newUri;
    }

    @Override
    public boolean onCreate() {
        if (mContext == null) {
            mContext = getContext();
        }

        if (mOpenHelper == null) {
            mOpenHelper = new DatabaseHelper(mContext, DATABASE_VERSION);
        }

        // The URI of the icon that we will include on every suggestion here.
        mHistoricalIcon = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/" + R.drawable.ic_history_24dp;
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String query = selectionArgs[0];
        return this.query(query);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not implemented");
    }
    /* @} */
}
