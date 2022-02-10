
package com.sprd.mail.compose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import com.sprd.mail.ui.CheckableContactsListItem;

import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.utils.Utils;

import com.android.bitmap.BitmapCache;
import com.android.bitmap.UnrefedBitmapCache;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

public class MultiCheckContactsPickerAdapter extends BaseAdapter {

    private static final String KEY_CHECKED_CONTACTS = "key_checned_contacts";
    private static final int SENDERS_IMAGES_CACHE_TARGET_SIZE_BYTES = 1024 * 339;
    private static final float SENDERS_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION = 0f;
    private static final int SENDERS_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY = 100;
    public static final int MAX_CHECKED_COUNT = 100;

    private ArrayList<Contact> mContacts = new ArrayList<Contact>(16);
    private HashMap<Long, Contact> mCheckedContact = new HashMap<Long, Contact>(16);
    private Context mContext;
    private BitmapCache mSendersImageCache;
    private ContactResolver mContactResolver;
    private OnCheckStateChangeListener mCheckedStateListener;

    public MultiCheckContactsPickerAdapter(Context context) {
        mContext = context;
        mSendersImageCache = new UnrefedBitmapCache(Utils.isLowRamDevice(context) ?
                0 : SENDERS_IMAGES_CACHE_TARGET_SIZE_BYTES,
                SENDERS_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION,
                SENDERS_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY);
        mContactResolver = new ContactResolver(context.getContentResolver(), mSendersImageCache);
    }

    public int getCount() {
        return mContacts.size();
    }

    public Object getItem(int position) {
        return mContacts.get(position);
    }

    public long getItemId(int position) {
        return mContacts.get(position).getId();
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflator = LayoutInflater.from(mContext);
        CheckableContactsListItem view;
        /* UNISOC modify for bug1208597 {@ */
        if (convertView != null && convertView instanceof CheckableContactsListItem) {
            view = (CheckableContactsListItem) convertView;
        } else {
            view = (CheckableContactsListItem) inflator.inflate(R.layout.contact_list_item_view,
                    null);
        }
        /* @} */
        Contact contact = (Contact) getItem(position);
        view.bind(contact, mCheckedContact.containsKey(contact.getId()), mContactResolver);
        view.setTag(position);
        view.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                /* UNISOC modify for bug1208597 {@ */
                if (v instanceof CheckableContactsListItem) {
                    toogleChecked((CheckableContactsListItem) v);
                }
                /* @} */
            }
        });

        return view;
    }

    public void updateData(ArrayList<Contact> data) {
        mContacts.clear();
        mContacts.addAll(data);
        validataAgainstData(data);
        notifyDataSetChanged();
        notifyChecedStateChanged();
    }

    public void onSaveInstanceState(Bundle outState) {
        Contact[] array = mCheckedContact.values().toArray(new Contact[mCheckedContact.size()]);
        outState.putParcelableArray(KEY_CHECKED_CONTACTS, array);
    }

    public void restoreInstanceState(Bundle savedState) {
        Parcelable[] array = savedState.getParcelableArray(KEY_CHECKED_CONTACTS);
        if (array != null) {
            int len = array.length;
            for (int i = 0; i < len; i++) {
                Contact contact = (Contact) array[i];
                mCheckedContact.put(contact.getId(), contact);
            }
            notifyChecedStateChanged();
        }
    }

    public void checkAll() {
        int contactSize = mContacts.size();
        int len = Math.min(contactSize, MAX_CHECKED_COUNT);
        if (mCheckedContact.size() < len) {
            Contact contact;
            long id;
            for (int i = 0; i < len; i++) {
                contact = mContacts.get(i);
                id = contact.getId();
                if (!mCheckedContact.containsKey(id)) {
                    mCheckedContact.put(id, contact);
                }

                if (mCheckedContact.size() >= len) {
                    break;
                }
            }

            notifyDataSetChanged();
            notifyChecedStateChanged();
        }

        if (contactSize > MAX_CHECKED_COUNT) {
            showCheckedContactExceedsMaxInfo();
        }
    }

    public void cancleAll() {
        mCheckedContact.clear();
        notifyDataSetChanged();
        notifyChecedStateChanged();

    }

    public int getCheckedContactsCount() {
        return mCheckedContact.size();
    }

    public Collection<Contact> getCheckedContacts() {
        return mCheckedContact.values();
    }

    public void setOnCheckStateListener(OnCheckStateChangeListener listener) {
        mCheckedStateListener = listener;
    }

    private void toogleChecked(CheckableContactsListItem v) {
        int position = (Integer) v.getTag();
        /* SPRD: Modify for bug 729005 @{ */
        if (position >= mContacts.size()) {
            return;
        }
        /* @} */
        Contact contact = (Contact) getItem(position);
        long id = contact.getId();
        boolean currentChecked = mCheckedContact.containsKey(id);
        boolean checkStateChanged = true;
        if (currentChecked) {
            mCheckedContact.remove(id);
        } else if (getCheckedContactsCount() < MAX_CHECKED_COUNT) {
            mCheckedContact.put(id, contact);
        } else {
            checkStateChanged = false;
            showCheckedContactExceedsMaxInfo();
        }

        if (checkStateChanged) {
            v.toggleChecked();
            notifyChecedStateChanged();
        }

    }

    private void validataAgainstData(ArrayList<Contact> data) {

        Set<Long> currentCheckedContacts = mCheckedContact.keySet();
        if (currentCheckedContacts.isEmpty()) {
            return;
        }

        int len = data.size();
        HashMap<Long, Contact> tempList = new HashMap<Long, Contact>(mCheckedContact.size());
        for (int i = 0; i < len; i++) {
            Contact contact = data.get(i);
            long id = contact.getId();
            if (currentCheckedContacts.contains(id)) {
                tempList.put(id, contact);
            }
        }
        mCheckedContact = tempList;
    }

    private void notifyChecedStateChanged() {
        if (mCheckedStateListener != null) {
            mCheckedStateListener.onCheckedStateChanged();
        }
    }

    private void showCheckedContactExceedsMaxInfo() {
        String message = mContext.getResources().getString(R.string.checked_contacts_exceeds_max,
                MAX_CHECKED_COUNT);
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

    public interface OnCheckStateChangeListener {
        void onCheckedStateChanged();
    }

}
