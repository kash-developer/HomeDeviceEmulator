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

import junit.framework.TestCase;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyMap;

public class DeviceTestCase<T> extends TestCase implements HomeDevice.Callback {
    private HomeDevice mDevice;
    private boolean mWaitForCallback;

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
    }

    @Override
    public void onPropertyChanged(HomeDevice device, PropertyMap props) {
        synchronized (mDevice) {
            mWaitForCallback = false;
            mDevice.notifyAll();
        }
    }

    @Override
    public void onErrorOccurred(HomeDevice device, @HomeDevice.Error int error) {
        synchronized (mDevice) {
            mDevice.notifyAll();
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
        wait_();
        mDevice.setProperty(propName, valueClass, toValue);
        waitForPropertyChanged();
        assertEquals(toValue, mDevice.getProperty(propName, valueClass));
    }

    protected void waitForPropertyChanged() throws Exception {
        mWaitForCallback = true;
        wait_();
        if (mWaitForCallback) {
            throw new Exception();
        }
    }

    private void wait_() throws Exception {
        synchronized (mDevice) {
            mDevice.wait(2000);
        }
    }

    protected void waitFor(long timeout) throws Exception {
        synchronized (mDevice) {
            mDevice.wait(timeout);
        }
    }

    public void test_OnOff() throws Exception {
        assertPropertyChanaged(HomeDevice.PROP_ONOFF, Boolean.class, false, true);
    }
}
