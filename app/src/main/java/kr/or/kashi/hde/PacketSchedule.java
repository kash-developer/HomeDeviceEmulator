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

package kr.or.kashi.hde;

public final class PacketSchedule {
    private HomePacket mPacket;
    private ExitCallback mExitCallback;
    private ErrorCallback mErrorCallback;
    private long mRepeatCount;
    private long mRepeatIntervalMs;
    private boolean mAllowSameRx;

    public static interface ExitCallback {
        void onScheduleExit(PacketSchedule schedule);
    }

    public static interface ErrorCallback {
        void onErrorOccurred(PacketSchedule schedule, int errorCode);
    }

    /** @hide */
    public PacketSchedule(Builder builder) {
        mPacket = builder.packet;
        mExitCallback = builder.exitCallback;
        mErrorCallback = builder.errorCallback;
        mRepeatCount = builder.repeatCount;
        mRepeatIntervalMs = builder.repeatIntervalMs;
        mAllowSameRx = builder.allowSameRx;
    }

    public HomePacket getPacket() {
        return mPacket;
    }

    public ExitCallback getExitCallback() {
        return mExitCallback;
    }

    public ErrorCallback getErrorCallback() {
        return mErrorCallback;
    }

    public long getRepeatCount() {
        return mRepeatCount;
    }

    public long getRepeatInterval() {
        return mRepeatIntervalMs;
    }

    public boolean allowSameRx() {
        return mAllowSameRx;
    }

    public static class Builder {
        private final HomePacket packet;
        private ExitCallback exitCallback = null;
        private ErrorCallback errorCallback = null;
        private long repeatCount = 0L;
        private long repeatIntervalMs = 0L;
        private boolean allowSameRx = false;

        public Builder(HomePacket packet) {
            this.packet = packet;
        }

        public Builder setExitCallback(ExitCallback callback) {
            this.exitCallback = callback;
            return this;
        }

        public Builder setErrorCallback(ErrorCallback callback) {
            this.errorCallback = callback;
            return this;
        }

        public Builder setRepeatCount(long repeatCount) {
            this.repeatCount = repeatCount;
            return this;
        }

        public Builder setRepeatInterval(long repeatIntervalMs) {
            this.repeatIntervalMs = repeatIntervalMs;
            return this;
        }

        public Builder setAllowSameRx(boolean allowSameRx) {
            this.allowSameRx = allowSameRx;
            return this;
        }

        public PacketSchedule build() {
            return new PacketSchedule(this);
        }
    }
}
