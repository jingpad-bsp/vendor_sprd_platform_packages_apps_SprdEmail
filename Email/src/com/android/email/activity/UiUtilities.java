/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.email.activity;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TransformationMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class UiUtilities {

    public static final boolean GUIDE_SUPPORT = true;  //SPRD:bug471289 add account guide function.

    private UiUtilities() {
    }

    /**
     * Same as {@link View#findViewById}, but crashes if there's no view.
     */
    @SuppressWarnings("unchecked")
    public static <T extends View> T getView(View parent, int viewId) {
        return (T) checkView(parent.findViewById(viewId));
    }

    private static View checkView(View v) {
        if (v == null) {
            throw new IllegalArgumentException("View doesn't exist");
        }
        return v;
    }

    /**
     * Same as {@link View#setVisibility(int)}, but doesn't crash even if {@code view} is null.
     */
    public static void setVisibilitySafe(View v, int visibility) {
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    /**
     * Same as {@link View#setVisibility(int)}, but doesn't crash even if {@code view} is null.
     */
    public static void setVisibilitySafe(View parent, int viewId, int visibility) {
        setVisibilitySafe(parent.findViewById(viewId), visibility);
    }

    /* SPRD modify for show password feature {@ */
    public static void setShowPasswordCheckbox(CheckBox checkbox, final EditText passwordEdit) {
        checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TransformationMethod method = isChecked ? HideReturnsTransformationMethod
                        .getInstance() : PasswordTransformationMethod.getInstance();
                int selectionEnd = passwordEdit.getSelectionEnd();
                passwordEdit.setTransformationMethod(method);
                passwordEdit.setSelection(selectionEnd);
            }
        });
    }
    /* @} */
}
