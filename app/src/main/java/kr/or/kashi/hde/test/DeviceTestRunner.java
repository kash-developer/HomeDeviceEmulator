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

import android.os.Handler;
import android.util.ArraySet;
import android.util.Log;

import junit.framework.TestCase;
import junit.framework.TestResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.LightTest;

public class DeviceTestRunner implements Runnable {
    private static final String TAG = DeviceTestRunner.class.getSimpleName();

    private final Handler mHandler;
    private final Executor mHandlerExecutor;
    private final List<DeviceTestCallback> mCallbacks = new ArrayList<>();
    private List<HomeDevice> mDevices = new ArrayList<>();
    private Thread mThread = null;
    private boolean mRun = true;

    public DeviceTestRunner(Handler handler) {
        mHandler = handler;
        mHandlerExecutor = mHandler::post;
    }

    public void addCallback(DeviceTestCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    public void removeCallback(DeviceTestCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public boolean start(List<HomeDevice> devices) {
        if (mThread != null) {
            stop();
        }

        mDevices.addAll(devices);

        mRun = true;
        mThread = new Thread(this, TAG + "." + DeviceTestRunner.class.getSimpleName());
        mThread.start();

        return true;
    }

    public void stop() {
        if (mThread == null) {
            return;
        }

        mRun = false;

        try {
            mThread.join(200);
        } catch (InterruptedException e) {
        }

        mThread = null;
        mDevices.clear();
    }

    private void callOnTestRunnerStarted() {
        Collection<DeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (DeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(cb::onTestRunnerStarted);
        }
    }

    private void callOnTestRunnerFinished() {
        Collection<DeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (DeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(cb::onTestRunnerFinished);
        }
    }

    private void callOnDeviceTestStarted(HomeDevice device) {
        Collection<DeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (DeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestStarted(device));
        }
    }

    private void callOnDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {
        Collection<DeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (DeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestExecuted(device, test, result, progress));
        }
    }

    private void callOnDeviceTestFinished(HomeDevice device) {
        Collection<DeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (DeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestFinished(device));
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "thread started");
        callOnTestRunnerStarted();

        for (int i=0; i<mDevices.size(); i++) {
            HomeDevice device = mDevices.get(i);
            callOnDeviceTestStarted(device);

            int progress = (int) (((double)i / (double)mDevices.size()) * 100.0);

            for (DeviceTestCase test: buildDeviceTestCases(device)) {
                TestResult result = new TestResult(); // TODO:
                test.run(result);
                callOnDeviceTestExecuted(device, test, result, progress);
            }

            callOnDeviceTestFinished(device);
        }

        callOnTestRunnerFinished();
        Log.d(TAG, "thread finished");
    }

    private List<DeviceTestCase> buildDeviceTestCases(HomeDevice device) {
        String testPackage = device.getClass().getPackage().getName();
        String testClassName = device.getClass().getSimpleName() + "Test";

        Class<?> testClass = null;
        try {
            testClass = Class.forName(testPackage + "." + testClassName);
        } catch (ClassNotFoundException e) {
        }

        if (testClass == null) {
            return new ArrayList<>();
        }

        List<DeviceTestCase> tests = createTestsFromClass(testClass);
        for (DeviceTestCase t: tests) {
            if (t instanceof DeviceTestCase) {
                ((DeviceTestCase)t).setDevice(device);
            }
        }
        return tests;
    }

    private List<DeviceTestCase> createTestsFromClass(final Class<?> testClass) {
        List tests = new ArrayList<>();

        try {
            getTestConstructor(testClass);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return tests;
        }

        if (!Modifier.isPublic(testClass.getModifiers())) {
            Log.e(TAG, "Class " + testClass.getName() + " is not public");
            return tests;
        }

        Class<?> clazz = testClass;
        while (DeviceTestCase.class.isAssignableFrom(clazz)) {
            for (Method m : clazz.getDeclaredMethods()) {
                DeviceTestCase test = createTestByMethod(testClass, m);
                if (test != null) tests.add(test);
            }
            clazz = clazz.getSuperclass();
        }

        return tests;
    }

    public static Constructor<?> getTestConstructor(Class<?> testClass)
            throws NoSuchMethodException {
        try {
            return testClass.getConstructor(String.class);	
        } catch (NoSuchMethodException e) {
        }
        return testClass.getConstructor(new Class[0]);
    }

    private static DeviceTestCase createTestByMethod(Class<?> testClass, Method testMethod) {
        if (!isTestMethod(testMethod)) {
            Log.e(TAG, "Method " + testMethod.getName() + " is not public");
            return null;
        }
        return createTest(testClass, testMethod.getName());
    }

    private static DeviceTestCase createTest(Class<?> testClass, String testName) {
        Constructor<?> constructor;
        try {
            constructor = getTestConstructor(testClass);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }

        DeviceTestCase test = null;

        try {
            if (constructor.getParameterTypes().length == 0) {
                test = (DeviceTestCase) constructor.newInstance(new Object[0]);
                if (test instanceof TestCase) ((TestCase)test).setName(testName);
            } else {
                test = (DeviceTestCase) constructor.newInstance(new Object[]{testName});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return test;
    }

    private static boolean isTestMethod(Method m) {
        return (m.getModifiers() & Modifier.PUBLIC) != 0 &&
                m.getParameterTypes().length == 0 && 
                m.getName().startsWith("test") && 
                m.getReturnType().equals(Void.TYPE);
    }
}
