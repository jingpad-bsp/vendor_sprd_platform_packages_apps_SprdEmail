
package com.sprd.mail.vip.activity;

import java.util.ArrayList;
import java.util.List;

import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.utils.Utils;
import com.android.bitmap.BitmapCache;
import com.android.bitmap.UnrefedBitmapCache;

import com.sprd.mail.vip.VipMember;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

/**
 * SPRD: an adapter for listView, add some extra contacts information in its MatrixCursor.
 */
public class VipListAdapter extends CursorAdapter {
    public static final String TAG = "VipListAdapter";

    private static final int SENDERS_IMAGES_CACHE_TARGET_SIZE_BYTES = 1024 * 339;
    private static final float SENDERS_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION = 0f;
    private static final int SENDERS_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY = 100;
    /* SPRD: Modify for bug709127 @{ */
    private static final String KEY_CHECKED_ITEM = "key_checked_item";
    private static final String KEY_CHECKED_MODE = "key_checked_mode_adapter";
    /* @} */

    private final LayoutInflater mInflater;

    private ArrayList<Long> mCheckedItems = new ArrayList<Long>();
    private SelectedVipChangedListener mSelectedItemsChangeListener;
    private BitmapCache mSendersImageCache;
    private ContactResolver mContactResolver;
    private boolean mIsCheckMode = false;

    public VipListAdapter(Context context) {
        super(context, null, 0 /* flags; no content observer */);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSendersImageCache = new UnrefedBitmapCache(Utils.isLowRamDevice(context) ?
                0 : SENDERS_IMAGES_CACHE_TARGET_SIZE_BYTES,
                SENDERS_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION,
                SENDERS_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY);
        mContactResolver = new ContactResolver(context.getContentResolver(), mSendersImageCache);
    }

    /* SPRD: Modify for bug709127 @{ */
    public void restoreInstanceState(Bundle savedState) {
        long[] saveCheckedItems = savedState.getLongArray(KEY_CHECKED_ITEM);
        if (saveCheckedItems != null) {
            for (int i = 0; i < saveCheckedItems.length; i++) {
                mCheckedItems.add(saveCheckedItems[i]);
            }
        }

        mIsCheckMode = savedState.getBoolean(KEY_CHECKED_MODE, false);
        notifySelectedItemsChanged();
    }

    public void onSaveInstanceState(Bundle outState) {
        long[] saveCheckedItems = new long[mCheckedItems.size()];
        for (int i = 0; i < saveCheckedItems.length; i++) {
            saveCheckedItems[i] = mCheckedItems.get(i);
        }
        outState.putBoolean(KEY_CHECKED_MODE, mIsCheckMode);
        outState.putLongArray(KEY_CHECKED_ITEM, saveCheckedItems);
    }
    /* @} */

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(R.layout.email_vip_item, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        /* UNISOC modify for bug1208507 {@ */
        if (view instanceof VipListItem) {
            VipListItem item = (VipListItem) view;
            String name = cursor.getString(VipMember.DISPLAY_NAME_COLUMN);
            String address = cursor.getString(VipMember.EMAIL_ADDRESS_COLUMN);
            long vipId = cursor.getLong(VipMember.ID_PROJECTION_COLUMN);
            item.resetViews(name, address, mContactResolver);
            item.setVipId(vipId);
            item.setVipName(name);
            item.setVipEmailAddress(address);
            item.setCheckMode(mIsCheckMode);
            if (mIsCheckMode) {
                item.setChecked(mCheckedItems.contains(vipId));
            }
            item.setOnClickListener(new OnClickListener() {

                public void onClick(View v) {
                    /* UNISOC modify for bug1208507 {@ */
                    /* SPRD: Modify for bug709188 @{ */
                    if (mIsCheckMode && v instanceof VipListItem) {
                        VipListItem vipItem = (VipListItem) v;
                        vipItem.setChecked(!vipItem.isChecked());
                        boolean isChecked = vipItem.isChecked();
                        long vipId = vipItem.getVipId();
                        boolean changed = false;
                        if (isChecked && !mCheckedItems.contains(vipId)) {
                            mCheckedItems.add(vipId);
                            changed = true;
                        } else {
                            mCheckedItems.remove(vipId);
                            changed = true;
                        }

                        if (changed) {
                            notifySelectedItemsChanged();
                        }
                    }
                }
                /* @} */
                /* @} */
            });
        }
        /* @} */
    }

    public void selectAll() {
        mCheckedItems.clear();
        int count = getCount();
        for (int i = 0; i < count; i++) {
            mCheckedItems.add(getItemId(i));
        }
        super.notifyDataSetChanged();
        notifySelectedItemsChanged();
    }

    public void cancleAll() {
        mCheckedItems.clear();
        super.notifyDataSetChanged();
        notifySelectedItemsChanged();
    }

    public static Loader<Cursor> createVipContentLoader(Context context, long accountId) {
        return new VipContentLoader(context, accountId);
    }

    /**
     * Loads vip members and add the corresponding avatar info. The returned {@link Cursor} is
     * always a {@link ClosingMatrixCursor}.
     */
    private static class VipContentLoader extends CursorLoader {

        public VipContentLoader(Context context, long accountId) {
            super(context, VipMember.CONTENT_URI, VipMember.CONTENT_PROJECTION,
                    VipMember.SELECTION_ACCCOUNT_ID, new String[] {
                            Long.toString(accountId)
                    }, VipMember.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
        }

        @Override
        public Cursor loadInBackground() {
            return super.loadInBackground();
        }

    }

    /**
     * Get the position of the vip with the email address. This method must called from UI thread.
     * 
     * @param emailAddress the email address of the vip
     * @return the position in the vip list
     */
    int getPosition(String emailAddress) {
        if (emailAddress == null) {
            return -1;
        }
        Cursor c = getCursor();
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            if (emailAddress.equalsIgnoreCase(c.getString(VipMember.EMAIL_ADDRESS_COLUMN))) {
                return c.getPosition();
            }
        }
        return -1;
    }

    public List<Long> getSelectedItems() {
        return mCheckedItems;
    }

    public void setSelectedItemsChangeListener(SelectedVipChangedListener listener) {
        mSelectedItemsChangeListener = listener;
    }

    private void notifySelectedItemsChanged() {
        if (mSelectedItemsChangeListener != null) {
            mSelectedItemsChangeListener.onSelectedVipListenerChanged();
        }
    }

    @Override
    public void notifyDataSetChanged() {
        if (!mCheckedItems.isEmpty()) {
            Cursor newCursor = getCursor();
            ArrayList<Long> currentCheckedItems = mCheckedItems;
            ArrayList<Long> tempList = new ArrayList<Long>(currentCheckedItems.size());

            if (newCursor != null && newCursor.moveToFirst()) {
                do {
                    long id = newCursor.getLong(VipMember.ID_PROJECTION_COLUMN);
                    if (currentCheckedItems.contains(id)) {
                        tempList.add(id);
                    }
                } while (newCursor.moveToNext());
            }
            mCheckedItems = tempList;
        }
        notifySelectedItemsChanged();
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        mCheckedItems.clear();
        super.notifyDataSetInvalidated();
    }

    public void toogleCheckMode() {
        mIsCheckMode = !mIsCheckMode;
        mCheckedItems.clear();
        notifyDataSetChanged();
    }

    public interface SelectedVipChangedListener {
        void onSelectedVipListenerChanged();
    }
}
