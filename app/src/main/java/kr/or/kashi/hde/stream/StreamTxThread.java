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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.InterruptedException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.util.Utils;

public class StreamTxThread extends Thread {
    private static final String TAG = StreamTxThread.class.getSimpleName();
    private static final boolean DBG = true;

    private final OutputStream mOutputStream;
    private final StreamCallback mCallback;
    private final ArrayDeque<HomePacket> mPacketQueue = new ArrayDeque<>();
    private boolean mRun = true;

    public StreamTxThread(OutputStream outputStream, StreamCallback callback) {
        super(TAG);
        mOutputStream = outputStream;
        mCallback = callback;
    }

    public void addPacket(HomePacket packet) {
        synchronized (mPacketQueue) {
            mPacketQueue.add(packet);
            mPacketQueue.notifyAll();
            // TODO: Monitor exceeding size of queue
        }
    }

    public void requestStop() {
        mRun = false;
        synchronized (mPacketQueue) {
            mPacketQueue.clear();
            mPacketQueue.notifyAll();
        }
    }

    @Override
    public void run() {
        if (DBG) Log.d(TAG, getName() + " thread started...");

        ByteBuffer byteBuffer = ByteBuffer.allocate(64);
        byte[] buf = new byte[64];

        while (mRun) {
            synchronized (mPacketQueue) {
                try {
                    if (mPacketQueue.isEmpty()) {
                        mPacketQueue.wait();
                    } else {
                        final HomePacket packet = mPacketQueue.poll();

                        boolean suppressLog = false;
                        if (packet instanceof HomePacket.WithMeta) {
                            suppressLog = ((HomePacket.WithMeta)packet).suppressLog;
                        }

                        // TODO: Try to move out of synchronized()
                        packet.toBuffer(byteBuffer);
                        byteBuffer.flip();

                        while (byteBuffer.hasRemaining()) {
                            int len = Math.min(buf.length, byteBuffer.remaining());
                            byteBuffer.get(buf, 0, len);

                            mOutputStream.write(buf, 0, len);

                            if (DBG && !suppressLog) {
                                Log.d(TAG, "TX: " + Utils.toHexString(buf, len));
                            }
                        }

                        byteBuffer.compact();
                    }
                } catch (InterruptedException e) {
                    // It assumes there might be a request to stop.
                } catch (IOException e) {
                    e.printStackTrace();
                    mCallback.onErrorOccurred();
                }
            }
        }

        if (DBG) Log.d(TAG, getName() + " thread finished...");
    }
}
