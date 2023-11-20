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

package kr.or.kashi.hde.base;

public final class ByteArrayBuffer {
    private static final int INITIAL_CAPACITY = 16;
    private static final int CAPACITY_INCREMENT = 8;

    private byte[] mBuffer;
    private int mLength;

    public ByteArrayBuffer() {
        mBuffer = new byte[INITIAL_CAPACITY];
        mLength = 0;
    }

    private void ensureCapableOf(int reqLen) {
        if (reqLen < mBuffer.length) return;
        int newLne = (reqLen / CAPACITY_INCREMENT) * CAPACITY_INCREMENT + CAPACITY_INCREMENT;
        byte newBuffer[] = new byte[newLne];
        System.arraycopy(mBuffer, 0, newBuffer, 0, mLength);
        mBuffer = newBuffer;
    }

    public void append(byte b) {
        int newLen = mLength + 1;
        ensureCapableOf(newLen);
        mBuffer[mLength] = b;
        mLength = newLen;
    }

    public void append(int b) {
        append((byte)b);
    }

    public void clear() {
        mLength = 0;
    }

    public byte[] toArray() {
        byte[] byteArray = new byte[mLength];
        System.arraycopy(mBuffer, 0, byteArray, 0, mLength);
        return byteArray;
    }
}
