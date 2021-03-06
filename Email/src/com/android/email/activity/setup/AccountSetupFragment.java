/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.email.activity.setup;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.android.email.R;
import com.android.email.activity.UiUtilities;
import com.android.mail.ui.UiHandler;

/**
 * Superclass for setup UI fragments.
 * Currently holds a super-interface for the callbacks, as well as the state it was launched for so
 * we can unwind things correctly when the user navigates the back stack.
 */
public class AccountSetupFragment extends Fragment implements View.OnClickListener {
    private static final String SAVESTATE_STATE = "AccountSetupFragment.state";
    private int mState;
    /* SPRD:bug471289 add account guide function {@ */
    protected static String mEmailType;
    protected View mButtonView;
    /*@}*/

    protected View mNextButton;
    protected View mPreviousButton;

    public interface Callback {
        void onNextButton();
        void onBackPressed();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt(SAVESTATE_STATE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVESTATE_STATE, mState);
    }

    public void setState(int state) {
        mState = state;
    }

    public int getState() {
        return mState;
    }

    /**
     * This method wraps the given content layout with the chrome appropriate for the account setup
     * flow. It also attaches itself as a click handler to the previous and next buttons.
     *
     * @param inflater LayoutInflater scoped to the appropriate context
     * @param container ViewGroup to inflate the view into
     * @param contentLayout Resource ID of the main content layout to insert into the template
     * @param headline Resource ID of the headline string
     * @return Fully inflated view hierarchy.
     */
    protected View inflateTemplatedView(final LayoutInflater inflater, final ViewGroup container,
            final int contentLayout, final int headline) {
        final View template = inflater.inflate(R.layout.account_setup_template, container, false);

        TextView headlineView = UiUtilities.getView(template, R.id.headline);
        if (headline > 0) {
            headlineView.setText(headline);
            headlineView.setVisibility(View.VISIBLE);
        } else {
            headlineView.setVisibility(View.GONE);
        }

        final ViewGroup contentContainer =
                (ViewGroup) template.findViewById(R.id.setup_fragment_content);
        inflater.inflate(contentLayout, contentContainer, true);

        mNextButton = UiUtilities.getView(template, R.id.next);
        mNextButton.setOnClickListener(this);
        mPreviousButton = UiUtilities.getView(template, R.id.previous);
        mPreviousButton.setOnClickListener(this);
        mButtonView = UiUtilities.getView(template, R.id.setup_fragment_button);  //SPRD:bug471289 add account guide function
        return template;
    }

    @Override
    public void onClick(View v) {
        final int viewId = v.getId();
        final Callback callback = (Callback) getActivity();
        /* UNISOC: Modify for bug1059670 {@ */
        if (callback == null) {
            return;
        }
        /* @} */

        if (viewId == R.id.next) {
            callback.onNextButton();
            /** SPRD bug497990 Avoid states disorder, block double-presses here @{ */
            blockNextButton();
            /** @} */
        } else if (viewId == R.id.previous) {
            callback.onBackPressed();
        }
    }

    public void setNextButtonEnabled(boolean enabled) {
        if (mNextButton != null) {
            mNextButton.setEnabled(enabled);
        }
    }

    public boolean isNextButtonEnabled() {
        return mNextButton.isEnabled();
    }

    /**
     * Disable "next" button for a period of time (1s), this is a walk
     * around for quick continious presses
     */
    public void blockNextButton() {
        boolean enabledBefore = isNextButtonEnabled();
        setNextButtonEnabled(false);
        if (enabledBefore) {
            new UiHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setNextButtonEnabled(true);
                }
            }, 1000L);
        }
    }

    /**
     * Set visibility of the "previous" button
     * @param visibility {@link View#INVISIBLE}, {@link View#VISIBLE}, {@link View#GONE}
     */
    public void setPreviousButtonVisibility(int visibility) {
        mPreviousButton.setVisibility(visibility);
    }

    /**
     * Set the visibility of the "next" button
     * @param visibility {@link View#INVISIBLE}, {@link View#VISIBLE}, {@link View#GONE}
     */
    public void setNextButtonVisibility(int visibility) {
        mNextButton.setVisibility(visibility);
    }

    /* SPRD:bug471289 add account guide function {@ */
    public void setButtonView(int visibility) {
        mButtonView.setVisibility(visibility);
    }

    public void showKeyboard(View v) {
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(v, 0);
            }
        }
    }
    /* @} */
}
