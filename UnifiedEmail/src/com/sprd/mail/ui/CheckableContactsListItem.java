
package com.sprd.mail.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.mail.R;
import com.android.mail.bitmap.ContactDrawable;
import com.android.mail.bitmap.ContactResolver;

import com.sprd.mail.compose.Contact;

public class CheckableContactsListItem extends RelativeLayout {

    private TextView mNameTextView;
    private TextView mEmailTextView;
    private CheckBox mCheckBox;
    private ImageView mContactImage;
    private ContactDrawable mContactDrawable;

    public CheckableContactsListItem(Context context) {
        this(context, null);

    }

    public CheckableContactsListItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CheckableContactsListItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Resources res = context.getResources();
        mContactDrawable = new ContactDrawable(res);
        int imageSize = res.getDimensionPixelSize(R.dimen.contacts_picker_image_size);
        mContactDrawable.setDecodeDimensions(imageSize, imageSize);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNameTextView = (TextView) findViewById(R.id.contact_name);
        mEmailTextView = (TextView) findViewById(R.id.contact_email);
        mContactImage = (ImageView) findViewById(R.id.contact_image);
        mCheckBox = (CheckBox) findViewById(R.id.contact_check_status);
        mCheckBox.setClickable(false);
    }

    public void bind(Contact contact, boolean checked, ContactResolver resolver) {
        String name = contact.getName();
        String emailAddress = contact.getEmailAddress();
        mNameTextView.setText(name);
        mEmailTextView.setText(emailAddress);
        mContactDrawable.setContactResolver(resolver);
        /* UNISOC: Modify for bug1070854 {@ */
        mContactDrawable.bind(name, emailAddress, contact.getPhotoUri());
        /* @} */
        mContactImage.setImageDrawable(mContactDrawable);
        mCheckBox.setChecked(checked);
    }

    public void setItemChecked(boolean checked) {
        mCheckBox.setChecked(checked);
    }

    public void toggleChecked() {
        boolean isChecked = mCheckBox.isChecked();
        mCheckBox.setChecked(!isChecked);
    }
}
