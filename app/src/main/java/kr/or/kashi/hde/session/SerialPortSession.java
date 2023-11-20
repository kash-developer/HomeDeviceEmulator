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
//import android.hardware.SerialManager;
//import android.hardware.SerialPort;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class SerialPortSession implements NetworkSession {
    private static final String TAG = "SerialPortSession";
    private static final boolean DBG = true;
    private static final int BUFFER_SIZE = 1024;

    private final Context mContext;
    private final String mPortName;
    private final int mPortSpeed;

//    private SerialPort mSerialPort;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    public SerialPortSession(@NonNull Context context, @NonNull String name, int speed) {
        mContext = context;
        mPortName = name;
        mPortSpeed = speed;
    }

    @Override
    public boolean open() {
//        final SerialManager serialManager =
//                (SerialManager)mContext.getSystemService(Context.SERIAL_SERVICE);
//
//        boolean found = false;
//        String[] availablePorts = serialManager.getSerialPorts();
//        for (String portName: availablePorts) {
//            if (mPortName.equals(portName)) {
//                found = true;
//                break;
//            }
//        }
//
//        if (!found) {
//            Log.e(TAG, "No available port for the name of " + mPortName);
//            return false;
//        }
//
//        try {
//            if (DBG) Log.d(TAG, " Open serial port " + mPortName + " " + mPortSpeed);
//            mSerialPort = serialManager.openSerialPort(mPortName, mPortSpeed);
//            if (mSerialPort == null) {
//                Log.e(TAG, "Can't open serial port (" + mPortName + ", " + mPortSpeed + ")");
//                return false;
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            return false;
//        }
//
//        mInputStream = new ReadStream();
//        mOutputStream = new WriteStream();

        return true;
    }

    @Override
    public void close() {
//        try {
//            if (mInputStream != null) {
//                mInputStream.close();
//                mInputStream = null;
//            }
//            if (mOutputStream != null) {
//                mOutputStream.close();
//                mOutputStream = null;
//            }
//            if (mSerialPort != null) {
//                mSerialPort.close();
//                mSerialPort = null;
//            }
//        } catch (IOException e) {
//        }
    }

    @Override
    public InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return mOutputStream;
    }

//    private class ReadStream extends InputStream {
//        private ByteBuffer mInputBuffer;
//
//        public ReadStream() {
//            // TODO: allocate direct buffer
//            mInputBuffer = ByteBuffer.allocate(BUFFER_SIZE);
//        }
//
//        @Override
//        public int read() throws IOException {
//            mInputBuffer.clear();
//            mSerialPort.read(mInputBuffer);
//            return mInputBuffer.get() & 0xFF;
//        }
//
//        @Override
//        public int read(byte b[], int off, int len) throws IOException {
//            mInputBuffer.clear();
//            int readLen = mSerialPort.read(mInputBuffer);
//            int retSize = Math.min(len, readLen);
//            mInputBuffer.get(b, off, retSize);
//            return retSize;
//        }
//
//        @Override
//        public long skip(long n) throws IOException {
//            if (n <= 0L) return 0L;
//            int remainingSize = Math.min(mInputBuffer.remaining(), (int)n);
//            mInputBuffer.position(mInputBuffer.position() + remainingSize);
//            return (long)n;
//        }
//
//        @Override
//        public int available() throws IOException {
//            return mInputBuffer.remaining();
//        }
//
//        @Override
//        public void close() throws IOException {
//            mInputBuffer.clear();
//            mInputBuffer = null;
//        }
//    }
//
//    private class WriteStream extends OutputStream {
//        private ByteBuffer mOutputBuffer;
//
//        public WriteStream() {
//            // TODO: allocate direct buffer
//            mOutputBuffer = ByteBuffer.allocate(BUFFER_SIZE);
//        }
//
//        @Override
//        public void write(int b) throws IOException {
//            mOutputBuffer.clear();
//            mOutputBuffer.put((byte)(b & 0xFF));
//            mSerialPort.write(mOutputBuffer, 1);
//        }
//
//        @Override
//        public void write(byte b[], int off, int len) throws IOException {
//            mOutputBuffer.clear();
//            mOutputBuffer.put(b, off, len);
//            mSerialPort.write(mOutputBuffer, len);
//        }
//
//        @Override
//        public void close() throws IOException {
//            mOutputBuffer.clear();
//            mOutputBuffer = null;
//        }
//    }
}
