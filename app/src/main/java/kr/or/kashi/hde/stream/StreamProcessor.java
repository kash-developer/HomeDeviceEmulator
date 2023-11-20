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

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.PacketSchedule;
import kr.or.kashi.hde.session.NetworkSession;
import kr.or.kashi.hde.session.UartSchedSession;

public class StreamProcessor implements StreamCallback {
    private static final String TAG = StreamProcessor.class.getSimpleName();
    private static final boolean DBG = true;

    private final Context mContext;
    private final Executor mHandlerExecutor;
    private final Runnable mErrorRunnable;
    private final List<Client> mClients = new ArrayList<>();
    private NetworkSession mNetworkSession;
    private StreamRxThread mRxThread;
    private StreamTxThread mTxThread;
    private boolean mIsRunning;

    public interface Client {
        void processPacket(byte[] data, int length);
    }

    public StreamProcessor(Context context, Handler handler, Runnable errorRunnable) {
        mContext = context;
        mHandlerExecutor = handler::post;
        mErrorRunnable = errorRunnable;
    }

    public void addClient(Client client) {
        mClients.add(client);
    }

    public void removeClient(Client client) {
        mClients.remove(client);
    }

    public boolean startStream(NetworkSession NetworkSession) {
        mNetworkSession = NetworkSession;

        if (!mNetworkSession.open()) {
            Log.e(TAG, "Can't open NetworkSession!");
            return false;
        }

        final InputStream inputStream = mNetworkSession.getInputStream();
        if (inputStream == null) {
            Log.e(TAG, "Can't get input stream");
            return false;
        }

        final OutputStream outputStream = mNetworkSession.getOutputStream();
        if (outputStream == null) {
            Log.e(TAG, "Can't get input stream");
            return false;
        }

        mRxThread = new StreamRxThread(inputStream, this);
        mTxThread = new StreamTxThread(outputStream, this);

        mRxThread.start();
        mTxThread.start();

        mIsRunning = true;

        if (DBG) Log.d(TAG, "stream processor started!");

        return true;
    }

    public void stopStream() {
        if (mRxThread != null) {
            mRxThread.requestStop();
            mRxThread = null;
        }

        if (mTxThread != null) {
            mTxThread.requestStop();
            mTxThread = null;
        }

        if (mNetworkSession != null) {
            mNetworkSession.close();
            mNetworkSession = null;
        }

        mIsRunning = false;

        if (DBG) Log.d(TAG, "stream processor stopped!");
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void sendPacket(HomePacket packet) {
        mTxThread.addPacket(packet);
    }

    public boolean schedulePacket(PacketSchedule schedule) {
        // FIXME: Use general interface instead of concrete type of NetworkSession.
        if (mNetworkSession instanceof UartSchedSession) {
            UartSchedSession UartSchedSession = (UartSchedSession)mNetworkSession;
            return UartSchedSession.schedulePacket(schedule);
        }
        return false;
    }

    public void cancelSchedule(PacketSchedule schedule) {
        // FIXME: Use general interface instead of concrete type of NetworkSession.
        if (mNetworkSession instanceof UartSchedSession) {
            UartSchedSession UartSchedSession = (UartSchedSession)mNetworkSession;
            UartSchedSession.removeSchedule(schedule);
        }
    }

    @Override
    public void onPacketReceived(byte[] data, int length) {
        for (Client client: mClients) {
            client.processPacket(data, length);
        }
    }

    @Override
    public void onErrorOccurred() {
        mHandlerExecutor.execute(() -> {
            Log.d(TAG, "error! stream processor is stopping");
            stopStream();

            if (mErrorRunnable != null) {
                mErrorRunnable.run();
            }
        });
    }
}
