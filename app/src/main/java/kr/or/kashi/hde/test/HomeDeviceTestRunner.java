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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.ksx4506.KSAddress;

public class HomeDeviceTestRunner implements Runnable {
    private static final String TAG = HomeDeviceTestRunner.class.getSimpleName();

    private final Handler mHandler;
    private final Executor mHandlerExecutor;
    private final List<HomeDeviceTestCallback> mCallbacks = new ArrayList<>();
    private List<HomeDevice> mDevices = new ArrayList<>();
    private Thread mThread = null;
    private boolean mRun = true;

    public HomeDeviceTestRunner(Handler handler) {
        mHandler = handler;
        mHandlerExecutor = mHandler::post;
    }

    public void addCallback(HomeDeviceTestCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    public void removeCallback(HomeDeviceTestCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public boolean isRunning() {
        return mRun;
    }

    public boolean start(List<HomeDevice> devices) {
        if (mThread != null) {
            stop();
        }

        mDevices.addAll(devices);
        Collections.sort(mDevices, (Object o1, Object o2) -> {
            HomeDevice d1 = (HomeDevice) o1;
            HomeDevice d2 = (HomeDevice) o2;
            return d1.getAddress().toString().compareTo(d2.getAddress().toString());
        });

        mRun = true;
        mThread = new Thread(this, TAG + "." + HomeDeviceTestRunner.class.getSimpleName());
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
        Collection<HomeDeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (HomeDeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(cb::onTestRunnerStarted);
        }
    }

    private void callOnTestRunnerFinished() {
        Collection<HomeDeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (HomeDeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(cb::onTestRunnerFinished);
        }
    }

    private void callOnDeviceTestStarted(HomeDevice device) {
        Collection<HomeDeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (HomeDeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestStarted(device));
        }
    }

    private void callOnDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {
        Collection<HomeDeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (HomeDeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestExecuted(device, test, result, progress));
        }
    }

    private void callOnDeviceTestFinished(HomeDevice device) {
        Collection<HomeDeviceTestCallback> callbacks;

        synchronized (mCallbacks) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (HomeDeviceTestCallback cb : callbacks) {
            mHandlerExecutor.execute(() -> cb.onDeviceTestFinished(device));
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "thread started");
        callOnTestRunnerStarted();

        for (int i=0; i<mDevices.size() && mRun; i++) {
            HomeDevice device = mDevices.get(i);

            // Test only single devices
            if (new KSAddress(device.getAddress()).getDeviceSubId().hasFull()) {
                continue;
            }

            callOnDeviceTestStarted(device);

            int progress = (int) (((double)i / (double)mDevices.size()) * 100.0);

            List<HomeDeviceTestCase> testCases = buildDeviceTestCases(device);
            for (int j=0; j<testCases.size() && mRun; j++) {
                HomeDeviceTestCase test = testCases.get(j);
                TestResult result = new TestResult(); // TODO:
                test.run(result);
                callOnDeviceTestExecuted(device, test, result, progress);
            }

            callOnDeviceTestFinished(device);
        }

        mRun = false;
        callOnTestRunnerFinished();
        Log.d(TAG, "thread finished");
    }

    private List<HomeDeviceTestCase> buildDeviceTestCases(HomeDevice device) {
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

        List<HomeDeviceTestCase> tests = createTestsFromClass(testClass);
        for (HomeDeviceTestCase t: tests) {
            if (t instanceof HomeDeviceTestCase) {
                ((HomeDeviceTestCase)t).setDevice(device);
            }
        }
        return tests;
    }

    private List<HomeDeviceTestCase> createTestsFromClass(final Class<?> testClass) {
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
        while (HomeDeviceTestCase.class.isAssignableFrom(clazz)) {
            for (Method m : clazz.getDeclaredMethods()) {
                HomeDeviceTestCase test = createTestByMethod(testClass, m);
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

    private static HomeDeviceTestCase createTestByMethod(Class<?> testClass, Method testMethod) {
        if (!isTestMethod(testMethod)) {
            return null;
        }
        return createTest(testClass, testMethod.getName());
    }

    private static HomeDeviceTestCase createTest(Class<?> testClass, String testName) {
        Constructor<?> constructor;
        try {
            constructor = getTestConstructor(testClass);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }

        HomeDeviceTestCase test = null;

        try {
            if (constructor.getParameterTypes().length == 0) {
                test = (HomeDeviceTestCase) constructor.newInstance(new Object[0]);
                if (test instanceof TestCase) ((TestCase)test).setName(testName);
            } else {
                test = (HomeDeviceTestCase) constructor.newInstance(new Object[]{testName});
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
