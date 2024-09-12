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

package kr.or.kashi.hde.session;

import android.content.Context;
import android.os.Handler;
import android.util.ArraySet;
import android.util.Log;

import com.kdiwin.nova.uartsched.UartSched;
import com.kdiwin.nova.uartsched.UartSchedPort;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import kr.or.kashi.hde.PacketSchedule;

public class UartSchedSession extends NetworkSessionAdapter {
    private static final String TAG = "UartSchedSession";
    private static final boolean DBG = true;
    private static final int BUFFER_SIZE = 1024;

    public static final int PORT_TYPE_RS232 = UartSched.PORT_TYPE_RS232;
    public static final int PORT_TYPE_RS485 = UartSched.PORT_TYPE_RS485;
    public static final int PORT_TYPE_RS485_MASTER = UartSched.PORT_TYPE_RS485_MASTER;
    public static final int PORT_TYPE_RS485_SLAVE = UartSched.PORT_TYPE_RS485_SLAVE;

    private final Context mContext;
    private final Handler mHandler;
    private final int mPortType;
    private final String mPortName;
    private final int mPortSpeed;

    private UartSchedPort mUartSchedPort;
    private UartSchedPort.Callback mUartSchedPortCallback = new UartSchedPort.Callback() {
        @Override
        public void onDataReceived(UartSchedPort port, long scheduleId, byte[] data) {
            putData(data);
        }

        @Override
        public void onScheduleExit(UartSchedPort port, long scheduleId) {
            synchronized (mPacketSchedules) {
                Collection<PacketSchedule> schedules = mPacketSchedules.get(scheduleId);
                if (schedules == null) return;

                for (PacketSchedule s : schedules) {
                    if (s.getExitCallback() != null) {
                        s.getExitCallback().onScheduleExit(s);
                    }
                }

                mPacketSchedules.remove(scheduleId);
            }
        }

        @Override
        public void onErrorOccurred(UartSchedPort port, long scheduleId, int error) {
            synchronized (mPacketSchedules) {
                Collection<PacketSchedule> schedules = mPacketSchedules.get(scheduleId);
                if (schedules == null) return;

                for (PacketSchedule s : schedules) {
                    if (s.getErrorCallback() != null) {
                        s.getErrorCallback().onErrorOccurred(s, error);
                    }
                }
            }

        }

        @Override
        public void onPortClosed(UartSchedPort port, boolean byError) {
            mUartSchedPort = null;
            synchronized (mPacketSchedules) {
                mPacketSchedules.clear();
            }
            if (byError) Log.e(TAG, mPortName + " port closed by error!");
        }
    };

    private final Map<Long, ArraySet<PacketSchedule>> mPacketSchedules = new ConcurrentHashMap<>();

    public UartSchedSession(Context context, Handler handler, int type, String name, int speed) {
        mContext = context;
        mHandler = handler;
        mPortType = type;
        mPortName = name;
        mPortSpeed = speed;
    }

    @Override
    public boolean onOpen() {
        if (DBG) Log.d(TAG, "openning... " + mPortName + " " + mPortSpeed);

        synchronized (mPacketSchedules) {
            mPacketSchedules.clear();
        }

        try {
            mUartSchedPort = UartSched.openPort(mHandler, mPortType, mPortName, mPortSpeed);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (mUartSchedPort == null) {
            Log.e(TAG, "Can't open port (" + mPortName + ", " + mPortSpeed + ")");
            return false;
        }

        if (mPortType == PORT_TYPE_RS485_SLAVE) {
            // If the port works as slave, put null executor intentionally to call
            // callbacks synchronously without handler executor, because responding
            // is time-critical.
            mUartSchedPort.registerCallback(mUartSchedPortCallback, null);
        } else {
            mUartSchedPort.registerCallback(mUartSchedPortCallback);
        }

        return true;
    }

    @Override
    public void onClose() {
        if (mUartSchedPort == null) {
            return;
        }

        mUartSchedPort.unregisterCallback(mUartSchedPortCallback);

        if (mUartSchedPort != null) {
            UartSched.closePort(mUartSchedPort);
            mUartSchedPort = null;
        }
    }

    @Override
    public void onWrite(byte[] b) {
        if (mUartSchedPort == null) return;
        mUartSchedPort.schedulePacket(b, 0, 0, false);
    }

    public boolean schedulePacket(PacketSchedule schedule) {
        if (mUartSchedPort == null) return false;

        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        schedule.getPacket().toBuffer(byteBuffer);
        byteBuffer.flip();
        byte[] buf = new byte[byteBuffer.remaining()];
        byteBuffer.get(buf, 0, buf.length);
        byteBuffer.compact();

        final long repeatCount = schedule.getRepeatCount();
        final long repeatIntervalMs = schedule.getRepeatInterval();
        final boolean allowSameRx = schedule.allowSameRx();

        final long scheduleId = mUartSchedPort.schedulePacket(
                buf, repeatCount, repeatIntervalMs, allowSameRx);
        if (scheduleId < 0) return false;

        synchronized (mPacketSchedules) {
            ArraySet<PacketSchedule> schedules = mPacketSchedules.get(scheduleId);
            if (schedules == null) {
                schedules = new ArraySet<>();
                mPacketSchedules.put(scheduleId, schedules);
            }
            schedules.add(schedule);
        }

        return true;
    }

    public void removeSchedule(PacketSchedule schedule) {
        if (schedule == null) return;
        if (mUartSchedPort == null) return;

        synchronized (mPacketSchedules) {
            Iterator<Map.Entry<Long, ArraySet<PacketSchedule>>> iterator
                    = mPacketSchedules.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, ArraySet<PacketSchedule>> entry = iterator.next();
                entry.getValue().remove(schedule);
                if (entry.getValue().size() == 0) {
                    mUartSchedPort.removeSchedule(entry.getKey());
                    iterator.remove();
                }
            }
        }
    }

    public void removeAllSchedules() {
        if (mUartSchedPort == null) return;

        synchronized (mPacketSchedules) {
            mPacketSchedules.clear();
        }

        mUartSchedPort.clearAllSchedules();
    }
}
