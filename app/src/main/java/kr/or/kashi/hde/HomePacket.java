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

import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public interface HomePacket {
    String address();
    int command();
    byte[] data();

    int hashCode();
    boolean parse(ByteBuffer buffer) throws BufferUnderflowException;
    void toBuffer(ByteBuffer buffer) throws BufferOverflowException;

    public class Null implements HomePacket {
        @Override public String address() { return ""; }
        @Override public int command() { return 0; }
        @Override public byte[] data() { return new byte[0]; }
        @Override public int hashCode() { return 0; };
        @Override public boolean parse(ByteBuffer buffer) throws BufferUnderflowException { return false; }
        @Override public void toBuffer(ByteBuffer buffer) throws BufferOverflowException { }
    }

    public class WithMeta implements HomePacket {
        public boolean suppressLog = false;

        private final HomePacket mInner;

        public WithMeta(HomePacket inner) {
            mInner = inner;
        }

        @Override public String address() { return mInner.address();}
        @Override public int command() { return mInner.command(); }
        @Override public byte[] data() { return mInner.data(); }
        @Override public int hashCode() { return mInner.hashCode(); }
        @Override public boolean parse(ByteBuffer buffer) throws BufferUnderflowException { return mInner.parse(buffer); }
        @Override public void toBuffer(ByteBuffer buffer) throws BufferOverflowException { mInner.toBuffer(buffer); }
    }
}
