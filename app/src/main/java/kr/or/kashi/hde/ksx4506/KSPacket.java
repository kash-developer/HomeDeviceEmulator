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

package kr.or.kashi.hde.ksx4506;

import android.util.Log;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.util.Utils;

/**
 * [KS X 4506] This class en/decodes header of packet.
 *
 * TODO: reuse object like KeyEvent
 */
public class KSPacket implements HomePacket {
    private static final String TAG = "KSPacket";
    private static final boolean DBG = true;

    public static final int STX = 0xF7;

    public int deviceId;
    public int deviceSubId;
    public int commandType;
    public byte[] data = new byte[0];

    public static boolean ensure(ByteBuffer buffer) {
        final int pos = buffer.position();
        final int remain = buffer.remaining();
        final int minSize = (5 + 2); // [hdr,did,sid,cmd,len] + [xor,add]
        if (remain < minSize) { 
            return false;
        }

        final int length = buffer.get(pos + 4) & 0xFF;
        final int fullSize = minSize + length;
        if (remain < fullSize) {
            return false;
        }

        return true;
    }

    public static boolean check(ByteBuffer buffer) throws BufferUnderflowException {
        final int pos = buffer.position();

        if ((buffer.get(pos) & 0xFF) != STX) {
            return false;
        }

        final int length = buffer.get(pos + 4) & 0xFF;
        final int xorSum = buffer.get(pos + 5 + length) & 0xFF;
        final int addSum = buffer.get(pos + 5 + length + 1) & 0xFF;
        final int packetSize = 5 + length; // XOR-SUM and ADD-SUM are not included

        int calXorSum = 0;
        int calAddSum = 0;
        for (int i = pos; i < pos + packetSize; i++) {
            int b = buffer.get(i) & 0xFF;
            calXorSum ^= b; // xor
            calAddSum += b; // add
        }
        calAddSum += xorSum; // add xor-sum byte

        calXorSum &= 0xFF;
        calAddSum &= 0xFF;

        if (calXorSum != xorSum) {
            if (DBG) Log.w(TAG, "xor-sum mismatched! ("
                    + "cal:" + Utils.toHexString((byte)calXorSum) + " != "
                    + "rcv:" + Utils.toHexString((byte)xorSum) + ")");
            return false;
        }

        if (calAddSum != addSum) {
            if (DBG) Log.w(TAG, "add-sum mismatched! ("
                    + "cal:" + Utils.toHexString((byte)calAddSum) + " != "
                    + "rcv:" + Utils.toHexString((byte)addSum) + ")");
            return false;
        }

        return true;
    }

    public KSPacket() { }

    @Override
    public String address() {
        return new KSAddress(deviceId, deviceSubId).getDeviceAddress();
    }

    @Override
    public int command() {
        return this.commandType;
    }

    @Override
    public byte[] data() {
        return this.data;
    }

    @Override
    public int hashCode() {
        int result = deviceId;
        result = 31 * result + deviceSubId;
        result = 31 * result + commandType;
        result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
        return result;
    }

    @Override
    public boolean parse(ByteBuffer buffer) throws BufferUnderflowException {
        if (!check(buffer)) return false;

        final int header = buffer.get() & 0xFF;

        this.deviceId = buffer.get() & 0xFF;
        this.deviceSubId = buffer.get() & 0xFF;
        this.commandType = buffer.get() & 0xFF;

        final int length = buffer.get() & 0xFF;
        if (length > 0) {
            this.data = new byte[length];
            buffer.get(this.data);
        } else {
            this.data = new byte[0];
        }

        final int xorSum = buffer.get() & 0xFF; // already compared in check()
        final int addSum = buffer.get() & 0xFF; // already compared in check()

        return true;
    }

    @Override
    public void toBuffer(ByteBuffer buffer) throws BufferOverflowException {
        final int pos = buffer.position();

        buffer.put((byte)STX);
        buffer.put((byte)this.deviceId);
        buffer.put((byte)this.deviceSubId);
        buffer.put((byte)this.commandType);

        int length = (this.data != null) ? this.data.length : 0;
        if (length > 0) {
            buffer.put((byte)(this.data.length & 0xFF));
            buffer.put(this.data);
        } else {
            buffer.put((byte)0);
        }

        final int packetSize = 5 + length;

        int calXorSum = 0;
        for (int i = pos; i < pos + packetSize; i++) {
            int b = buffer.get(i) & 0xFF;
            calXorSum ^= b; // xor
        }
        buffer.put((byte)calXorSum);

        int calAddSum = 0;
        for (int i = pos; i < pos + packetSize + 1; i++) {
            int b = buffer.get(i) & 0xFF;
            calAddSum += b; // add
        }
        buffer.put((byte)calAddSum);
    }
}
