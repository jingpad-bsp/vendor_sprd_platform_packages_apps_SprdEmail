
package com.sprd.mail.vip;

import java.util.ArrayList;
import java.util.List;

import com.android.emailcommon.mail.Address;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.Mailbox;
import com.android.mail.utils.LogUtils;
import com.sprd.mail.compose.Contact;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.text.TextUtils;

public class VipMember extends EmailContent implements Parcelable {
    public static final String ID = "_id";
    public static final String ACCOUNT_KEY = "accountKey";
    // The email address corresponding to this VipMember
    public static final String EMAIL_ADDRESS = "emailAddress";
    // The display name of the VipMember
    public static final String DISPLAY_NAME = "displayName";

    public static final String TABLE_NAME = "VipMember";
    public static final String EMAIL_PACKAGE_NAME = "com.android.email";
    public static final String AUTHORITY = EMAIL_PACKAGE_NAME + ".provider";
    public static final String NOTIFIER_AUTHORITY = EMAIL_PACKAGE_NAME + ".notifier";
    public static final String UI_NOTIFICATION_AUTHORITY = EMAIL_PACKAGE_NAME + ".uinotifications";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/vipmember");
    public static final Uri NOTIFIER_URI = Uri
            .parse("content://" + NOTIFIER_AUTHORITY + "/vipmember");
    public static final Uri UIPROVIDER_VIPMEMBER_NOTIFIER =
            Uri.parse("content://" + UI_NOTIFICATION_AUTHORITY + "/uivipmembers");
    public static final Uri UIPROVIDER_CONVERSATION_NOTIFIER =
            Uri.parse("content://" + UI_NOTIFICATION_AUTHORITY + "/uimessages");

    public static final String SELECTION_ACCCOUNT_ID = VipMember.ACCOUNT_KEY + "=?";
    public static final String SELECTION_EMAIL_ADDRESS = VipMember.EMAIL_ADDRESS
            + "=? COLLATE NOCASE";
    public static final String SELECTION_ACCCOUNT_ID_AND_EMAIL_ADDRESS =
            SELECTION_ACCCOUNT_ID + " and " + SELECTION_EMAIL_ADDRESS;

    public static final int CONTENT_ID_COLUMN = 0;
    public static final int ACCOUNT_KEY_COLUMN = 1;
    public static final int EMAIL_ADDRESS_COLUMN = 2;
    public static final int DISPLAY_NAME_COLUMN = 3;
    public static final String[] CONTENT_PROJECTION = new String[] {
            RECORD_ID, ACCOUNT_KEY, EMAIL_ADDRESS, DISPLAY_NAME
    };
    public static final int VIP_MAX_COUNT = 100;

    // SPRD: The all VIPs selection, used for VIP folder
    public static final String ALL_VIP_SELECTION =
            MessageColumns.MAILBOX_KEY + " NOT IN ("
                    + "SELECT " + MailboxColumns.ID + " FROM " + Mailbox.TABLE_NAME + ""
                    + " WHERE " + MailboxColumns.TYPE + " = " + Mailbox.TYPE_TRASH + " OR "
                    + MailboxColumns.TYPE + " = " + Mailbox.TYPE_SEARCH
                    + ")"
                    + " AND " + Message.FLAG_LOADED_SELECTION;

    public long mAccountKey;
    public String mDisplayName;
    public String mEmailAddress;

    public static VipMember restoreVipMemberWithId(Context context, long id) {
        return EmailContent.restoreContentWithId(context, VipMember.class,
                VipMember.CONTENT_URI, VipMember.CONTENT_PROJECTION, id);
    }

    /**
     * SPRD: Restore vip member with special email address of the account
     *
     * @param context the context to access database
     * @param accountId the id of this account
     * @param emailAddress the email address of this vip member
     * @return the vip member
     */
    public static VipMember restoreVipMemberWithEmailAddress(Context context,
            long accountId, String emailAddress) {
        try {
            Cursor c = context.getContentResolver().query(CONTENT_URI,
                    VipMember.CONTENT_PROJECTION,
                    SELECTION_ACCCOUNT_ID_AND_EMAIL_ADDRESS,
                    new String[] {
                            String.valueOf(accountId), emailAddress
                    }, null);
            try {
                if (c.moveToFirst()) {
                    return getContent(context, c, VipMember.class);
                } else {
                    return null;
                }
            } finally {
                c.close();
            }
        } catch (IllegalStateException e) {
            LogUtils.w(LogUtils.TAG, e,
                    "VipMember#restoreVipMemberWithEmailAddress throw out IllegalStateException");
        } catch (SQLiteException e) {
            LogUtils.w(LogUtils.TAG, e,
                    "VipMember#restoreVipMemberWithEmailAddress throw out SQLiteException");
        }
        return null;
    }

    /**
     * SPRD: Restore the vip members of the account with id accountId
     *
     * @param context the context to access database
     * @param accountId the id of the account
     * @return array of VipMembers for the account with id accountId
     */
    public static VipMember[] restoreVipMembersWithAccountId(Context context,
            long accountId) {
        Cursor c = context.getContentResolver().query(CONTENT_URI, CONTENT_PROJECTION,
                SELECTION_ACCCOUNT_ID, new String[] {
                    String.valueOf(accountId)
                }, null);
        try {
            int count = c.getCount();
            VipMember[] vipMembers = new VipMember[count];
            for (int i = 0; i < count; ++i) {
                c.moveToNext();
                VipMember vipMember = new VipMember();
                vipMember.restore(c);
                vipMembers[i] = vipMember;
            }
            return vipMembers;
        } finally {
            c.close();
        }
    }

    /**
     * Add VIPs with addresses. But the duplicates and invalid addresses will not be added. And if
     * the address same as one existed VIP's but the display name not same, just update the display
     * name.
     *
     * @param context the context
     * @param accountId the account id
     * @param addresses the address which contains email address and display name
     * @param addresses call back while add vips
     */
    public static void addVIPs(Context context, long accountId,
            ArrayList<Contact> selectedContacts,
            AddVipsCallback callback) {
        boolean hasDuplicateAddress = removeDuplicateAddress(selectedContacts);

        ArrayList<Contact> vipAddresses = new ArrayList<Contact>();
        ArrayList<Contact> nonVipAddresses = new ArrayList<Contact>();
        ArrayList<VipMember> updatedVips = new ArrayList<VipMember>();
        VipMember[] vips = VipMember.restoreVipMembersWithAccountId(context, accountId);
        ArrayList<VipMember> vipList = new ArrayList<VipMember>();
        for (VipMember vip : vips) {
            vipList.add(vip);
        }
        collectEmailAddresses(vipList, selectedContacts, vipAddresses, nonVipAddresses, updatedVips);
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        for (VipMember vip : updatedVips) {
            ops.add(ContentProviderOperation.newUpdate(VipMember.CONTENT_URI)
                    .withSelection(VipMember.ID + "=" + vip.mId, null)
                    .withValue(VipMember.DISPLAY_NAME, vip.mDisplayName).build());
        }

        int vipCount = vips.length;
        for (int i = 0; i < nonVipAddresses.size(); i++) {
            vipCount++;
            if (vipCount > VIP_MAX_COUNT) {
                break;
            }
            ContentValues values = new ContentValues();
            values.put(VipMember.ACCOUNT_KEY, accountId);
            values.put(VipMember.EMAIL_ADDRESS, nonVipAddresses.get(i).getEmailAddress());
            values.put(VipMember.DISPLAY_NAME, nonVipAddresses.get(i).getName());
            ops.add(ContentProviderOperation.newInsert(VipMember.CONTENT_URI).withValues(values)
                    .build());
        }

        try {
            context.getContentResolver().applyBatch(VipMember.AUTHORITY, ops);
        } catch (Exception e) {
            LogUtils.e(LogUtils.TAG, e, "VipMember#addVIPs",
                    "Error occured while save contacts as vips");
        }

        if (hasDuplicateAddress || vipAddresses.size() > updatedVips.size()) {
            callback.tryToAddDuplicateVip();
        }
        if (vipCount > VIP_MAX_COUNT) {
            callback.addVipOverMax();
        }
    }

    /**
     * SPRD: Add one address into VIPs. Return true if the address added as a new VIP member, or
     * updated the display name of existed VIP member. Return false if the count of existed VIPs has
     * match VIP_MAX_COUNT, or it duplicated with a existed VIP.
     *
     * @param context the context to access database
     * @param address the Address will add as a VIP member. The address should contains the email
     *            address and display name.
     * @param callback the callback for feed back the adding status
     * @return Return true if the address added as a new VIP member, or updated the display name of
     *         existed VIP member. Return false if the count of existed VIPs has match
     *         VIP_MAX_COUNT, or it duplicated with a existed VIP.
     */
    public static boolean addVIP(Context context, Address address, AddVipsCallback callback) {
        if (address == null) {
            return false;
        }
        String emailAddress = address.getAddress();
        String displayName = address.getPersonal();
        if (displayName == null) {
            displayName = emailAddress;
            address.setPersonal(displayName);
        }
        VipMember[] vips = restoreVipMembersWithAccountId(context, Account.ACCOUNT_ID_COMBINED_VIEW);
        if (vips.length == VIP_MAX_COUNT) {
            callback.addVipOverMax();
            return false;
        }
        VipMember existVip = null;
        for (VipMember vip : vips) {
            if (vip.mEmailAddress.equalsIgnoreCase(emailAddress)) {
                existVip = vip;
                break;
            }
        }
        if (existVip == null) {
            // Not exist, add as new vip
            VipMember vip = new VipMember();
            vip.mAccountKey = Account.ACCOUNT_ID_COMBINED_VIEW;
            vip.mEmailAddress = emailAddress;
            vip.mDisplayName = displayName;
            vip.save(context);
            return true;
        }
        // Exist, check if the display name should be updated
        if (displayName.equals(existVip.mDisplayName)) {
            // Duplicate vip member
            callback.tryToAddDuplicateVip();
            return false;
        } else {
            // Update the display name
            existVip.mDisplayName = displayName;
            ContentValues cv = new ContentValues();
            cv.put(DISPLAY_NAME, displayName);
            existVip.update(context, cv);
            return true;
        }
    }

    /**
     * A call back for add vips
     */
    public interface AddVipsCallback {
        public void tryToAddDuplicateVip();

        public void addVipOverMax();
    }

    /**
     * Remove the duplicate email addresses in the address list
     *
     * @param addresses the address list
     * @return true if there are duplicate email addresses be removed
     */
    private static boolean removeDuplicateAddress(ArrayList<Contact> selectedContacts) {
        boolean hasDuplicateAddress = false;
        for (int i = 0; i < selectedContacts.size(); i++) {
            String addr = selectedContacts.get(i).getEmailAddress();
            if (TextUtils.isEmpty(addr)) {
                selectedContacts.remove(i);
                i--;
                continue;
            }
            for (int j = i + 1; j < selectedContacts.size(); j++) {
                if (addr.equalsIgnoreCase(selectedContacts.get(j).getEmailAddress())) {
                    selectedContacts.remove(j);
                    j--;
                    hasDuplicateAddress = true;
                }
            }
        }
        return hasDuplicateAddress;
    }

    /**
     * update VIP display name with addresses.
     *
     * @param context the context
     * @param accountId the account id
     * @param addresses the email address
     * @param name the display name
     */
    public static void updateVipDisplayName(Context context, long accountId, String addresses,
            String name) {
        VipMember member = restoreVipMemberWithEmailAddress(context, accountId, addresses);
        /*
         * SPRD: update the user name when it was changed in contacts, and trigger database change
         * in order to update the avatar of contact
         */
        if (member != null) {
            if (name == null) {
                name = member.mDisplayName;
            }
            ContentValues values = new ContentValues();
            values.put(DISPLAY_NAME, name);
            member.update(context, values);
        }
    }

    /**
     * According the current exist vipList, collect the addresses into VipAddresses and
     * NonVipAddresses, and if the address exist in vipList but the display name not same as vip,
     * update the vip's display name and put it into updatedVips.
     *
     * @param vipList input, the current exist vipList
     * @param addresses input, the addresses that will be collected
     * @param VipAddresses output, the email addresses exist in vipList
     * @param NonVipAddresses output, the email addresses not exist in vipList
     * @param updatedVips output, the vips that display name has been updated
     */
    private static void collectEmailAddresses(ArrayList<VipMember> vipList,
            ArrayList<Contact> selectedContacts, ArrayList<Contact> VipAddresses,
            ArrayList<Contact> NonVipAddresses, ArrayList<VipMember> updatedVips) {
        for (int i = 0; i < selectedContacts.size(); i++) {
            String emailAddress = selectedContacts.get(i).getEmailAddress();
            String displayName = selectedContacts.get(i).getName();
            if (displayName == null) {
                displayName = emailAddress;
                // / SPRD: no need to decode when set displayName as email address, which may lead
                // to messy code @{
                selectedContacts.get(i).setName(displayName);
                // / @}
            }
            VipMember existVip = findExistVip(vipList, emailAddress);
            if (existVip == null) {
                NonVipAddresses.add(selectedContacts.get(i));
            } else {
                VipAddresses.add(selectedContacts.get(i));
                vipList.remove(existVip);
                if (!displayName.equals(existVip.mDisplayName)) {
                    existVip.mDisplayName = displayName;
                    updatedVips.add(existVip);
                }
            }
        }
    }

    private static VipMember findExistVip(ArrayList<VipMember> vips, String emailAddress) {
        for (VipMember vip : vips) {
            if (vip.mEmailAddress.equalsIgnoreCase(emailAddress)) {
                return vip;
            }
        }
        return null;
    }

    /**
     * SPRD: count the VipMembers of the account with id accountId
     *
     * @param context the context to access database
     * @param accountId the id of the account
     * @return the VipMembers count of the account with id accountId
     */
    public static int countVipMembersWithAccountId(Context context,
            long accountId) {
        return count(context, CONTENT_URI, SELECTION_ACCCOUNT_ID,
                new String[] {
                    String.valueOf(accountId)
                });
    }

    /**
     * SPRD: Delete the vip member with the email address for the account
     *
     * @param context the context to access database
     * @param emailAddress the email address of the vip member
     * @return the count of deleted
     */
    public static int deleteVipMembers(Context context,
            String emailAddress) {
        try {
            return context.getContentResolver().delete(CONTENT_URI,
                    SELECTION_EMAIL_ADDRESS,
                    new String[] {
                        emailAddress
                    });
        } catch (SQLiteException ex) {
            LogUtils.w(LogUtils.TAG, ex, "VipMember#deleteVipMembers throw out SQLiteException");
            return -1;
        }
    }

    public static int deleteVipMembers(Context context, List<Long> vipIdList) {
        try {
            StringBuilder sb = new StringBuilder();
            int size = vipIdList.size();
            if (size > 0) {
                sb.append(BaseColumns._ID).append(" IN(");
                for (int i = 0; i < size; i++) {
                    sb.append(vipIdList.get(i)).append(',');
                }
                sb.setLength(sb.length() - 1);
                sb.append(")");
                return context.getContentResolver().delete(CONTENT_URI,
                        sb.toString(), null
                        );
            } else {
                return -1;
            }

        } catch (SQLiteException ex) {
            LogUtils.w(LogUtils.TAG, ex, "VipMember#deleteVipMembers throw out SQLiteException");
            return -1;
        }
    }

    public VipMember() {
        mBaseUri = CONTENT_URI;
    }

    @Override
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(ACCOUNT_KEY, mAccountKey);
        values.put(EMAIL_ADDRESS, mEmailAddress);
        values.put(DISPLAY_NAME, mDisplayName);
        return values;
    }

    @Override
    public void restore(Cursor cursor) {
        mBaseUri = CONTENT_URI;
        mId = cursor.getLong(CONTENT_ID_COLUMN);
        mAccountKey = cursor.getLong(ACCOUNT_KEY_COLUMN);
        mEmailAddress = cursor.getString(EMAIL_ADDRESS_COLUMN);
        mDisplayName = cursor.getString(DISPLAY_NAME_COLUMN);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeLong(mAccountKey);
        dest.writeString(mEmailAddress);
        dest.writeString(mDisplayName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append(mId);
        sb.append(", ");
        sb.append(mAccountKey);
        sb.append(", ");
        sb.append(mEmailAddress);
        sb.append(", ");
        sb.append(mDisplayName);
        sb.append("]");
        return sb.toString();
    }
}
