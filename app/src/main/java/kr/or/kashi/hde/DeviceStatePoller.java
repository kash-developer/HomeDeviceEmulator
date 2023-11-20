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

import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;

import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

public class DeviceStatePoller implements Runnable {
    private static final String TAG = DeviceStatePoller.class.getSimpleName();
    private static final boolean DBG = true;

    private static final long POLL_INTERVAL_MS = 500L;
    private static final long POLL_DURATION_MS = 5 * 1000L;

    private static final long PHASE_INITIAL_INTERVAL = 0L;
    private static final long PHASE_WAITING_INTERVAL = 1 * 1000L;
    private static final long PHASE_WORKING_INTERVAL = 100L;
    private static final long PHASE_NAPPING_INTERVAL = 10 * 1000L;

    private static class PollInfo {
        final DeviceStatePollee pollee;
        @DeviceStatePollee.Phase int phase;
        long phaseTime;
        long interval;
        long duration;
        long lastPollTime;
        boolean disposed;

        public PollInfo(DeviceStatePollee pollee) {
            this.pollee = pollee;
            this.phase = DeviceStatePollee.Phase.INITIAL;
            this.phaseTime = SystemClock.uptimeMillis();
            this.interval = PHASE_INITIAL_INTERVAL;
            this.lastPollTime = 0L;
            this.disposed = false;
        }
    }

    private final ArrayDeque<PollInfo> mPolleeQueue = new ArrayDeque<>();
    private final Map<DeviceStatePollee, PollInfo> mPollInfoMap = new ArrayMap<>();

    private long mPollIntervalMs = POLL_INTERVAL_MS;
    private Thread mThread = null;
    private boolean mRun = true;
    private boolean mRepeative = false;

    public DeviceStatePoller() { }

    public long getPollIntervalMs() {
        synchronized (this) {
            return mPollIntervalMs;
        }
    }

    public void setPollIntervalMs(long intervalMs) {
        synchronized (this) {
            if (intervalMs <= 0) {
                mPollIntervalMs = Long.MAX_VALUE;
            } else {
                mPollIntervalMs = intervalMs;
            }
        }
        synchronized (mPolleeQueue) {
            mPolleeQueue.notifyAll();
        }
    }

    @Override
    public void run() {
        if (DBG) Log.d(TAG, "thread started");

        while (mRun) {
            try {
                PollInfo info = null;
                synchronized (mPolleeQueue) {
                    if (mPolleeQueue.isEmpty()) {
                        mPolleeQueue.wait();
                    }
                    info = mPolleeQueue.poll();
                    if (info == null || info.disposed) {
                        continue;   // No info to poll or disposed.
                    }
                }

                processPhase(info);

                synchronized (mPolleeQueue) {
                    if (!info.disposed) {
                        mPolleeQueue.add(info);
                    }
                    mPolleeQueue.wait(getPollIntervalMs());
                }
            } catch (Exception e) {
                if (!mRun && (e instanceof InterruptedIOException || e instanceof InterruptedException)) {
                    // It assumes there might be a request to stop thread.
                } else {
                    e.printStackTrace();
                }
            }
        }

        if (DBG) Log.d(TAG, "thread finished");
    }

    public void processPhase(PollInfo info) {
        final long lastUpdateTime = info.pollee.getUpdateTime();
        final long currentTime = SystemClock.uptimeMillis();
        final long phaseElapsed = currentTime - info.phaseTime;
        final long updateElapsed = currentTime - lastUpdateTime;
        final long pollElasped = currentTime - info.lastPollTime;

        switch (info.phase) {
        case DeviceStatePollee.Phase.INITIAL:
            transitPhase(info, DeviceStatePollee.Phase.WAITING);
            break;

        case DeviceStatePollee.Phase.WAITING:
            if (updateElapsed < POLL_DURATION_MS)
                transitPhase(info, DeviceStatePollee.Phase.WORKING);
            else if (phaseElapsed > POLL_DURATION_MS)
                transitPhase(info, DeviceStatePollee.Phase.NAPPING);
            break;

        case DeviceStatePollee.Phase.WORKING:
            if (updateElapsed > POLL_DURATION_MS)
                transitPhase(info, DeviceStatePollee.Phase.WAITING);
            break;

        case DeviceStatePollee.Phase.NAPPING:
            if (updateElapsed < POLL_DURATION_MS)
                transitPhase(info, DeviceStatePollee.Phase.WORKING);
            break;
        }

        if (pollElasped > info.interval && updateElapsed > info.interval) {
            info.pollee.requestUpdate();
            info.lastPollTime = currentTime;
        }

        if (lastUpdateTime > 0 && !mRepeative) {
            info.disposed = true;
        }
    }

    public void transitPhase(PollInfo info, @DeviceStatePollee.Phase int newPhase) {
        info.phase = newPhase;
        info.phaseTime = SystemClock.uptimeMillis();

        switch (newPhase) {
        case DeviceStatePollee.Phase.INITIAL:
            info.interval = PHASE_INITIAL_INTERVAL;
            break;

        case DeviceStatePollee.Phase.WAITING:
            info.interval = PHASE_WAITING_INTERVAL;
            break;

        case DeviceStatePollee.Phase.WORKING:
            info.interval = PHASE_WORKING_INTERVAL;
            break;

        case DeviceStatePollee.Phase.NAPPING:
            info.interval = PHASE_NAPPING_INTERVAL;
            break;
        }

        info.pollee.setPollPhase(newPhase, info.interval);
    }

    public void start(boolean repeative, List<DeviceStatePollee> polleeList) {
        if (mThread != null) {
            stop();
        }

        mPollInfoMap.clear();
        mPolleeQueue.clear();

        for (DeviceStatePollee pollee: polleeList) {
            PollInfo info = new PollInfo(pollee);
            mPollInfoMap.put(pollee, info);
            mPolleeQueue.add(info);
        }

        mThread = new Thread(this, TAG + "." + DeviceStatePoller.class.getSimpleName());
        mRun = true;
        mRepeative = repeative;

        mThread.start();
    }

    public void stop() {
        if (mThread == null) {
            return;
        }

        mPollInfoMap.clear();

        synchronized (mPolleeQueue) {
            mRun = false;
            mPolleeQueue.clear();
            mPolleeQueue.notifyAll();
        }

        try {
            mThread.join(200);
        } catch (InterruptedException e) {
        }

        mThread = null;
    }

    public void addPollee(DeviceStatePollee pollee) {
        removePollee(pollee);

        PollInfo info = new PollInfo(pollee);
        mPollInfoMap.put(pollee, info);

        synchronized (mPolleeQueue) {
            mPolleeQueue.add(info);
            mPolleeQueue.notifyAll();
        }
    }

    public void removePollee(DeviceStatePollee pollee) {
        PollInfo info = mPollInfoMap.get(pollee);
        if (info != null) {
            info.disposed = true;
            mPollInfoMap.remove(pollee);
        }
    }
}
