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

package com.android.email;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;

import com.android.emailcommon.provider.EmailContent;
import com.android.email.activity.setup.EmailPreferenceActivity;
import com.android.email.preferences.EmailPreferenceMigrator;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.InlineAttachmentViewIntentBuilder;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreator;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreatorHolder;
import com.android.mail.preferences.BasePreferenceMigrator;
import com.android.mail.preferences.PreferenceMigratorHolder;
import com.android.mail.preferences.PreferenceMigratorHolder.PreferenceMigratorCreator;
import com.android.mail.providers.Account;
import com.android.mail.ui.settings.PublicPreferenceActivity;
import com.android.mail.utils.LogTag;
import com.sprd.drm.EmailDrmUtils;
import com.sprd.email.EmailAlternativeFeatureConfig;
import com.sprd.mail.vip.VipMemberCache;

public class EmailApplication extends Application {
    private static final String LOG_TAG = "Email";
    /* SPRD modify for bug706639{@ */
    private static final boolean STRICT_MODE = true;
    /* @} */

    static {
        LogTag.setLogTag(LOG_TAG);

        PreferenceMigratorHolder.setPreferenceMigratorCreator(new PreferenceMigratorCreator() {
            @Override
            public BasePreferenceMigrator createPreferenceMigrator() {
                return new EmailPreferenceMigrator();
            }
        });

        InlineAttachmentViewIntentBuilderCreatorHolder.setInlineAttachmentViewIntentCreator(
                new InlineAttachmentViewIntentBuilderCreator() {
                    @Override
                    public InlineAttachmentViewIntentBuilder
                    createInlineAttachmentViewIntentBuilder(Account account, long conversationId) {
                        return new InlineAttachmentViewIntentBuilder() {
                            @Override
                            public Intent createInlineAttachmentViewIntent(Context context,
                                    String url, ConversationMessage message) {
                                return null;
                            }
                        };
                    }
                });

        PublicPreferenceActivity.sPreferenceActivityClass = EmailPreferenceActivity.class;

        NotificationControllerCreatorHolder.setNotificationControllerCreator(
                new NotificationControllerCreator() {
                    @Override
                    public NotificationController getInstance(Context context){
                        return EmailNotificationController.getInstance(context);
                    }
                });
    }

    /* SPRD:bug477579 add to deal with drm files function. @{ */
    @Override
    public void onCreate() {
        /* SPRD modify for bug706639{@ */
        if (STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }
        /* @} */
        super.onCreate();
        /* SPRD:Modify for bug619949 NullpointerException exception after switched user @{ */
        EmailContent.init(this);
        /* @} */

        EmailAlternativeFeatureConfig.init(this);
        VipMemberCache.init(this);
        if (EmailDrmUtils.getInstance().drmPluginEnabled()) {
            EmailDrmUtils.getInstance().sendContext(this);
        }
    }
    /* @} */
}
