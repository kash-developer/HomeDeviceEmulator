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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Map;

import kr.or.kashi.hde.stream.StreamProcessor;
import kr.or.kashi.hde.util.DebugLog;
import kr.or.kashi.hde.util.Utils;

public abstract class MainContext extends DeviceManager
                                  implements StreamProcessor.Client {
    private static final String TAG = MainContext.class.getSimpleName();
    private static final int RX_BUFFER_SIZE = 1024;

    private final Context mContext;
    private final Handler mRxEventHandler;
    private final ByteBuffer mRxByteBuffer;
    private final Runnable mProcessBufferRunnable = this::onProcessBuffer;
    private final Runnable mClearBufferRunnable = this::onClearBuffer;
    protected StreamProcessor mStreamProcessor;

    public MainContext(Context context) {
        super(context, null);
        mContext = context;
        mRxEventHandler = new Handler(Looper.getMainLooper());
        mRxByteBuffer = ByteBuffer.allocate(RX_BUFFER_SIZE);
    }

    public void attachStream(StreamProcessor streamProcessor) {
        mRxByteBuffer.clear();

        mStreamProcessor = streamProcessor;
        mStreamProcessor.addClient(this);

        for (HomeDevice device : getAllDevices()) {
            device.dc().onAttachedToStream();
        }
    }

    public void detachStream() {
        if (mStreamProcessor != null) {
            for (HomeDevice device : getAllDevices()) {
                device.dc().onDetachedFromStream();
            }
        }

        mRxEventHandler.removeCallbacksAndMessages(null);
        mStreamProcessor.removeClient(this);
        mStreamProcessor = null;

        mRxEventHandler.removeCallbacksAndMessages(null);
        mRxByteBuffer.clear();
    }

    public HomeDevice createDevice(Map defaultProps) {
        Class<?> deviceContextClass = getContextClass(defaultProps);
        if (deviceContextClass == null) return null;

        try {
            Constructor contextBuilder = deviceContextClass.getConstructor(MainContext.class, Map.class);
            DeviceContextBase deviceContext = (DeviceContextBase) contextBuilder.newInstance(this, defaultProps);
            Class<?> deviceClass = deviceContext.getDeviceClass();;
            Constructor deviceBuilder = deviceClass.getConstructor(DeviceContextBase.class);
            return (HomeDevice) deviceBuilder.newInstance(deviceContext);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public boolean addDevice(HomeDevice device) {
        boolean added = super.addDevice(device);
        if (added && mStreamProcessor != null) {
            device.dc().onAttachedToStream();
        }
        return added;
    }

    public void removeDevice(HomeDevice device) {
        super.removeDevice(device);
        if (mStreamProcessor != null) {
            device.dc().onDetachedFromStream();
        }
    }

    public void clearAllDevices() {
        if (mStreamProcessor != null) {
            for (HomeDevice device : getAllDevices()) {
                device.dc().onDetachedFromStream();
            }
        }
        super.clearAllDevices();
    }

    public void processPacket(byte[] data, int length) {
        synchronized (mRxByteBuffer) {
            if (length > mRxByteBuffer.remaining()) {
                Log.w(TAG, "clear buffer by overflow! " + (mRxByteBuffer.position() + length));
                mRxByteBuffer.clear();
            }

            mRxByteBuffer.put(data, 0, length);

            if (mRxEventHandler.hasCallbacks(mClearBufferRunnable) == false) {
                mRxEventHandler.removeCallbacks(mClearBufferRunnable);
            }

            if (mRxEventHandler.hasCallbacks(mProcessBufferRunnable) == false) {
                mRxEventHandler.post(mProcessBufferRunnable);
            }
        }
    }

    private void onProcessBuffer() {
        synchronized (mRxByteBuffer) {
            mRxByteBuffer.flip(); // change to read mode
            mRxByteBuffer.mark(); // mark current buffer's position

            try {
                while (mRxByteBuffer.hasRemaining()) {
                    if (parsePacket(mRxByteBuffer)) {
                        mRxByteBuffer.mark(); // mark current buffer's position
                    }
                }
            } catch (BufferUnderflowException e) {
                mRxByteBuffer.reset(); // move to buffer's marked position.
                // Wait for more data, and then clear buffer.
                mRxEventHandler.postDelayed(mClearBufferRunnable, 500);
            }

            mRxByteBuffer.compact(); // change to write mode
        }
    }

    private void onClearBuffer() {
        synchronized (mRxByteBuffer) {
            int remaining = mRxByteBuffer.remaining();
            if (remaining > 0) {
                mRxByteBuffer.clear();
                Log.w(TAG, "clear incomplete remaining data (" + remaining + ")");
            }
        }
    }

    public void sendPacket(DeviceContextBase base, HomePacket packet) {
        if (mStreamProcessor != null) {
            mStreamProcessor.sendPacket(packet);
            printTxLog(packet);
        }
    }

    public boolean schedulePacket(DeviceContextBase base, PacketSchedule schedule) {
        if (mStreamProcessor == null) {
            return false;
        }

        boolean scheduled = mStreamProcessor.schedulePacket(schedule);
        if (scheduled) {
            printTxLog(schedule.getPacket());
        }

        return scheduled;
    }

    public void cancelSchedule(DeviceContextBase base, PacketSchedule schedule) {
        if (mStreamProcessor != null) {
            mStreamProcessor.cancelSchedule(schedule);
        }
    }

    private void printTxLog(HomePacket packet) {
        // TODO: Should be get more efficient instead of converting to byte buffer.
        ByteBuffer byteBuffer = ByteBuffer.allocate(64);
        packet.toBuffer(byteBuffer);
        DebugLog.printTxRx("TX: " + Utils.toHexString(byteBuffer));
    }

    // Override it on every derived classes and implement it according to specific protocols.
    public abstract Class<?> getContextClass(Map defaultProps);
    public abstract DeviceDiscovery getDeviceDiscovery();
    public abstract HomePacket createPacket();
    public abstract boolean parsePacket(ByteBuffer buffer) throws BufferUnderflowException;

}
