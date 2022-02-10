
package com.sprd.mail.compose;

import java.util.ArrayList;
import java.util.Collection;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;

import android.os.Bundle;
import com.android.mail.R;
import com.android.mail.utils.FragmentStatePagerAdapter2;

import com.sprd.mail.RequestPermissionsActivity;
import com.sprd.mail.ui.TabNagationView;
import com.sprd.mail.ui.TabNagationView.TabSelectedListener;

public class ContactsPickerActivity extends AppCompatActivity implements
        ViewPager.OnPageChangeListener {

    public static final String KEY_CONTACTS_LIST = "key_contacts_list";
    public static final int ALL_CONTACTS_PAGE_INDEX = 0;
    public static final int FAVORITY_CONTACTS_PAGE_INDEX = 1;
    public static final int MAX_CHECKED_COUNT = 100;
    /* SPRD: Modify for bug749153 @{ */
    private static Activity mLastActivity = null;
    /* @} */

    ActionBar mActionBar;
    ViewPager mPager;
    TabNagationView mTabNavagationView;
    ContactsPickerPagerAdapter mPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD modify for bug709634 {@ */
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        /* @} */

        /* SPRD: Modify for bug749153 @{ */
        if (ActivityManager.isUserAMonkey()) {
            if (mLastActivity != null) {
                mLastActivity.finish();
            }
            mLastActivity = this;
        }
        /* @} */

        setContentView(R.layout.contacts_picker_activity);
        mTabNavagationView = (TabNagationView) findViewById(R.id.pager_tab);
        Resources resource = getResources();
        mTabNavagationView.addTab(resource.getString(R.string.contacts_tab_tittle));
        mTabNavagationView.addTab(resource.getString(R.string.favorite_tab_tittle));
        mTabNavagationView.seletcTab(0);

        mActionBar = getSupportActionBar();
        mActionBar.setHomeButtonEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);

        mPager = (ViewPager) findViewById(R.id.contacts_picker_pager);
        mPagerAdapter = new ContactsPickerPagerAdapter(getFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mTabNavagationView.setOntabSelectedListener(new TabSelectedListener() {

            public void onTabSelected(int posotion) {
                final int currentItem = mPager.getCurrentItem();
                if (currentItem != posotion) {
                    mPager.setCurrentItem(posotion);
                }
            }
        });

        mPager.setOnPageChangeListener(this);

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        mTabNavagationView.seletcTab(position);
        /* SPRD modify fot bug709049 {@ */
        MultiCheckContactsPickerFragment fragment = (MultiCheckContactsPickerFragment) mPagerAdapter
                .getFragmentAt(position);
        if (fragment != null) {
            mActionBar.setTitle(fragment.getCheckedContactsCount() + "/"
                    + MAX_CHECKED_COUNT);
        }
        /* @} */
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void onContactsSelected(Collection<Contact> contacts) {
        if (!contacts.isEmpty()) {
            Intent intent = new Intent();
            ArrayList<Contact> list = new ArrayList<Contact>(contacts.size());
            list.addAll(contacts);
            intent.putParcelableArrayListExtra(KEY_CONTACTS_LIST, list);
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED);
        }
        /* UNISOC: Modify for bug 1146764 {@ */
        View view = getWindow().peekDecorView();
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager.isActive()) {
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(),0);
            }
        }
        /* @} */
        finish();
    }

    public void onCheckedStateChanged() {
        invalidateOptionsMenu();
        /* SPRD modify for bug709634 bug709049{@ */
        if (mPager != null && mActionBar != null) {
            final int currentItem = mPager.getCurrentItem();
            MultiCheckContactsPickerFragment fragment = (MultiCheckContactsPickerFragment) mPagerAdapter
                    .getFragmentAt(currentItem);

            if (fragment != null) {
                mActionBar.setTitle(fragment.getCheckedContactsCount() + "/" + MAX_CHECKED_COUNT);
            }
        }
        /* @} */
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static class ContactsPickerPagerAdapter extends FragmentStatePagerAdapter2 {

        private static final int TAB_COUNT = 2;

        MultiCheckContactsPickerFragment mAllContactsFragment;
        MultiCheckContactsPickerFragment mFavoriteFragment;

        public ContactsPickerPagerAdapter(FragmentManager fm) {
            super(fm);
            mAllContactsFragment = new MultiCheckContactsPickerFragment(false);
            mFavoriteFragment = new MultiCheckContactsPickerFragment(true);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == ALL_CONTACTS_PAGE_INDEX) {
                return mAllContactsFragment;
            } else {
                return mFavoriteFragment;
            }
        }

        public int getCount() {
            return TAB_COUNT;
        }
    }
}
