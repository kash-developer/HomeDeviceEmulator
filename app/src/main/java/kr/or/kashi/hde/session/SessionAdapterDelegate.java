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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import kr.or.kashi.hde.session.NetworkSession;

/** @hide */
public class SessionAdapterDelegate implements NetworkSession {
    private static final String TAG = SessionAdapterDelegate.class.getSimpleName();
    private static final int BUFFER_SIZE = 1024;

    private final WeakReference<NetworkSessionAdapter> mAdapter;
    private ReadStream mInputStream;
    private WriteStream mOutputStream;

    public SessionAdapterDelegate(NetworkSessionAdapter adapter) {
        mAdapter = new WeakReference<>(adapter);
    }

    public void putData(byte[] b) {
        if (mInputStream != null) {
            mInputStream.addBuffer(b);
        }
    }

    @Override
    public boolean open() {
        mInputStream = new ReadStream();
        mOutputStream = new WriteStream();
        return true;
    }

    @Override
    public void close() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }
            if (mOutputStream != null) {
                mOutputStream.close();
                mOutputStream = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        private ByteBuffer mByteBuffer;

        public ReadStream() {
            mByteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        }

        public void addBuffer(byte[] b) {
            synchronized (mByteBuffer) {
                try {
                    mByteBuffer.put(b);
                    mByteBuffer.notifyAll();
                } catch (Exception e) {
                    e.printStackTrace();
                    mByteBuffer.clear();
                }
            }
        }

        private boolean ensureBuffer() throws IOException {
            try {
                if (mByteBuffer.position() == 0) {
                    mByteBuffer.wait();
                }
            } catch (InterruptedException e) {
            }
            return mByteBuffer.hasRemaining();
        }

        @Override
        public int read() throws IOException {
            synchronized (mByteBuffer) {
                if (!ensureBuffer()) return -1;
                mByteBuffer.flip();
                final int d = mByteBuffer.get() & 0xFF;
                mByteBuffer.compact();
                return d;
            }
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            synchronized (mByteBuffer) {
                if (!ensureBuffer()) return -1;
                mByteBuffer.flip();
                len = Math.min(len, mByteBuffer.remaining());
                mByteBuffer.get(b, off, len);
                mByteBuffer.compact();
                return len;
            }
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0L) return 0L;
            synchronized (mByteBuffer) {
                mByteBuffer.flip();
                int remainingSize = Math.min(mByteBuffer.remaining(), (int)n);
                mByteBuffer.position(mByteBuffer.position() + remainingSize);
                mByteBuffer.compact();
            }
            return (long)n;
        }

        @Override
        public int available() throws IOException {
            return mByteBuffer.remaining();
        }

        @Override
        public void close() throws IOException {
            synchronized (mByteBuffer) {
                mByteBuffer.clear();
                mByteBuffer.notifyAll();
            }
        }
    }

    private class WriteStream extends OutputStream {
        public WriteStream() { }

        @Override
        public void write(int b) throws IOException {
            onWriteBytes(new byte[] { ((byte)(b & 0xFF)) });
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            if (b.length == len && off == 0) {
                onWriteBytes(b);
            } else {
                byte[] data = new byte[len];
                System.arraycopy(b, 0, data, 0, len);
                onWriteBytes(data);
            }
        }

        private void onWriteBytes(byte[] b) {
            NetworkSessionAdapter adapter = mAdapter.get();
            if (adapter != null) {
                adapter.onWrite(b);
            }
        }
    }
}
