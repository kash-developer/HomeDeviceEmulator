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

package kr.or.kashi.hde.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import kr.or.kashi.hde.util.DebugLog;

public class DebugLogView extends RecyclerView implements DebugLog {
    private static final String TAG = "DebugLogView";

    private final DebugLogAdapter mAdapter;
    private final LinearLayoutManager mLayoutManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OnTouchListener mOnRawTouchListener;
    private final Runnable mScrollRunnable = this::scrollToLast;
    private int mFilteredTypes = 0;
    private boolean mAutoScrollOn = true;
    private long mScrollDelay = 200;

    public DebugLogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAdapter = new DebugLogAdapter(context);
        mLayoutManager = new LinearLayoutManager(context);
        setLayoutManager(mLayoutManager);
        setAdapter(mAdapter);
        setItemAnimator(null);
        setHasFixedSize(true);
    }

    public void setOnRawTouchListener(OnTouchListener l) {
        mOnRawTouchListener = l;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOnRawTouchListener != null) {
            mOnRawTouchListener.onTouch(this, event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onPrint(int type, String text) {
        if ((mFilteredTypes & type) != type) {
            return;
        }

        mHandler.post(() -> {
            int oldPos = mLayoutManager.findFirstVisibleItemPosition();
            int oldCount = mAdapter.getLogCount();

            mAdapter.addLog(text);

            int newCount = mAdapter.getLogCount();

            boolean autoScrolled = triggerAutoScrollIf();
            if (!autoScrolled && oldCount > newCount) {
//                int reduce = oldCount - newCount;
//                int newPos = oldPos + reduce;
//                scrollToPosition(newPos);
            }
        });
    }

    public void clear() {
        mAdapter.clearAll();
    }

    public void setFilter(int typeBits) {
        mFilteredTypes = typeBits;
    }

    public void setAutoScroll(boolean on) {
        if (on == mAutoScrollOn) return;
        mAutoScrollOn = on;

        if (on == false) {
            mHandler.removeCallbacks(mScrollRunnable);
        } else {
            triggerAutoScrollIf();
        }
    }

    public void setScrollDelay(long delayMs) {
        mScrollDelay = delayMs;
    }

    public void setTextSize(int textSizeSp) {
//        mDebugLogText.setTextSize(textSizeSp);
    }

    private boolean triggerAutoScrollIf() {
        if (!mAutoScrollOn) {
            return false;
        }

        if (mScrollDelay <= 0) {
            scrollToLast();
        } else if (!mHandler.hasCallbacks(mScrollRunnable)) {
            mHandler.postDelayed(mScrollRunnable, mScrollDelay);
        }

        return true;
    }

    private void scrollToLast() {
        scrollToPosition(mAdapter.getItemCount() - 1);
    }
}
