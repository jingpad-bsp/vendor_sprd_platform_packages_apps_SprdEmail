
package com.sprd.mail.vip.activity;

import com.android.mail.R;
import com.android.mail.bitmap.ContactDrawable;
import com.android.mail.bitmap.ContactResolver;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * SPRD: The VipListItem class used for display vip item info(include: name, address, avatar...)
 */
public class VipListItem extends LinearLayout {

    private long mVipId;
    private ImageView mVipPhoto;
    private CheckBox mCheckBox;
    private TextView mVipName;
    private TextView mVipEmailAddress;
    private ContactDrawable mContactDrawable;

    public VipListItem(Context context) {
        this(context, null);
    }

    public VipListItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VipListItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Resources res = context.getResources();
        mContactDrawable = new ContactDrawable(res);
        int imageSize = res.getDimensionPixelSize(R.dimen.contacts_picker_image_size);
        mContactDrawable.setDecodeDimensions(imageSize, imageSize);
    }

    public void setVipId(long id) {
        mVipId = id;
    }

    public long getVipId() {
        return mVipId;
    }

    public void setVipName(String vipName) {
        mVipName.setText(vipName);
    }

    public void setVipEmailAddress(String vipEmailAddress) {
        mVipEmailAddress.setText(vipEmailAddress);
    }

    public void resetViews(String name, String address, ContactResolver resolver) {
        mContactDrawable.setContactResolver(resolver);
        mContactDrawable.bind(name, address);
        mVipPhoto.setImageDrawable(mContactDrawable);
    }

    public void setChecked(boolean checked) {
        mCheckBox.setChecked(checked);
    }

    public boolean isChecked() {
        return mCheckBox.isChecked();
    }

    public void setCheckMode(boolean isCheckMode) {
        if (isCheckMode) {
            mCheckBox.setVisibility(View.VISIBLE);
        } else {
            mCheckBox.setChecked(false);
            mCheckBox.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate(); // UNISOC: Modify for bug1208552
        mVipPhoto = (ImageView) findViewById(R.id.contact_icon);
        mCheckBox = (CheckBox) findViewById(R.id.vip_members_check_status);
        mCheckBox.setClickable(false);
        mVipName = (TextView) findViewById(R.id.contact_name);
        mVipEmailAddress = (TextView) findViewById(R.id.email_address);
    }
}
