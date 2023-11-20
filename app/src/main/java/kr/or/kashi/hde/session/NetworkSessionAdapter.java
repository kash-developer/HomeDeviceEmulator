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

import java.io.InputStream;
import java.io.OutputStream;

public class NetworkSessionAdapter implements NetworkSession {
    private final SessionAdapterDelegate mDelegate;

    public NetworkSessionAdapter() {
        mDelegate = new SessionAdapterDelegate(this);
    }

    public void putData(byte[] buffer) {
        mDelegate.putData(buffer);
    }

    public boolean onOpen() { return true; }

    public void onClose() { }

    public void onWrite(byte[] b) {}

    @Override
    public boolean open() {
        if (!mDelegate.open()) return false;
        return onOpen();
    }

    @Override
    public void close() {
        onClose();
        mDelegate.close();
    }

    @Override
    public InputStream getInputStream() {
        return mDelegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return mDelegate.getOutputStream();
    }
}
