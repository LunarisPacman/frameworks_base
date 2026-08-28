/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar.notification;

import android.app.Notification;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;

import com.android.systemui.DejankUtils;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.statusbar.notification.collection.EntryAdapter;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.stack.AmbientState;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;

import javax.inject.Inject;

/**
 * Click handler for generic clicks on notifications. Clicks on specific areas (expansion caret,
 * app ops icon, etc) are handled elsewhere.
 */
public final class NotificationClicker implements View.OnClickListener {
    private static final String TAG = "NotificationClicker";

    private final NotificationClickerLogger mLogger;
    private final PowerInteractor mPowerInteractor;
    private final NotificationActivityStarter mNotificationActivityStarter;
    private final AmbientState mAmbientState;

    private ExpandableNotificationRow.OnDragSuccessListener mOnDragSuccessListener
            = new ExpandableNotificationRow.OnDragSuccessListener() {
        @Override
        public void onDragSuccess(NotificationEntry entry) {
            NotificationBundleUi.assertInLegacyMode();
            mNotificationActivityStarter.onDragSuccess(entry);
        }

        @Override
        public void onDragSuccess(EntryAdapter entryAdapter) {
            NotificationBundleUi.isUnexpectedlyInLegacyMode();
            entryAdapter.onDragSuccess();
        }
    };

    private NotificationClicker(
            NotificationClickerLogger logger,
            PowerInteractor powerInteractor,
            NotificationActivityStarter notificationActivityStarter,
            AmbientState ambientState) {
        mLogger = logger;
        mPowerInteractor = powerInteractor;
        mNotificationActivityStarter = notificationActivityStarter;
        mAmbientState = ambientState;
    }

    @Override
    public void onClick(final View v) {
        if (!(v instanceof ExpandableNotificationRow)) {
            Log.e(TAG, "NotificationClicker called on a view that is not a notification row.");
            return;
        }

        final ExpandableNotificationRow row = (ExpandableNotificationRow) v;
        if (!row.isSummaryWithChildren() && row.areChildrenExpanded()) {
            // We never want to open the app directly if the user clicks in between
            // the notifications.
            mLogger.logChildrenExpanded(row.getLoggingKey());
            return;
        } else if (row.areGutsExposed()) {
            // ignore click if guts are exposed
            mLogger.logGutsExposed(row.getLoggingKey());
            return;
        }

        // Lockscreen Stack Style expansion handling: expand stack on lockscreen when clicked
        boolean isLockscreenStackActive = mAmbientState != null
                && mAmbientState.isOnKeyguard()
                && !mAmbientState.isShadeExpanded()
                && mAmbientState.getFractionToShade() == 0f
                && mAmbientState.getQsExpansionFraction() == 0f
                && mAmbientState.isLockscreenNotifStyleStack()
                && !isOngoingRow(row);

        if (isLockscreenStackActive) {
            NotificationStackScrollLayout nssl = getStackScrollLayout(v);
            if (!mAmbientState.isLockscreenNotifStackExpanded()) {
                // Stack is currently collapsed: expand the stack on lockscreen with fluid animation!
                if (nssl != null) {
                    nssl.setLockscreenNotifStackExpanded(true);
                } else {
                    mAmbientState.setLockscreenNotifStackExpanded(true);
                }
                return;
            } else {
                // Stack is ALREADY expanded on lockscreen:
                // Tapping top card re-collapses back to stacked deck
                if (nssl != null) {
                    ExpandableView firstChild = nssl.getFirstChildNotGone();
                    if (firstChild == row || (row.isChildInGroup() && firstChild == row.getNotificationParent())) {
                        nssl.setLockscreenNotifStackExpanded(false);
                        return;
                    }
                }
            }
        }

        // Mark notification for one frame.
        row.setJustClicked(true);
        DejankUtils.postAfterTraversal(() -> row.setJustClicked(false));

        if (NotificationBundleUi.isEnabled()) {
            row.getEntryAdapter().onEntryClicked(row);
        } else {
            mNotificationActivityStarter.onNotificationClicked(row.getEntryLegacy(), row);
        }
    }

    private NotificationStackScrollLayout getStackScrollLayout(View v) {
        View current = v;
        while (current != null) {
            if (current instanceof NotificationStackScrollLayout) {
                return (NotificationStackScrollLayout) current;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        View root = v.getRootView();
        if (root != null) {
            View stack = root.findViewById(R.id.notification_stack_scroller);
            if (stack instanceof NotificationStackScrollLayout) {
                return (NotificationStackScrollLayout) stack;
            }
        }
        return null;
    }

    private boolean isOngoingRow(ExpandableNotificationRow row) {
        if (row == null) return false;
        StatusBarNotification sbn = NotificationBundleUi.isEnabled()
                ? (row.getEntryAdapter() != null ? row.getEntryAdapter().getSbn() : null)
                : (row.getEntry() != null ? row.getEntry().getSbn() : null);
        return sbn != null && sbn.isOngoing();
    }

    private boolean isMenuVisible(ExpandableNotificationRow row) {
        return row.getProvider() != null && row.getProvider().isMenuVisible();
    }

    /**
     * Attaches the click listener to the row if appropriate.
     */
    public void register(ExpandableNotificationRow row, StatusBarNotification sbn) {
        boolean isBubble = NotificationBundleUi.isEnabled()
                ? row.getEntryAdapter().isBubble()
                : row.getEntryLegacy().isBubble();
        Notification notification = sbn.getNotification();
        if (notification.contentIntent != null || notification.fullScreenIntent != null
                || isBubble) {
            if (NotificationBundleUi.isEnabled()) {
                row.setBubbleClickListener(
                        v -> row.getEntryAdapter().onNotificationBubbleIconClicked());
            } else {
                row.setBubbleClickListener(v ->
                        mNotificationActivityStarter.onNotificationBubbleIconClicked(
                                row.getEntryLegacy()));
            }
            row.setOnClickListener(this);
            row.setOnDragSuccessListener(mOnDragSuccessListener);
        } else {
            row.setOnClickListener(null);
            row.setOnDragSuccessListener(null);
            row.setBubbleClickListener(null);
        }
    }

    /** Daggerized builder for NotificationClicker. */
    public static class Builder {
        private final NotificationClickerLogger mLogger;
        private final PowerInteractor mPowerInteractor;
        private final AmbientState mAmbientState;

        @Inject
        public Builder(NotificationClickerLogger logger, PowerInteractor powerInteractor, AmbientState ambientState) {
            mLogger = logger;
            mPowerInteractor = powerInteractor;
            mAmbientState = ambientState;
        }

        /** Builds an instance. */
        public NotificationClicker build(NotificationActivityStarter notificationActivityStarter) {
            return new NotificationClicker(
                    mLogger,
                    mPowerInteractor,
                    notificationActivityStarter,
                    mAmbientState);
        }
    }
}
