/**
 * Copyright (c) 2013, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.activity;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.android.email.R;
import com.android.emailcommon.provider.EmailContent;
import com.android.email.activity.setup.AccountSettingsUtils;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.utils.LogUtils;
import android.widget.Toast;

import java.util.List;

public class ComposeActivityEmail extends ComposeActivity
        implements InsertQuickResponseDialog.Callback {
    static final String insertQuickResponseDialogTag = "insertQuickResponseDialog";

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean superCreated = super.onCreateOptionsMenu(menu);
        //SPRD Modify bug493411 Quick response not display while reply mail from statusbar notification
        if (mReplyFromAccount != null || mAccount != null) {
            getMenuInflater().inflate(R.menu.email_compose_menu_extras, menu);
            return true;
        } else {
            LogUtils.d(LogUtils.TAG, "mReplyFromAccount is null, not adding Quick Response menu");
            return superCreated;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.insert_quick_response_menu_item) {
            if (mReplyFromAccount == null || mReplyFromAccount.account == null){
                LogUtils.d(LogUtils.TAG, "mReplyFromAccount is null, do nothing");
                return true;
            }
            InsertQuickResponseDialog dialog = InsertQuickResponseDialog.newInstance(null,
                    mReplyFromAccount.account);
            /* SPRD: Modify for bug497167 {@ */
            final View view = getWindow().peekDecorView();
            if (view != null&& view.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            /* @} */
            /* SPRD: Modify for bug699150 {@ */
            if (dialog != null && isResumed()) {
                dialog.showAllowingStateLoss(getFragmentManager(), insertQuickResponseDialogTag);
            }
            /* @} */
        }
        return super.onOptionsItemSelected(item);
    }

    public void onQuickResponseSelected(CharSequence quickResponse) {
        /* SPRD: Modify for bug 607985 {@ */
        int maxLength = getResources().getInteger(com.android.mail.R.integer.body_with_character_lengths);
        int bodyLen = mBodyView != null ? mBodyView.getText().length() : 0;
        int quickResponseLen = quickResponse != null ? quickResponse.length() : 0;
        if (bodyLen + quickResponseLen > maxLength) {
            Toast.makeText(this, getResources().getString(
                    com.android.mail.R.string.body_input_more, maxLength), Toast.LENGTH_SHORT).show();
            return;
        }
        /* @} */
        /* UNISOC: Modify for bug 1228229 {@ */
        if (mBodyView == null) {
            return;
        }
        /* @}  */
        final int selEnd = mBodyView.getSelectionEnd();
        final int selStart = mBodyView.getSelectionStart();

        if (selEnd >= 0 && selStart >= 0) {
            final SpannableStringBuilder messageBody =
                    new SpannableStringBuilder(mBodyView.getText());
            final int replaceStart = selStart < selEnd ? selStart : selEnd;
            final int replaceEnd = selStart < selEnd ? selEnd : selStart;
            messageBody.replace(replaceStart, replaceEnd, quickResponse);
            mBodyView.setText(messageBody);
            /* SPRD: Modify for bug 604572 {@ */
            int selectionIndex = replaceStart + quickResponse.length();
            if (selectionIndex >= maxLength) {
                Toast.makeText(this, getResources().getString(
                        com.android.mail.R.string.body_input_more, maxLength), Toast.LENGTH_SHORT).show();
                selectionIndex = maxLength;
            }
            mBodyView.setSelection(selectionIndex);
            /* @} */
        } else {
            /* UNISOC: Modify for bug 1228229 {@ */
            if (quickResponse != null) {
                mBodyView.append(quickResponse);
            }
            /* @}  */
            mBodyView.setSelection(mBodyView.getText().length());
        }
    }

    @Override
    protected String getEmailProviderAuthority() {
        return EmailContent.AUTHORITY;
    }

    /**
     * SPRD: 523599 Add for save recipients and auto match emailAddress when create mail. modify for
     * bug712291 @{
     */
    @Override
    protected boolean saveRecipientsToHistoryDB(List<String> recipients) {
        if (recipients != null) {
            AccountSettingsUtils.saveAddressToHistoryDB(this, recipients,
                    AccountSettingsUtils.FROM_OTHER_ADD);
        }
        return false;
    }
    /** @} */

}
