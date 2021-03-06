/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.android.mail.R;

import android.content.pm.ShortcutManager;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Icon;

/**
 * This activity prompts the user for a name for the shortcut to their folder
 * This activity could, in the future, be used to allow the user to specify an
 * icon for the shortcut
 */
public class ShortcutNameActivity extends Activity implements OnClickListener,
        TextView.OnEditorActionListener {
    /*package*/static final String EXTRA_FOLDER_CLICK_INTENT = "extra_folder_click_intent";

    /*package*/static final String EXTRA_SHORTCUT_NAME = "extra_shortcut_name";

    private EditText mFolderText;
    private String mFolderName;

    private Intent mShortcutClickIntent;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.shortcut_name_activity);

        mShortcutClickIntent = (Intent)getIntent().getParcelableExtra(EXTRA_FOLDER_CLICK_INTENT);
        mFolderName = getIntent().getStringExtra(EXTRA_SHORTCUT_NAME);

        mFolderText = (EditText) findViewById(R.id.folder_text);
        mFolderText.setText(mFolderName);
        mFolderText.setOnEditorActionListener(this);

        // Set focus to end of input line
        mFolderText.requestFocus();
        Editable editableText = mFolderText.getText();
        Selection.setSelection(editableText, editableText.length());

        findViewById(R.id.done).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            /* SPRD: Modify for bug495644 {@ */
            actionBar.setIcon(R.mipmap.ic_launcher_mail);
            /* @} */
        }
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (R.id.done == id) {
            doCreateShortcut();
        } else if (R.id.cancel == id) {
            doCancel();
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            doCreateShortcut();
            return true;
        }
        return false;
    }

    private void doCreateShortcut() {
        /* SPRD: Modify for bug 774793, ori 754831 @{ */
        // Get the name that the user entered
        String userShortcutName = mFolderName;

        final String shortcutName = mFolderText.getText().toString();
        if (TextUtils.getTrimmedLength(shortcutName) > 0) {
            userShortcutName = shortcutName;
        }

        /*
         * Create the shortcut Intent based on it with the additional
         * information that we have in this activity: name of the
         * account, calculate the human readable name of the folder and
         * use it as the shortcut name, etc...
         */
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        ShortcutInfo info = new ShortcutInfo.Builder(this, userShortcutName.toString().trim())
                .setShortLabel(userShortcutName)
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(mShortcutClickIntent)
                .build();
        Intent resultIntent = sm.createShortcutResultIntent(info); //UNISOC: Modify for bug1227545
        /* UNISOC: Modify for bug1233109 {@ */
        if (resultIntent != null) {
            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, mShortcutClickIntent);

            /*
             * fit for other launcher, can not remove
             */
            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher_widget));
            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, userShortcutName);

            setResult(RESULT_OK, resultIntent);
            /* @} */
        }
        /* @} */

        finish();
    }

    private void doCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

}
