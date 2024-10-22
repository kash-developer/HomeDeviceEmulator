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

package kr.or.kashi.hde.test;

import android.util.Log;

import junit.framework.TestCase;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.device.LightView;

public class DeviceTestCase<T> extends TestCase implements HomeDevice.Callback {
    private static final String TAG = DeviceTestCase.class.getSimpleName();
    private final Object mLock = new Object();
    private HomeDevice mDevice;

    public void setDevice(HomeDevice device) {
        mDevice = device;
    }

    public HomeDevice device() {
        return mDevice;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice.addCallback(this);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mDevice.removeCallback(this);
        synchronized (mLock) {
            mLock.notifyAll();
        }
    }

    @Override
    public void onPropertyChanged(HomeDevice device, PropertyMap props) {
        synchronized (mLock) {
            mLock.notifyAll();
        }
    }

    @Override
    public void onErrorOccurred(HomeDevice device, @HomeDevice.Error int error) {
        synchronized (mLock) {
            mLock.notifyAll();
        }
    }

    public void assertSupported(String propName, int supportMask) throws Exception {
        final long supports = mDevice.getProperty(propName, Integer.class);
        if ((supports & supportMask) != supportMask) {
            throw new UnsupportedOperationException();
        }
    }

    public void assertSupported(String propName, long supportMask) throws Exception {
        final long supports = mDevice.getProperty(propName, Long.class);
        if ((supports & supportMask) != supportMask) {
            throw new UnsupportedOperationException();
        }
    }

    public <E> void assertEquals(String propName, Class<E> valueClass, E expectedValue) throws Exception {
        final E value = mDevice.getProperty(propName, valueClass);
        assertEquals(value, expectedValue);
    }

    public <E> void assertMasked(String propName, Class<E> valueClass, E maskedValue) throws Exception {
        final E value = mDevice.getProperty(propName, valueClass);
        assertEquals(value, ((long)value & (long)maskedValue) == (long)maskedValue);
    }

    public <E> void assertPropertyChanaged(String propName, Class<E> valueClass, E fromValue, E toValue) throws Exception {
        mDevice.setProperty(propName, valueClass, fromValue);
        assertTrue(waitForProperty(propName, valueClass, fromValue));
        mDevice.setProperty(propName, valueClass, toValue);
        assertTrue(waitForProperty(propName, valueClass, toValue));
    }

    protected <E> boolean waitForProperty(String propName, Class<E> valueClass, E dstValue) throws Exception {
        for (int i=0; i<5; i++) {
            E curValue = mDevice.getProperty(propName, valueClass);
            if (curValue.equals(dstValue)) return true;
            waitFor(500);
        }
        return false;
    }

    protected void waitFor(long timeout) throws Exception {
        synchronized (mLock) {
            mLock.wait(timeout);
        }
    }
}
