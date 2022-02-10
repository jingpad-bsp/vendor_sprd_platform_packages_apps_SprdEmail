
package com.sprd.mail.compose;

import java.util.ArrayList;

import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import com.android.mail.R;
import com.sprd.mail.compose.MultiCheckContactsPickerAdapter.OnCheckStateChangeListener;

public class MultiCheckContactsPickerFragment extends Fragment {

    private static final int LOADER_CONTACTS = 0;

    private static final int MSG_SERARCH = 0;
    private static final long SERARCH_MSG_DELAY = 200;

    private static final String KEY_STARED_ONLY = "stared_only";

    // private static final String EMAIL_NOT_NULL_SELECTION = Email.ADDRESS + "<>null";
    private static final String STARED_SELECTION = Email.STARRED + "=1";

    private ListView mListView;
    private EditText mSerachEdit;
    private ImageButton mClearSearchButton;
    private MultiCheckContactsPickerAdapter mAdapter;
    private boolean mIsStaredOnly;
    private Handler mHandler;
    private TextWatcher mSearchTextWatcher = new TextWatcher() {

        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {

            mHandler.removeMessages(MSG_SERARCH);
            mHandler.sendEmptyMessageDelayed(MSG_SERARCH, SERARCH_MSG_DELAY);

        }
    };

    private LoaderCallbacks<Cursor> mContactsLoaderCallback = new LoaderCallbacks<Cursor>() {

        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return createCursorLoader();
        }

        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            ArrayList<Contact> allContacts = Contact.getContactsFromCursor(data);
            mAdapter.updateData(allContacts);

        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }

    };

    public MultiCheckContactsPickerFragment() {
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MSG_SERARCH) {
                    restartLoader();
                }
            }
        };
    }

    public MultiCheckContactsPickerFragment(boolean staredOnly) {
        this();
        mIsStaredOnly = staredOnly;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        restartLoader();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSerachEdit.addTextChangedListener(mSearchTextWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSerachEdit.removeTextChangedListener(mSearchTextWatcher);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeMessages(MSG_SERARCH);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.contacts_picker_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem checkAllItem = menu.findItem(R.id.select_all);
        MenuItem cancleAllItem = menu.findItem(R.id.cancel_all);
        MenuItem doneItem = menu.findItem(R.id.done);
        int checkedCount = mAdapter.getCheckedContactsCount();
        int count = mAdapter.getCount();

        cancleAllItem.setVisible(count > 0 && checkedCount == count);
        doneItem.setVisible(checkedCount > 0);
        checkAllItem.setVisible(count > 0 && checkedCount != count
                && checkedCount < MultiCheckContactsPickerAdapter.MAX_CHECKED_COUNT);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = false;
        int id = item.getItemId();
        switch (id) {
            case R.id.select_all:
                mAdapter.checkAll();
                break;
            case R.id.cancel_all:
                mAdapter.cancleAll();
                break;
            case R.id.done:
                onContactsSelcted();
                break;
            default:
                handled = true;
                break;
        }
        return handled || super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.contacts_picker_fragment, null);
        mSerachEdit = (EditText) rootView.findViewById(R.id.contacts_picker_search_edit);
        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mAdapter = new MultiCheckContactsPickerAdapter(getActivity());
        mListView.setAdapter(mAdapter);
        mClearSearchButton = (ImageButton) rootView.findViewById(R.id.contacts_picker_clear_button);
        mClearSearchButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                mSerachEdit.setText("");
            }
        });

        mAdapter.setOnCheckStateListener(new OnCheckStateChangeListener() {

            public void onCheckedStateChanged() {
                ContactsPickerActivity activity = (ContactsPickerActivity) getActivity();
                if (activity != null) {
                    activity.onCheckedStateChanged();
                }
            }
        });
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mAdapter.restoreInstanceState(savedInstanceState);
            mIsStaredOnly = savedInstanceState.getBoolean(KEY_STARED_ONLY);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_STARED_ONLY, mIsStaredOnly);
        if (mAdapter != null) {
            mAdapter.onSaveInstanceState(outState);
        }
    }

    public int getContactsCount() {
        return mAdapter == null ? 0 : mAdapter.getCount();
    }

    public int getCheckedContactsCount() {
        return mAdapter == null ? 0 : mAdapter.getCheckedContactsCount();
    }

    private CursorLoader createCursorLoader() {
        CursorLoader loader = new CursorLoader(getContext());
        String filter = mSerachEdit.getText().toString();
        if (TextUtils.isEmpty(filter)) {
            loader.setUri(Email.CONTENT_URI);
        } else {
            /* SPRD modify for bug709711{@ */
            loader.setUri(Email.CONTENT_FILTER_URI.buildUpon()
                    .appendEncodedPath(Uri.encode(filter)).build());
            /* @} */
        }
        loader.setProjection(ContactEmailQuery.EMAIL_PROJECTION);

        if (mIsStaredOnly) {
            loader.setSelection(STARED_SELECTION);
        }
        loader.setSortOrder(Email.DISPLAY_NAME + " ASC");
        return loader;
    }

    private void restartLoader() {
        LoaderManager lm = getLoaderManager();
        lm.destroyLoader(LOADER_CONTACTS);
        lm.initLoader(LOADER_CONTACTS, null, mContactsLoaderCallback);
    }

    private void onContactsSelcted() {
        ContactsPickerActivity activity = (ContactsPickerActivity) getActivity();
        if (activity != null) {
            activity.onContactsSelected(mAdapter.getCheckedContacts());
        }
    }

}
