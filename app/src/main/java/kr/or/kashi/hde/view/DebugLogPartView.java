/*
 * Copyright (C) 2023 Korea Association of AI Smart Home.
 * Copyright (C) 2023 KyungDong Navien Co, Ltd.
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

package kr.or.kashi.hde.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.R;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.LocalPreferences;
import kr.or.kashi.hde.widget.DebugLogView;

public class DebugLogPartView extends LinearLayout {
    private static final String TAG = DebugLogPartView.class.getSimpleName();

    private final Context mContext;
    private final Handler mHandler;

    private CheckBox mEventLogCheck;
    private CheckBox mTxRxLogCheck;
    private ToggleButton mAutoScrollToggle;
    private DebugLogView mDebugLogView;

    public DebugLogPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init() {
        findViewById(R.id.clear_log_button).setOnClickListener(view -> {
            mDebugLogView.clear();
            mAutoScrollToggle.setChecked(true);
            mDebugLogView.setAutoScroll(true);
        });

        mEventLogCheck = findViewById(R.id.event_log_check);
        mEventLogCheck.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.DEBUG_LOG_EVENT_ENABLED, true));
        mEventLogCheck.setOnClickListener(view -> updateLogFilter());
        mTxRxLogCheck = findViewById(R.id.txrx_log_check);
        mTxRxLogCheck.setChecked(LocalPreferences.getBoolean(LocalPreferences.Pref.DEBUG_LOG_TXRX_ENABLED, true));
        mTxRxLogCheck.setOnClickListener(view -> updateLogFilter());
        mAutoScrollToggle = findViewById(R.id.auto_scroll_toggle);;
        mAutoScrollToggle.setOnClickListener(view -> mDebugLogView.setAutoScroll(((Checkable)view).isChecked()));

        mDebugLogView = findViewById(R.id.debug_log_view);
        DebugLog.setLogger(mDebugLogView);
        mDebugLogView.setOnRawTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                mAutoScrollToggle.setChecked(false);
                mDebugLogView.setAutoScroll(false);
            }
            return false;
        });

        updateLogFilter();
    }

    private void updateLogFilter() {
        int logFilter = 0;
        if (mEventLogCheck.isChecked()) logFilter |= DebugLog.EVENT;
        if (mTxRxLogCheck.isChecked()) logFilter |= DebugLog.TXRX;
        mDebugLogView.setFilter(logFilter);

        LocalPreferences.putBoolean(LocalPreferences.Pref.DEBUG_LOG_EVENT_ENABLED, mEventLogCheck.isChecked());
        LocalPreferences.putBoolean(LocalPreferences.Pref.DEBUG_LOG_TXRX_ENABLED, mTxRxLogCheck.isChecked());
    }
}
