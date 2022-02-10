
package com.sprd.mail.compose;

import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;

public final class ContactEmailQuery {

    /* SPRD modify for bug 723456{@ */
    public static final int NAME_COLUMN_INDEX = 0;                  // String
    public static final int ADDRESS_COLUMN_INDEX = 1;               // String
    public static final int TYPE_COLUMN_INDEX = 2;                  // int
    public static final int LABEL_COLUMN_INDEX = 3;                 // String
    public static final int CONTACT_ID_COLUMN_INDEX = 4;            // long
    public static final int DATA_ID_COLUMN_INDEX = 5;               // long
    public static final int PHOTO_THUMBNAIL_COLUMN_INDEX = 6;       // String
    public static final int DISPLAY_NAME_SOURCE_COLUMN_INDEX = 7;   // int
    public static final int LOOKUP_KEY_COLUMN_INDEX = 8;            // String

    public static String EMAIL_PROJECTION[] = {
            Contacts.DISPLAY_NAME,                          // 0
            Email.DATA,                                     // 1
            Email.TYPE,                                     // 2
            Email.LABEL,                                    // 3
            Email.CONTACT_ID,                               // 4
            Email._ID,                                      // 5
            Contacts.PHOTO_THUMBNAIL_URI,                   // 6
            Contacts.DISPLAY_NAME_SOURCE,                   // 7
            Contacts.LOOKUP_KEY,                            // 8
    };
    /* @} */
}
