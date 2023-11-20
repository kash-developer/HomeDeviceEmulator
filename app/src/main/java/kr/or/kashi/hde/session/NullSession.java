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

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import kr.or.kashi.hde.HomeNetwork;

import java.lang.InterruptedException;
import java.lang.Thread;

public class NullSession implements NetworkSession {
    private static final String TAG = "NullSession";
    private static final boolean DBG = false;

    private InputStream mInputStream;
    private OutputStream mOutputStream;

    public NullSession() {
    }

    @Override
    public boolean open() {
        if (DBG) Log.w(TAG, "open: this is null stream!");
        mInputStream = new ReadStream();
        mOutputStream = new WriteStream();
        return true;
    }

    @Override
    public void close() {
        if (DBG) Log.w(TAG, "close: null stream has been closed!");
    }

    @Override
    public InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    private class ReadStream extends InputStream {
        public ReadStream() { }

        private void blockForAWhile() {
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
            }
        }

        @Override
        public int read() throws IOException {
            blockForAWhile();
            return 0;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            blockForAWhile();
            return 0;
        }

        @Override public long skip(long n) throws IOException { return n; }
        @Override public int available() throws IOException { return 0; }
        @Override public void close() throws IOException { }
    }

    private class WriteStream extends OutputStream {
        public WriteStream() { }
        @Override public void write(int b) throws IOException { }
        @Override public void write(byte b[], int off, int len) throws IOException { }
        @Override public void flush() throws IOException { }
        @Override public void close() throws IOException { }
    }
}
