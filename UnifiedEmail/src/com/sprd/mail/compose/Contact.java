
package com.sprd.mail.compose;

import java.util.ArrayList;
import java.util.Objects;

import android.database.Cursor;
import com.android.ex.chips.RecipientEntry;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public class Contact implements Parcelable {

    /* SPRD modify for bug 723456{@ */
    private String mName;
    private String mEmailAddress;
    private int mType;
    private String mLabel;
    private long mContactId;
    private long mId;
    private String mPhotoUri;
    private int mDisplayNameSource;
    private String mLookupKey;

    public Contact(Cursor cursor) {
        mName = cursor.getString(ContactEmailQuery.NAME_COLUMN_INDEX);
        mEmailAddress = cursor.getString(ContactEmailQuery.ADDRESS_COLUMN_INDEX);
        mType = cursor.getInt(ContactEmailQuery.TYPE_COLUMN_INDEX);
        mLabel = cursor.getString(ContactEmailQuery.LABEL_COLUMN_INDEX);
        mContactId = cursor.getLong(ContactEmailQuery.CONTACT_ID_COLUMN_INDEX);
        mId = cursor.getLong(ContactEmailQuery.DATA_ID_COLUMN_INDEX);
        mPhotoUri = cursor.getString(ContactEmailQuery.PHOTO_THUMBNAIL_COLUMN_INDEX);
        mDisplayNameSource = cursor.getInt(ContactEmailQuery.DISPLAY_NAME_SOURCE_COLUMN_INDEX);
        mLookupKey = cursor.getString(ContactEmailQuery.LOOKUP_KEY_COLUMN_INDEX);
    }

    public Contact(String address) {
        mName = address;
        mEmailAddress = address;
        mType = -1;
        mLabel = null;
        mContactId = -1;
        mId = -1;
        mPhotoUri = null;
        mDisplayNameSource = -1;
        mLookupKey = null;
    }

    public Contact(Parcel source) {
        mName = source.readString();
        mEmailAddress = source.readString();
        mType = source.readInt();
        mLabel = source.readString();
        mContactId = source.readLong();
        mId = source.readLong();
        mPhotoUri = source.readString();
        mDisplayNameSource = source.readInt();
        mLookupKey = source.readString();
    }
    /* @} */

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getEmailAddress() {
        return mEmailAddress;
    }

    /* UNISOC: Modify for bug1070854 {@ */
    public String getPhotoUri() {
        return mPhotoUri;
    }
    /* @} */

    public long getId() {
        return mId;
    }

    /* SPRD modify for bug 723456{@ */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Contact) {
            Contact contact = (Contact) o;
            return mId == contact.mId
                    && Objects.equals(mName, contact.mName)
                    && Objects.equals(mEmailAddress, contact.mEmailAddress)
                    && mType == contact.mType
                    && Objects.equals(mLabel, contact.mLabel)
                    && mContactId == contact.mContactId
                    && Objects.equals(mPhotoUri, contact.mPhotoUri)
                    && mDisplayNameSource == contact.mDisplayNameSource
                    && Objects.equals(mLookupKey, contact.mLookupKey);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mEmailAddress, mName, mType, mLabel, mContactId, mPhotoUri,
                mDisplayNameSource, mLookupKey);
    }
    /* @} */

    public int describeContents() {
        return 0;
    }

    /* SPRD modify for bug 723456{@ */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mEmailAddress);
        dest.writeInt(mType);
        dest.writeString(mLabel);
        dest.writeLong(mContactId);
        dest.writeLong(mId);
        dest.writeString(mPhotoUri);
        dest.writeInt(mDisplayNameSource);
        dest.writeString(mLookupKey);
    }
    /* @} */

    public static final Parcelable.Creator<Contact> CREATOR = new Creator<Contact>() {

        public Contact[] newArray(int size) {
            return new Contact[size];
        }

        public Contact createFromParcel(Parcel source) {
            return new Contact(source);
        }
    };

    public static final ArrayList<Contact> getContactsFromCursor(Cursor data) {
        ArrayList<Contact> result = new ArrayList<Contact>(16);
        /* SPRD Modify for bug883493 {@ */
        if (data != null && data.moveToFirst()) {
        /* @} */
            do {
                result.add(new Contact(data));
            } while (data.moveToNext());
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Name - ").append(mName).append(']').append("[EmailAddress - ")
                .append(mEmailAddress).append(']').append("[ID - ").append(mId).append(']').append("[ContactId - ").append(mContactId).append(']');
        return sb.toString();
    }

    /* SPRD modify for bug 723456{@ */
    public RecipientEntry getRecipientEntry() {
        if (mContactId != -1) {
            return RecipientEntry.constructTopLevelEntry(mName, mDisplayNameSource,
                    mEmailAddress, mType, mLabel, mContactId,
                    null, mId, TextUtils.isEmpty(mPhotoUri) ? null : mPhotoUri, true,
                    mLookupKey);
        } else {
            return RecipientEntry.constructFakeEntry(mEmailAddress, true);
        }
    }
    /* @} */
}
