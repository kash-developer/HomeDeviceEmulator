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
import junit.framework.TestResult;

import kr.or.kashi.hde.HomeDevice;

public interface HomeDeviceTestCallback {
    default void onTestRunnerStarted() {}
    default void onTestRunnerFinished() {}
    default void onDeviceTestStarted(HomeDevice device) {}
    default void onDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {}
    default void onDeviceTestFinished(HomeDevice device) {}
}
