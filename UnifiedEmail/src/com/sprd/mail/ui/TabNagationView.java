
package com.sprd.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;

public class TabNagationView extends LinearLayout {

    private Context mContext;
    private TabSelectedListener mListener;
    private View mSelectedTab;

    private OnClickListener mTabClickListener = new OnClickListener() {

        public void onClick(View v) {
            /* UNISOC: modify for bug1208588 {@ */
            if (mSelectedTab != v && v instanceof SelectableTab) {
                SelectableTab tab = (SelectableTab) v;
                selectTab(tab);
                int posption = tab.mPosition;
                if (mListener != null) {
                    mListener.onTabSelected(posption);
                }
            }
            /* @} */
        }
    };

    public TabNagationView(Context context) {
        this(context, null, android.R.attr.actionBarTabStyle);

    }

    public TabNagationView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.actionBarTabStyle);

    }

    public TabNagationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        setOrientation(LinearLayout.HORIZONTAL);
    }

    public void addTab(String description) {
        SelectableTab tab = new SelectableTab(mContext);
        tab.mPosition = getChildCount();
        tab.setText(description);
        super.addView(tab, -1, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
        tab.setOnClickListener(mTabClickListener);
    }

    public void seletcTab(int posotion) {
        SelectableTab tab = (SelectableTab) getChildAt(posotion);
        selectTab(tab);
        if (mListener != null) {
            mListener.onTabSelected(posotion);
        }
    }

    private void selectTab(SelectableTab tab) {
        if (mSelectedTab != null) {
            mSelectedTab.setSelected(false);
        }

        tab.setSelected(true);
        mSelectedTab = tab;
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        // Addview being called from outside is forbided. Since all other addview method will call
        // this method
        // eventually, just thorw UnsupportedOperationException here
        throw new UnsupportedOperationException("Only addTab is avaliable from outside ");
    }

    public void setOntabSelectedListener(TabSelectedListener listener) {
        mListener = listener;
    }

    private static class SelectableTab extends FrameLayout {

        public int mPosition;

        private TextView mText;
        private View mSelectedIndicator;

        public SelectableTab(Context context) {
            this(context, null);
        }

        public SelectableTab(Context context, AttributeSet attrs) {
            this(context, attrs, -1);
        }

        public SelectableTab(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            LayoutInflater.from(context).inflate(R.layout.selectable_tab, this);
            mText = (TextView) findViewById(R.id.tab_title);
            mSelectedIndicator = findViewById(R.id.selected_indicator);
        }

        @Override
        public void setSelected(boolean selected) {
            boolean isSelectedNow = isSelected();
            if (selected != isSelectedNow) {
                super.setSelected(selected);
                mSelectedIndicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            }
        }

        public void setText(String text) {
            mText.setText(text);
        }

    }

    public interface TabSelectedListener {
        void onTabSelected(int posotion);
    }
}
