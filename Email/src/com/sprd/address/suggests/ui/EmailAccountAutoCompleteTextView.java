package com.sprd.address.suggests.ui;

import android.content.Context;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.MultiAutoCompleteTextView;
import com.android.emailcommon.Logging;
import com.android.emailcommon.utility.TextUtilities;

/** SPRD : 523599. An editable text view, extending {@link android.widget.MultiAutoCompleteTextView}, that
 *  can filter data by EmailAccountTokenizer (separate string by '@'), and auto-fill
 *  EditText when the filter count was only one.
 */
public class EmailAccountAutoCompleteTextView extends MultiAutoCompleteTextView {
    // Disable auto-replace on only one matched item when IME was in fullscreen mode
    //, cause the IME's own bug.
    private boolean mIMEFullScreenMode = false;
    // Indicate that we are using IME wrapper to workaround the flicker issue.
    // Don't do the auto-replace if the user was deleting chars.
    private boolean mDeletingText = false;
    private Tokenizer mTokenizer;

    private String mEmailType;

    public void setmEmailType(String mEmailType) {
        this.mEmailType = mEmailType;
    }

    public String getmEmailType() {
        return mEmailType;
    }

    private class MyWatcher implements TextWatcher {
        public void afterTextChanged(Editable s) {
        }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mDeletingText = (after == 0);
        }
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    public EmailAccountAutoCompleteTextView(Context context) {
        super(context);
    }

    public EmailAccountAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setThreshold(1);
        setTokenizer(new EmailAccountTokenizer());
        addTextChangedListener(new MyWatcher());
    }

    public EmailAccountAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressWarnings("unchecked")
    private void replaceTextAndHighLight(CharSequence text) {
        clearComposingText();
        int end = getSelectionStart();
        Editable editable = getText();
        DropdownAccountsArrayAdapter<String> adapter = (DropdownAccountsArrayAdapter<String>) getAdapter();
        Filter filter = adapter.getFilter();

        int start = 0;
        String filteringString = null;
        // Account filter and Address filter used different tokenizer.
        if (filter instanceof DropdownAddressFilter) {
            filteringString = ((DropdownAddressFilter) filter).getFilterString();
            start = new EmailHistoryTokenizer().findTokenStart(editable, end);
        } else {
            filteringString = ((DropdownAccountsFilter) filter).getFilterString();
            start = new EmailAccountTokenizer().findTokenStart(editable, end);
        }
        String currentToFilterString = editable.subSequence(start, end).toString();
        // Cause the filtering was an asynchronous operation, if the current text was not yet the filtered text.
        // we never replace the text.
        Log.d(Logging.LOG_TAG, "replaceTextAndHighLight : filteringString : " + filteringString + " currentToFilterString : "
                + currentToFilterString + " start : " + start + " end : " + end);
        if (TextUtilities.stringOrNullEquals(filteringString, currentToFilterString)) {
            // false, not trigger filter once more.
            setText(text, false);
            Editable replaced = getText();
            Selection.setSelection(replaced, end, replaced.length());
        }
    }

    @Override
    protected void replaceText(CharSequence text) {
        clearComposingText();

        setText(text);
        // make sure we keep the caret at the end of the text view
        Editable spannable = getText();
        Selection.setSelection(spannable, spannable.length());
    }

    @Override
    public void onFilterComplete(int count) {
        super.onFilterComplete(count);
        // Don't perform filtering when user deleting chars.
        if (!mDeletingText) {
            //replacePartTextIfNeed();
        }
    }

    /**
     * Only filter the unselected part of text.
     */
    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionStart();
            int start = mTokenizer.findTokenStart(text, end);
            performFiltering(text, start, end, keyCode);
        } else {
            dismissDropDown();

            Filter f = getFilter();
            if (f != null) {
                f.filter(null);
            }
        }
    }

    @Override
    public void setTokenizer(Tokenizer t) {
        mTokenizer = t;
        super.setTokenizer(t);
    }

    public void replacePartTextIfNeed() {
        BaseAdapter adapter = (BaseAdapter) getAdapter();
        if (adapter.getCount() == 0) {
            getText().delete(getSelectionStart(), getSelectionEnd());
            return;
        }
        int itemCount = adapter.getCount();
        // if the matched account was only one, we auto fill the EditText with it.
        // Don't replace text when the IME was in full screen mode.
        if (itemCount == 1 && !mIMEFullScreenMode) {
            CharSequence currentText = getText().subSequence(0, getSelectionStart());
            CharSequence replaceText = convertSelectionToString(adapter.getItem(0));
            /* SPRD:bug547595 Coverity defect CID:124616 begin. @{ */
            if (replaceText != null && replaceText.equals(currentText)) {
                dismissDropDown();
                return;
            }
            /* @} */
            replaceTextAndHighLight(replaceText);
            dismissDropDown();
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection target = super.onCreateInputConnection(outAttrs);
        if (target == null) {
            return null;
        }
        return new EmailInputConnectionWrapper(target, false);
    }

    /**
     * Using for override commitText(), we need keep text's selection state
     * when user input new characters. To avoid text flicker in selection state.
     *
     */
    private class EmailInputConnectionWrapper extends InputConnectionWrapper {
        public EmailInputConnectionWrapper(InputConnection target, boolean mutable) {
            super(target, mutable);
        }

        @Override
        public boolean reportFullscreenMode(boolean enabled) {
            mIMEFullScreenMode = enabled;
            return super.reportFullscreenMode(enabled);
        }
    }

    /**
     * This email account Tokenizer can be used for lists where the items are
     * separated by '@' and one or more spaces.
     */
    public static class EmailAccountTokenizer implements Tokenizer {
        public int findTokenStart(CharSequence text, int cursor) {
            int i = 0;
            while (i < cursor && text.charAt(i) != '@') {
                i++;
            }
            // Don't perform filtering when there was no '@' input.
            if (i == cursor) {
                return cursor;
            }
            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (text.charAt(i) == '@') {
                    return i;
                } else {
                    i++;
                }
            }
            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            // Highlight the replacing text part.
            SpannableString highlightSpan = new SpannableString(text);
            highlightSpan.setSpan(new BackgroundColorSpan(TextUtilities.HIGHLIGHT_COLOR_INT), 0,
                    highlightSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return highlightSpan;
        }
    }

    /* SPRD modify for bug711477 {@ */
    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);
        }
        return super.onTextContextMenuItem(id);
    }
    /* @} */

    /**
     * Just set a empty tokennizer to enable auto complete function,
     * Original CommaTokenizer will terminate any input comma to two part,
     * which will make UI abnormal.
     * Current solution only support auto complete one email address.
     */
    public static class EmailHistoryTokenizer implements Tokenizer {
        @Override
        public CharSequence terminateToken(CharSequence text) {
            return text;
        }

        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            return 0;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            return text.length();
        }
    }
}
