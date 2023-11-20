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

package kr.or.kashi.hde.stream;

import android.util.Log;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.InterruptedException;

import kr.or.kashi.hde.util.Utils;

public class StreamRxThread extends Thread {
    private static final String TAG = StreamRxThread.class.getSimpleName();
    private static final boolean DBG = true;

    private final InputStream mInputStream;
    private final StreamCallback mCallback;
    private boolean mRun = true;

    public StreamRxThread(InputStream inputStream, StreamCallback callback) {
        super(TAG);
        mInputStream = inputStream;
        mCallback = callback;
    }

    public void requestStop() {
        mRun = false;
        interrupt();
    }

    @Override
    public void run() {
        if (DBG) Log.d(TAG, getName() + " thread started...");

        byte[] buf = new byte[64];

        try {
            while (mRun) {
                int ret = mInputStream.read(buf);
                if (ret < 0) { // -1: end-of-stream
                    Log.d(TAG, "end-of-stream or error " + ret);
                    break;
                }

                if (ret == 0) {
                    Thread.sleep(1000); // TODO: FFFFFFFFFFFFFFFIX me
                    continue;
                }

                int len = Math.min(ret, buf.length);
                if (DBG) Log.d(TAG, "RX: " + Utils.toHexString(buf, len));

                mCallback.onPacketReceived(buf, len);
            }
        } catch (Exception e) {
            if (!mRun && (e instanceof InterruptedIOException || e instanceof InterruptedException)) {
                // It assumes there might be a request to stop thread.
            } else {
                e.printStackTrace();
                mCallback.onErrorOccurred();
            }
        } finally {
            mRun = false;
        }

        if (DBG) Log.d(TAG, getName() + " thread finished...");
    }
}
