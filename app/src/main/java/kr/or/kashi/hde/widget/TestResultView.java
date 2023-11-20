/*
 * Copyright (C) 2022 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.test.DeviceTestCallback;
import kr.or.kashi.hde.util.DebugLog;


public class TestResultView extends ScrollView implements DeviceTestCallback  {
    private static final String TAG = TestResultView.class.getSimpleName();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mScrollRunnable = this::scrollToLast;
    private TextView mTestResultText;
    private long mScrollDelay = 200;

    public TestResultView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();

        mTestResultText = (TextView)findViewById(R.id.test_result_text);
        mTestResultText.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public void onTestRunnerStarted() {
        clear();
    }

    @Override
    public void onTestRunnerFinished() {
    }

    @Override
    public void onDeviceTestStarted(HomeDevice device) {
        MySpannableStringBuilder text = new MySpannableStringBuilder();

        text.append("\u0387 "); // center dot
        text.append("Device (");
        text.append(HomeDevice.typeToString(device.getType()));
        text.append(", ");
        text.append(device.getAddress());
        text.append(")");
        text.append("\n");

        mTestResultText.append(text);
        triggerAutoScrollIf();
    }

    @Override
    public void onDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {
        MySpannableStringBuilder text = new MySpannableStringBuilder();

        text.append("\t");
        text.append("- ");
        text.append(test.getName());
        text.append(" ... ");

        if (result.wasSuccessful()) {
            text.appendColored("PASS", Color.BLUE);
        } else  {
            if (hasUnsupportedOperationException(test, result)) {
                text.appendColored("UNSUPPORTED", Color.GRAY);
            } else {
                text.appendColored("FAIL", Color.RED);
            }
        }

        text.append("\n");

        mTestResultText.append(text);
        triggerAutoScrollIf();
    }

    private static boolean hasUnsupportedOperationException(Test test, TestResult result) {
        for (TestFailure fail: Collections.list(result.errors())) {
            if (fail.failedTest() == test) {
                if (fail.thrownException() instanceof UnsupportedOperationException) {
                    return true;
                }
            }
        }
        return false;
    }

    public void clear() {
        mTestResultText.setText("");
    }

    public void setTextSize(int textSizeSp) {
        mTestResultText.setTextSize(textSizeSp);
    }

    private void triggerAutoScrollIf() {
        if (mScrollDelay <= 0) {
            scrollToLast();
        } else if (!mHandler.hasCallbacks(mScrollRunnable)) {
            mHandler.postDelayed(mScrollRunnable, mScrollDelay);
        }
    }

    private void scrollToLast() {
        int bottom = mTestResultText.getBottom() + getPaddingBottom();
        int delta = bottom - (getScrollY() + getHeight());
        smoothScrollBy(0, delta);
    }
}
