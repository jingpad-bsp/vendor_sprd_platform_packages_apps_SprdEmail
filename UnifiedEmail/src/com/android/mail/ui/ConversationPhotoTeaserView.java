package com.android.mail.ui;

import android.content.Context;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Folder;

/**
 * A teaser to introduce people to the contact photo check boxes
 */
public class ConversationPhotoTeaserView extends ConversationTipView {
    private final MailPrefs mMailPrefs;
    private boolean mShown;

    public ConversationPhotoTeaserView(final Context context) {
        super(context);

        mMailPrefs = MailPrefs.get(context);
        setText(getResources().getString(R.string.conversation_photo_welcome_text));
    }

    @Override
    protected ImageAttrSet getStartIconAttr() {
        return new ImageAttrSet(R.drawable.ic_check_24dp,
                R.drawable.conversation_photo_teaser_checkmark_bg, null);
    }

    @Override
    public void onUpdate(Folder folder, ConversationCursor cursor) {
        mShown = checkWhetherToShow(folder);
    }

    /* SPRD: Modify for bug718164 @{ */
    @Override
    public boolean getShouldDisplayInList(Folder folder) {
        // show if 1) sender images are enabled 2) there are items 3)is not draft
        mShown = checkWhetherToShow(folder);
        return mShown;
    }

    private boolean checkWhetherToShow(Folder folder) {
        // show if 1) sender images are disabled 2) there are items
        return shouldShowSenderImage() && !mAdapter.isEmpty()
                && !mMailPrefs.isConversationPhotoTeaserAlreadyShown()
                && (folder != null ? !folder.isDraft() : true);
    }
    /* @} */

    @Override
    public void onCabModeEntered() {
        if (mShown) {
            dismiss();
        }
    }

    @Override
    public void dismiss() {
        if (mShown) {
            mMailPrefs.setConversationPhotoTeaserAlreadyShown();
            mShown = false;
            Analytics.getInstance().sendEvent("list_swipe", "photo_teaser", null, 0);
        }
        super.dismiss();
    }

    protected boolean shouldShowSenderImage() {
        return mMailPrefs.getShowSenderImages();
    }
}
