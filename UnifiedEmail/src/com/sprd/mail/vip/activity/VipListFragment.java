
package com.sprd.mail.vip.activity;

import java.util.ArrayList;

import com.android.mail.R;
import com.android.emailcommon.mail.Address;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.utility.EmailAsyncTask;
import com.android.emailcommon.utility.Utility;
import com.sprd.mail.compose.Contact;
import com.sprd.mail.compose.ContactsPickerActivity;
import com.sprd.mail.vip.VipMember;

import com.sprd.mail.vip.activity.VipListAdapter.SelectedVipChangedListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;

import android.widget.EditText;
import android.widget.Toast;

import android.widget.ListView;

import android.text.InputFilter;
import android.text.Spanned;
/**
 * SPRD: The Fragment was used for contain of all the VIP feature's UI components.
 */
public class VipListFragment extends ListFragment {

    private static final int REQUEST_CODE_PICK = 1;
    private static final String MANUALLY_ADD_DIALOG_TAG = "manually_add_dialog";
    /* SPRD: Modify for bug709024 @{ */
    private static final String MANUALLY_ADD_EDIT = "manually_add_edit";
    /* @} */

    /* SPRD: Modify for bug709185 @{ */
    private static final int MANUALLY_ADD_MAX_LENGTH = 512;
    /* @} */
    private Activity mActivity;
    private ListView mListView;
    private VipListAdapter mListAdapter;
    /** Arbitrary number for use with the loader manager */
    private static final int VIP_LOADER_ID = 1;
    /** Argument name(s) */
    private static final String ARG_ACCOUNT_ID = "accountId";
    /* SPRD: Modify for bug709127 @{ */
    private static final String KEY_CHECKED_MODE = "key_checked_mode_fragment";
    /* @} */
    public static final int EDITVIEW_MAX_LENGTH = 256;
    private Long mImmutableAccountId;
    private String mNewVipAddress = null;
    private boolean mIsCheckMode = false;

    public static VipListFragment newInstance(Long accountID) {
        VipListFragment f = new VipListFragment();
        Bundle bundle = new Bundle();
        bundle.putLong(ARG_ACCOUNT_ID, accountID);
        f.setArguments(bundle);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = getActivity();
        mListAdapter = new VipListAdapter(mActivity);
        setListAdapter(mListAdapter);
        mImmutableAccountId = getArguments().getLong(ARG_ACCOUNT_ID);

        setHasOptionsMenu(true);

        /* SPRD: Modify for bug709127 @{ */
        if (savedInstanceState != null) {
            mIsCheckMode = savedInstanceState.getBoolean(KEY_CHECKED_MODE, false);
            mListAdapter.restoreInstanceState(savedInstanceState);
        }
        /* @} */

        mListAdapter.setSelectedItemsChangeListener(new SelectedVipChangedListener() {

            public void onSelectedVipListenerChanged() {
                int totalCount = mListAdapter.getCount();
                int selectedCount = mListAdapter.getSelectedItems().size();
                if (!mIsCheckMode && isAdded()) {  // UNISOC: Modify for bug1186821
                    String vipTittle = getResources().getString(R.string.vip_members);
                    mActivity.setTitle(vipTittle + "(" + totalCount + ")");
                } else {
                    mActivity.setTitle(Integer.toString(selectedCount));
                }
                mActivity.invalidateOptionsMenu();
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView = getListView();
        mListView.setDivider(null);
        String emptyText = getContext().getResources().getString(R.string.vip_members_list_empty);
        setEmptyText(emptyText);
        setListShown(false);
        startLoading();
    }

    /* SPRD: Modify for bug709127 @{ */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_CHECKED_MODE, mIsCheckMode);

        if (mListAdapter != null) {
            mListAdapter.onSaveInstanceState(outState);
        }
        super.onSaveInstanceState(outState);
    }
    /* @} */

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.vip_add_contact_option, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem selectAllItem = menu.findItem(R.id.select_all);
        MenuItem cancleAllItem = menu.findItem(R.id.cancel_all);
        MenuItem deleteItem = menu.findItem(R.id.delete_contact);
        MenuItem addItem = menu.findItem(R.id.choose_contact);
        int selectedCount = mListAdapter.getSelectedItems().size();
        int totalCount = mListAdapter.getCount();
        if (mIsCheckMode) {
            selectAllItem.setVisible(selectedCount < totalCount);
            cancleAllItem.setVisible(selectedCount == totalCount && totalCount > 0);
        } else {
            selectAllItem.setVisible(false);
            cancleAllItem.setVisible(false);
        }
        addItem.setVisible(!mIsCheckMode);

        boolean shouldDisplayDelete = false;
        if (mIsCheckMode) {
            shouldDisplayDelete = selectedCount > 0;
        } else {
            shouldDisplayDelete = totalCount > 0;
        }
        deleteItem.setVisible(shouldDisplayDelete);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Activity activity = getActivity();
                activity.onBackPressed();
                break;
            case R.id.add_vip_from_contact:
                chooseVIPFromContact();
                break;
            case R.id.add_vip_manually:
                addVIPManually();
                break;
            case R.id.delete_contact:
                if (mIsCheckMode) {
                    deleteVipContacts();
                } else {
                    toogleCheckMode();
                }
                break;
            case R.id.cancel_all:
                mListAdapter.cancleAll();
                break;
            case R.id.select_all:
                mListAdapter.selectAll();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_PICK) {
            final ArrayList<Contact> selectedContacts = data
                    .getParcelableArrayListExtra(ContactsPickerActivity.KEY_CONTACTS_LIST);
            EmailAsyncTask.runAsyncParallel(new Runnable() {
                public void run() {
                    /* SPRD: Modify for bug709024 @{ */
                    saveContactsAsVips(getContext(), selectedContacts);
                    /* @} */
                }
            });

        }
    }

    public boolean handleBackKey() {
        if (mIsCheckMode) {
            toogleCheckMode();
            return true;
        }
        return false;
    }

    private void toogleCheckMode() {
        mIsCheckMode = !mIsCheckMode;
        mListAdapter.toogleCheckMode();
    }

    public void onAddVip(final Address[] addresses) {
        EmailAsyncTask.runAsyncParallel(new Runnable() {

            public void run() {
                VipListFragment.this.saveAsVips(addresses);
            }
        });
    }

    /* SPRD: Modify for bug709024 @{ */
    private static void saveContactsAsVips(Context context, String addressList) {
        ArrayList<Contact> newContacts = new ArrayList<Contact>();
        String[] newVipAdd = addressList.split(";");
        for (String nva : newVipAdd) {
            Contact contact = new Contact(nva);
            newContacts.add(contact);
        }
        saveContactsAsVips(context, newContacts);
    }

    private static void saveContactsAsVips(Context context,
            ArrayList<Contact> selectedContacts/* long[] contactIds */) {
        /* UNISOC: Modify for bug1208519 {@ */
        if (selectedContacts == null || selectedContacts.size() <= 0) {
            return;
        }
        int size = selectedContacts.size();
        /* @} */
        /** SPRD: Use for check address's validity before add vip @{ */
        boolean hasInvalidAddress = false;
        for (int i = 0; i < size; i++) {
            if (!Address.isAllValid(selectedContacts.get(i).getEmailAddress())) {
                hasInvalidAddress = true;
            }
        }
        if (hasInvalidAddress) {
            Utility.showToast(context, R.string.message_compose_error_invalid_email);
        }
        /** @} */

        VipMember.addVIPs(context, Account.ACCOUNT_ID_COMBINED_VIEW, selectedContacts,
                new VipMember.AddVipsCallback() {

                    public void tryToAddDuplicateVip() {
                        Utility.showToast(context, R.string.not_add_duplicate_vip);

                    }

                    public void addVipOverMax() {
                        /* SPRD: Modify for bug711605 @{ */
                        String message = context.getResources().getString(R.string.can_not_add_vip_over,
                                VipMember.VIP_MAX_COUNT);

                        Utility.showToast(context, message);
                        /* @} */
                    }
                });
    }
    /* @} */

    /**
     * Starts the loader.
     */
    private void startLoading() {
        final LoaderManager lm = getLoaderManager();
        lm.initLoader(VIP_LOADER_ID, null, new VipListLoaderCallbacks());
    }

    private void saveAsVips(Address[] addresses) {
        ArrayList<Address> addressList = new ArrayList<Address>();
        for (Address addr : addresses) {
            addressList.add(addr);
        }
        if (addressList.size() > 0) {
            mNewVipAddress = addressList.get(addressList.size() - 1).getAddress();
        }
    }

    private void deleteVipContacts() {
        final int selectedCount = mListAdapter.getSelectedItems().size();
        final int totalCount = mListAdapter.getCount();
        if (selectedCount > 0) {

            EmailAsyncTask<Void, Void, Void> task = new EmailAsyncTask<Void, Void, Void>(null) {

                protected Void doInBackground(Void... params) {
                    VipMember.deleteVipMembers(getContext(), mListAdapter.getSelectedItems());
                    return null;
                }

                protected void onSuccess(Void result) {
                    if (selectedCount == totalCount) {
                        toogleCheckMode();
                    }
                }
            };
            task.executeParallel();
        }
    }

    private void chooseVIPFromContact() {
        Intent intent = new Intent(getContext(), ContactsPickerActivity.class);
        startActivityForResult(intent, REQUEST_CODE_PICK);
    }

    /* SPRD: Modify for bug709024 @{ */
    private void addVIPManually() {
        VipIputFragement vipIputFragement = VipIputFragement.newInstance();
        vipIputFragement.showAllowingStateLoss(mActivity.getFragmentManager(),
                MANUALLY_ADD_DIALOG_TAG);
    }
    /* @} */

    private class VipListLoaderCallbacks implements LoaderCallbacks<Cursor> {

        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return VipListAdapter.createVipContentLoader(getActivity(), mImmutableAccountId);
        }

        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // Always swap out the cursor so we don't hold a reference to a stale one.
            mListAdapter.swapCursor(data);
            setListShown(true);

            // Scroll to the new added vip item.
            if (mNewVipAddress != null) {
                int position = mListAdapter.getPosition(mNewVipAddress);
                if (position != -1) {
                    mListView.setSelection(position);
                }
                mNewVipAddress = null;
            }

        }

        public void onLoaderReset(Loader<Cursor> loader) {
            mListAdapter.swapCursor(null);

        }
    }

    /* SPRD: Modify for bug709024 @{ */
    public static class VipIputFragement extends DialogFragment {
        EditText mVipEdit;
        /* SPRD: Modify for bug710593 @{ */
        Toast mToast;
        /* @} */

        public VipIputFragement() {
        }

        public static VipIputFragement newInstance() {
            return new VipIputFragement();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getContext();
             /* SPRD: Modify for bug 803961  @{ */
            final View wrapper = LayoutInflater.from(context)
                 .inflate(R.layout.add_vip_edit_dialog, null);
            mVipEdit = (EditText) wrapper.findViewById(R.id.add_vip_text);
            /* @} */
            /* SPRD: Modify for bug709185, 710593 @{ */
            mVipEdit.setFilters(new InputFilter[] {
                    new InputFilter.LengthFilter(MANUALLY_ADD_MAX_LENGTH) {
                        @Override
                        public CharSequence filter(CharSequence source, int start, int end,
                                Spanned dest, int dstart, int dend) {
                            if (source != null && source.length() > 0
                                    && (((dest == null ? 0 : dest.length()) + dstart
                                            - dend) == getMax())) {
                                if (mToast != null) {
                                    mToast.cancel();
                                }
                                mToast = Toast.makeText(
                                        context, context.getResources()
                                                .getString(R.string.body_input_more, getMax()),
                                        Toast.LENGTH_SHORT);
                                mToast.show();
                                return "";
                            } else {
                                /* UNISOC: Modify for bug1208519 {@ */
                                if (dest != null && source != null) {
                                    return super.filter(source, start, end, dest, dstart, dend);
                                }
                                return "";
                                /* @} */
                            }
                        }
                    }
            });
            /* @} */
            if (savedInstanceState != null) {
                mVipEdit.setText(savedInstanceState.getString(MANUALLY_ADD_EDIT), null);
                mVipEdit.setSelection(mVipEdit.getText().toString().trim().length());
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
             /* SPRD: Modify for bug 803961  @{ */
            builder.setTitle(R.string.vip_add_from_input).setView(wrapper)
                    .setPositiveButton(R.string.save_action, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            final String input = mVipEdit.getText().toString().trim();
                            /* SPRD: Modify for bug709733 @{ */
                            if (TextUtils.isEmpty(input)) {
                                Toast.makeText(context.getApplicationContext(),
                                        context.getString(
                                                R.string.account_setup_names_user_name_empty_error),
                                        Toast.LENGTH_SHORT).show();
                            } else if (!Address.isAllValid(input)) {
                                Toast.makeText(context.getApplicationContext(), context.getResources()
                                        .getString(R.string.message_compose_error_invalid_email),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                EmailAsyncTask.runAsyncParallel(new Runnable() {
                                    @Override
                                    public void run() {
                                        VipListFragment.saveContactsAsVips(context, input);
                                    }
                                });
                            }
                            /* @} */
                        }
                    }).setNegativeButton(R.string.cancel_action,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
            /* @} */
            return builder.create();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (mVipEdit != null) {
                outState.putString(MANUALLY_ADD_EDIT, mVipEdit.getText().toString().trim());
            }
            super.onSaveInstanceState(outState);
        }
    }
    /* @} */
}
