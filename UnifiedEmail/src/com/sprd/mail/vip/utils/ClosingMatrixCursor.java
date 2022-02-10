package com.sprd.mail.vip.utils;

import android.database.Cursor;

import com.android.mail.utils.MatrixCursorWithCachedColumns;

/**
 * {@link MatrixCursor} which takes an extra {@link Cursor} to the constructor, and close
 * it when self is closed.
 */
public class ClosingMatrixCursor extends MatrixCursorWithCachedColumns {
    private final Cursor mInnerCursor;

    public ClosingMatrixCursor(String[] columnNames, Cursor innerCursor) {
        super(columnNames);
        mInnerCursor = innerCursor;
    }

    @Override
    public void close() {
        if (mInnerCursor != null) {
            mInnerCursor.close();
        }
        super.close();
    }
}
