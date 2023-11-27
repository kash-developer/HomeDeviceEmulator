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

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.DeviceStatePollee;
import kr.or.kashi.hde.HomeAddress;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.PacketSchedule;
import kr.or.kashi.hde.HomeDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * [KS X 4506] The base class of device context.
 */
public abstract class KSDeviceContextBase extends DeviceContextBase {
    private static final String TAG = "KSDeviceContextBase";
    private static final boolean DBG = true;

    public static final int CMD_STATUS_REQ = 0x01;
    public static final int CMD_STATUS_RSP = 0x81;
    public static final int CMD_CHARACTERISTIC_REQ = 0x0F;
    public static final int CMD_CHARACTERISTIC_RSP = 0x8F;
    public static final int CMD_SINGLE_CONTROL_REQ = 0x41;
    public static final int CMD_SINGLE_CONTROL_RSP = 0xC1;
    public static final int CMD_GROUP_CONTROL_REQ = 0x42;

    private final MainContext mMainContext;

    private boolean mCharacteristicRetrieved = false;
    private PacketSchedule mAutoStatusReqSchedule = null;
    private int mAutoStatusReqScheduleError = 0;

    protected PropertyTask mSingleControlTask = new PropertyTask() {
        @Override
        public boolean execTask(PropertyMap reqProps, PropertyMap outProps) {
            sendPacket(makeControlReq(reqProps));
            // sendPacket(makeStatusReq(reqProps));
            return true;
        }
    };

    public KSDeviceContextBase(MainContext mainContext, Map defaultProps, Class<?> deviceClass) {
        super(mainContext, defaultProps, deviceClass);
        mMainContext = mainContext;
    }

    @Override
    public void onAttachedToStream() {
        super.onAttachedToStream(); // call super
        requestUpdate();
    }

    @Override
    public void onDetachedFromStream() {
        mCharacteristicRetrieved = false;
        if (mAutoStatusReqSchedule != null) {
            cancelSchedule(mAutoStatusReqSchedule);
            mAutoStatusReqSchedule = null;
        }
        mAutoStatusReqScheduleError = 0;
        super.onDetachedFromStream(); // call super
    }

    @Override
    public void setPollPhase(@DeviceStatePollee.Phase int phase, long interval) {
        int curPhase = mPollPhase;
        int newPhase = phase;

        if (newPhase != curPhase) {
            if (newPhase == DeviceStatePollee.Phase.NAPPING) {
                // Clear flag to re-reterive the characteristics of device when
                // returning to working state.
                mCharacteristicRetrieved = false;
            }

            // Update connection state.
            boolean connected = (newPhase == DeviceStatePollee.Phase.WORKING);
            mRxPropertyMap.put(HomeDevice.PROP_CONNECTED, connected);
            commitPropertyChanges(mRxPropertyMap);
        }

        super.setPollPhase(phase, interval);
    }

    @Override
    public long getUpdateTime() {
        if (isSlave()) {
            // In slave mode, the update time is the time when last packed is parsed.
            return super.getUpdateTime();
        }

        if (mAutoStatusReqSchedule != null && mAutoStatusReqScheduleError == 0) {
            // If auto status request has been scheduled, returns always current
            // time since the status update depends on underlying scheduler.
            return SystemClock.uptimeMillis();
        }

        return super.getUpdateTime();
    }

    @Override
    public void requestUpdate(PropertyMap props) {
        if (!mCharacteristicRetrieved) {
            HomePacket packet = makeCharacteristicReq();
            if (mPollPhase == DeviceStatePollee.Phase.NAPPING) {
                // HACK: Wrap the packet within special container to suppress
                // verbose log in napping phase.
                HomePacket.WithMeta metaPacket = new HomePacket.WithMeta(packet);
                metaPacket.suppressLog = false; // false for emulator
                mMainContext.sendPacket(this, metaPacket);
            } else {
                mMainContext.sendPacket(this, packet);
            }
            return; // TODO: What if device doesn't respond for the characteristic request.
        }

        KSPacket statusReqPacket = makeStatusReq(props);

        if (mAutoStatusReqSchedule != null) {
            cancelSchedule(mAutoStatusReqSchedule);
            mAutoStatusReqSchedule = null;
        }

        PacketSchedule schedule = new PacketSchedule.Builder(statusReqPacket)
                .setExitCallback(this::onScheduleExit)
                .setErrorCallback(this::onScheduleError)
                .setRepeatCount(0 /* infinity */)
                .setRepeatInterval(mPollInterval)
                .build();

        // Try to schedule repeative sending of status request.
        if (schedulePacket(schedule)) {
            mAutoStatusReqSchedule = schedule;
        } else {
            sendPacket(statusReqPacket);
        }
    }

    private void onScheduleExit(PacketSchedule schedule) {
        if (schedule == mAutoStatusReqSchedule) {
            mAutoStatusReqSchedule = null;
        }
    }

    private void onScheduleError(PacketSchedule schedule, int errorCode) {
        mAutoStatusReqScheduleError = errorCode;
    }

    public boolean isDetected() {
        return mCharacteristicRetrieved;
    }

    @Override
    public HomeAddress createAddress(String deviceAddress) {
        return new KSAddress(deviceAddress);
    }

    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        mAutoStatusReqScheduleError = 0; // Clear last error code

        switch (ksPacket.commandType) {
            case CMD_STATUS_REQ: return parseStatusReq(ksPacket, outProps);
            case CMD_STATUS_RSP: return parseStatusRsp(ksPacket, outProps);
            case CMD_CHARACTERISTIC_REQ: return parseCharacteristicReq(ksPacket, outProps);
            case CMD_CHARACTERISTIC_RSP: {
                int res = parseCharacteristicRsp(ksPacket, outProps);
                if (res >= PARSE_OK_NONE) {
                    mCharacteristicRetrieved = true;
                }
                return res;
            }
            case CMD_SINGLE_CONTROL_REQ: return parseSingleControlReq(ksPacket, outProps);
            case CMD_SINGLE_CONTROL_RSP: return parseSingleControlRsp(ksPacket, outProps);
        }

        return PARSE_OK_NONE;
    }

    protected void sendPacket(KSPacket packet) {
        sendPacket(packet, 0);
    }

    protected void sendPacket(KSPacket packet, long repeatCount) {
        // Try to schedule packet.
        if (repeatCount > 0) {
            PacketSchedule schedule = new PacketSchedule.Builder(packet)
                    .setRepeatCount(repeatCount)
                    .setRepeatInterval(0)
                    .build();
            if (mMainContext.schedulePacket(this, schedule)) {
                return; // Scheduled successfully
            }
        }

        // Or send packet manually.
        for (long i=0; i<repeatCount+1; i++) {
            mMainContext.sendPacket(this, packet);
        }
    }

    protected boolean schedulePacket(PacketSchedule schedule) {
        return mMainContext.schedulePacket(this, schedule);
    }

    protected void cancelSchedule(PacketSchedule schedule) {
        mMainContext.cancelSchedule(this, schedule);
    }

    protected int getDeviceId() {
        return ((KSAddress)getAddress()).getDeviceId();
    }

    protected KSAddress.DeviceSubId getDeviceSubId() {
        return ((KSAddress)getAddress()).getDeviceSubId();
    }

    protected boolean isSingleDevice() {
        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        return thisSubId.isSingle() || thisSubId.isSingleOfGroup();
    }

    protected KSPacket createPacket(int cmd) {
        return createPacket(cmd, null);
    }

    protected KSPacket createPacket(int cmd, byte... data) {
        final int devId = getDeviceId();
        final int subId = getDeviceSubId().value();
        return createPacket(devId, subId, cmd, data);
    }

    protected KSPacket createPacket(int devId, int subId, int cmd, byte... data) {
        KSPacket packet = (KSPacket) mMainContext.createPacket();

        packet.deviceId = devId;
        packet.deviceSubId = subId;

        if (cmd == CMD_STATUS_REQ || cmd == CMD_CHARACTERISTIC_REQ) {
            // If the device is of group, query for all (0x?F) with the group.
            if ((packet.deviceSubId & 0xF0) != 0) {
                packet.deviceSubId |= 0x0F;
            }

            // Some devices should queary with full range (0x0F) for single devices.
            switch (packet.deviceId) {
                case 0x30:  // house meter
                case 0x36:  // thermostat
                    packet.deviceSubId |= 0x0F;
                    break;
            }
        }

        packet.commandType = cmd;
        packet.data = (data == null) ? new byte[0] : data;

        return packet;
    }

    // Override these methods to parse data that comes from each type of packets.
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }
    protected @ParseResult int parseSingleControlReq(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }
    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) { return PARSE_OK_NONE; }

    protected KSPacket makeStatusReq(PropertyMap props) {
        return createPacket(CMD_STATUS_REQ);
    }

    protected KSPacket makeCharacteristicReq() {
        return createPacket(CMD_CHARACTERISTIC_REQ);
    }

    // Override it to make request packet with specific data.
    protected KSPacket makeControlReq(PropertyMap props) {
        return null;
    }
}
