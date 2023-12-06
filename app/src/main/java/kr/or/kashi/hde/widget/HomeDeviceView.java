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
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.util.LocalPreferences;

public class HomeDeviceView<T extends HomeDevice> extends LinearLayout implements HomeDevice.Callback {
    private static final String TAG = HomeDeviceView.class.getSimpleName();
    private final Context mContext;
    protected T mDevice;
    protected boolean mIsSlave;

    public HomeDeviceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mIsSlave = (LocalPreferences.getInt(LocalPreferences.Pref.MODE_INDEX) == 1);
    }

    public boolean isSlave() {
        return mIsSlave;
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mDevice != null) {
            mDevice.addCallback(this);
            final PropertyMap props = mDevice.getReadPropertyMap();
            onUpdateProperty(props, props);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mDevice != null) {
            mDevice.removeCallback(this);
        }
    }

    @Override
    public void onPropertyChanged(HomeDevice device, PropertyMap props) {
        onUpdateProperty(mDevice.getReadPropertyMap(), props);
    }

    @Override
    public void onErrorOccurred(HomeDevice device, int error) {
        // No operation
    }

    public void onUpdateProperty(PropertyMap props, PropertyMap changed) {
        // Override it
    }

    protected T device() {
        return mDevice;
    }

    public void setDevice(T device) {
        mDevice = device;
    }
}
