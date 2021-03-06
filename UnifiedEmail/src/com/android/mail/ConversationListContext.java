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

package com.android.mail;

import android.content.Context;
import android.content.UriMatcher;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.mail.preferences.FolderPreferences;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;
import com.google.common.base.Preconditions;
import com.android.emailcommon.service.SearchParams;

/**
 * This class is supposed to have the same thing that the Gmail ConversationListContext
 * contained. For now, it has no implementation at all. The goal is to bring over functionality
 * as required.
 *
 * Original purpose:
 * An encapsulation over a request to a list of conversations and the various states around it.
 * This includes the folder the user selected to view the list, or the search query for the
 * list, etc.
 */
public class ConversationListContext {
    public static final String EXTRA_SEARCH_QUERY = "query";

    /* SPRD:bug475886 add local search function @{ */
    public static final String EXTRA_SEARCH_APPROACH = "approach";
    public static final String EXTRA_SEARCH_IS_LOCAL = "is_local";
    /* @} */

    /**
     * A matcher for data URI's that specify conversation list info.
     */
    private static final UriMatcher sUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    /**
     * The account for whom we are showing a list
     */
    public final Account account;
    /**
     * The folder whose conversations we are displaying, if any.
     */
    public final Folder folder;
    /**
     * The search query whose results we are displaying, if any.
     */
    public String searchQuery;

    /* SPRD:bug475886 add local search function @{ */
    /**
     * The approach whose results we are displaying, if any.
     */
    public int searchApproach;

    /**
     * Whether search query is local
     */
    public boolean isLocalSearch;
    /* @} */

    public int sortOrder;
    static {
        sUrlMatcher.addURI(UIProvider.AUTHORITY, "account/*/folder/*", 0);
    }

    /**
     * De-serializes a context from a bundle.
     */
    public static ConversationListContext forBundle(Context context, Bundle bundle) {
        // The account is created here as a new object. This is probably not the best thing to do.
        // We should probably be reading an account instance from our controller.
        Account account = bundle.getParcelable(Utils.EXTRA_ACCOUNT);
        Folder folder = bundle.getParcelable(Utils.EXTRA_FOLDER);
        /* SPRD:bug475886 add local search function @{ */
        if (Utils.SUPPORT_LOCAL_SEARCH) {
            boolean isLocal = bundle.getBoolean(EXTRA_SEARCH_IS_LOCAL, true);
            ConversationListContext listContext = new ConversationListContext(context, account,
                    bundle.getString(EXTRA_SEARCH_QUERY), folder);
            listContext.setLocalSearch(isLocal);
            return listContext;
        } else {
            return new ConversationListContext(context, account,
                    bundle.getString(EXTRA_SEARCH_QUERY), folder);
        }
        /* @} */
    }

    /**
     * Builds a context for a view to a Gmail folder.
     *
     * @param context
     * @param account
     * @param folder
     * @return
     */
    public static ConversationListContext forFolder(Context context, Account account, Folder folder) {
        /* SPRD:bug475886 add local search function @{ */
        if (Utils.SUPPORT_LOCAL_SEARCH) {
            ConversationListContext listContext = new ConversationListContext(context, account,
                    null, folder);
            listContext.setLocalSearch(false);
            listContext.setSearchApproach(SearchParams.INVILID_SEARCH_APPROACH);
            return listContext;
        } else {
            return new ConversationListContext(context, account, null, folder);
        }
        /* @} */
    }

    /**
     * Builds a context object for viewing a conversation list for a search query.
     */
    public static ConversationListContext forSearchQuery(Context context, Account account,
            Folder folder,
            String query) {
        /* SPRD:bug475886 add local search function @{ */
        if (Utils.SUPPORT_LOCAL_SEARCH) {
            ConversationListContext listContext = new ConversationListContext(context, account,
                    Preconditions.checkNotNull(query), folder);
            listContext.setLocalSearch(true);
            listContext.setSearchApproach(SearchParams.DEFAULT_SEARCH_APPROACH);
            return listContext;
        } else {
            return new ConversationListContext(context, account, Preconditions.checkNotNull(query),
                    folder);
        }
        /* @} */
    }


    /**
     * Internal constructor To create a class, use the static {@link #forFolder} or
     * {@link #forBundle(Bundle)} method.
     *
     * @param context
     * @param a
     * @param query
     * @param f
     */
    private ConversationListContext(Context context, Account a, String query, Folder f) {
        account = a;
        searchQuery = query;
        folder = f;
        if (a != null && f != null) {
            FolderPreferences perf = new FolderPreferences(context, a.getAccountId(),
                    f, f.isInbox());
            sortOrder = perf.getSortOrder();
        }
    }

    /* SPRD:bug475886 add local search function @{ */
    /**
     * Returns true if the provided context represents search results.
     * @param in
     * @return true the context represents search results. False otherwise
     */
    public static final boolean isSearchResult(ConversationListContext in) {
        return in != null && !in.isLocalSearch && !TextUtils.isEmpty(in.searchQuery);
    }

    /**
     * Returns true if the provided context represents local search results.
     *
     * @param in
     * @return true the context represents local search results. False otherwise
     */
    public static final boolean isLocalSearchResult(ConversationListContext in) {
        return in != null && in.isLocalSearch && !TextUtils.isEmpty(in.searchQuery);
    }

    /**
     * Serializes the context to a bundle.
     */
    public Bundle toBundle() {
        Bundle result = new Bundle();
        result.putParcelable(Utils.EXTRA_ACCOUNT, account);
        result.putString(EXTRA_SEARCH_QUERY, searchQuery);
        result.putParcelable(Utils.EXTRA_FOLDER, folder);
        result.putInt(EXTRA_SEARCH_APPROACH, searchApproach);
        result.putBoolean(EXTRA_SEARCH_IS_LOCAL, isLocalSearch);
        return result;
    }

    public void setSearchApproach(int searchApproach) {
        this.searchApproach = searchApproach;
    }

    public void setLocalSearch(boolean isLocalSearch) {
        this.isLocalSearch = isLocalSearch;
    }

    public void setQuery(String query) {
        this.searchQuery = query;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "[searchQuery]: " + searchQuery + " [searchApproach]: " + searchApproach
                + " [isLocal]: " + isLocalSearch + " [Folder]:"
                + folder + " [Account]" + account + "[sortOrder]" + sortOrder;
    }
    /* @} */
}
