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
    public static final int CMD_GROUP_CONTROL_REQ  = 0x42;

    public static final int CAP_STATUS_SINGLE   = (1 << 0);
    public static final int CAP_STATUS_MULTI    = (1 << 1);
    public static final int CAP_CHARAC_SINGLE   = (1 << 2);
    public static final int CAP_CHARAC_MULTI    = (1 << 3);

    private final MainContext mMainContext;

    private boolean mCharacteristicRetrieved = false;
    private PacketSchedule mAutoCharacReqSchedule = null;
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

    protected int getCapabilities() {
        return CAP_STATUS_SINGLE | CAP_STATUS_MULTI | CAP_CHARAC_SINGLE | CAP_CHARAC_MULTI;
    }

    protected boolean isCapableOf(int caps) {
        return (getCapabilities() & caps) == caps;
    }

    protected boolean checkStatusCapability() {
        return checkStatusCapability(getDeviceSubId());
    }

    protected boolean checkStatusCapability(final KSAddress.DeviceSubId subId) {
        if (subId.hasSingle() && isCapableOf(CAP_STATUS_SINGLE)) return true;
        if (subId.hasFull() && isCapableOf(CAP_STATUS_MULTI)) return true;
        return false;
    }

    protected boolean checkCharacCapability() {
        return checkCharacCapability(getDeviceSubId());
    }

    protected boolean checkCharacCapability(final KSAddress.DeviceSubId subId) {
        if (subId.hasSingle() && isCapableOf(CAP_CHARAC_SINGLE)) return true;
        if (subId.hasFull() && isCapableOf(CAP_CHARAC_MULTI)) return true;
        return false;
    }

    protected boolean checkCapabilityByPacket(KSPacket packet) {
        final int cmd = packet.commandType;
        final int subId = packet.deviceSubId;
        if (cmd == CMD_STATUS_REQ || cmd == CMD_STATUS_RSP)
            return checkStatusCapability(KSAddress.toDeviceSubId(subId));
        if (cmd == CMD_CHARACTERISTIC_REQ || cmd == CMD_CHARACTERISTIC_RSP)
            return checkCharacCapability(KSAddress.toDeviceSubId(subId));
        return true;
    }

    @Override
    public void onAttachedToStream() {
        super.onAttachedToStream(); // call super
        requestUpdate();
    }

    @Override
    public void onDetachedFromStream() {
        mCharacteristicRetrieved = false;
        cancelAllAutoSchedules();
        mAutoStatusReqScheduleError = 0;
        super.onDetachedFromStream(); // call super
    }

    @Override
    public void setPollPhase(@DeviceStatePollee.Phase int phase, long interval) {
        int curPhase = mPollPhase;
        int newPhase = phase;

        if (newPhase != curPhase) {
            if (newPhase == DeviceStatePollee.Phase.NAPPING) {
                // Clear flag to re-retrieve the characteristics of device when
                // returning to working state.
                mCharacteristicRetrieved = false;

                cancelAllAutoSchedules();
            }

            // Update connection state.
            boolean connected = (newPhase == DeviceStatePollee.Phase.WORKING);

            if (getDeviceSubId().isAll()) {
                // Can't retrieve the status from the device if it's of full range,
                // so no way to treat it as connected.
                connected = false;
            }

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

        if (getDeviceSubId().isAll()) {
            // Can't retrieve the status from the device if it's of full range,
            // so returns always current time as if the status is up-to-date.
            return SystemClock.uptimeMillis();
        }

        return super.getUpdateTime();
    }

    @Override
    public void requestUpdate(PropertyMap props) {
        if (isSlave()) {
            // In slave mode, don't need to request state but wait for packet.
            return;
        }

        if (getDeviceSubId().isAll()) {
            // Can't retrieve the status from the device if it's of full range.
            return;
        }

        if (!mCharacteristicRetrieved) {
            if (mAutoCharacReqSchedule != null) {
                return;
            }

            // Skip query of status if the device is not capable of.
            if (!checkCharacCapability()) {
                // HACK: If has child, set first child's update time as also of group's update time.
                final KSDeviceContextBase firstChild = getChildAt(KSDeviceContextBase.class, 0);
                if (firstChild != null) mLastUpdateTime = firstChild.getUpdateTime();
                return;
            }


            final HomePacket packet = makeCharacteristicReq();

            if (mPollPhase == DeviceStatePollee.Phase.NAPPING) {
                // Distribute each intervals not to use port at the same time.
                long distributedIntervalMs = mPollInterval + (long) (Math.random() * ((double)mPollInterval / 2.0));

                PacketSchedule schedule = new PacketSchedule.Builder(packet)
                        .setExitCallback(this::onScheduleExit)
                        .setErrorCallback(this::onScheduleError)
                        .setRepeatCount(0 /* infinity */)
                        .setRepeatInterval(distributedIntervalMs)
                        .setAllowSameRx(true) // To wakeup from napping when device is detected.
                        .build();

                // Try to schedule repeative sending of status request.
                if (schedulePacket(schedule)) {
                    mAutoCharacReqSchedule = schedule;
                } else {
                    // HACK: Wrap the packet within special container to suppress
                    // verbose log in napping phase.
                    HomePacket.WithMeta metaPacket = new HomePacket.WithMeta(packet);
                    metaPacket.suppressLog = true;
                    mMainContext.sendPacket(this, metaPacket);
                }
            } else {
                mMainContext.sendPacket(this, packet);
            }

            return; // TODO: What if device doesn't respond for the characteristic request.
        }

        cancelAllAutoSchedules();

        // Skip query of status if the device is not capable of.
        if (!checkStatusCapability()) {
            // HACK: If has child, set first child's update time as also of group's update time.
            final KSDeviceContextBase firstChild = getChildAt(KSDeviceContextBase.class, 0);
            if (firstChild != null) mLastUpdateTime = firstChild.getUpdateTime();
            return;
        }

        final KSPacket statusReqPacket = makeStatusReq(props);

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

    private void cancelAllAutoSchedules() {
        if (mAutoCharacReqSchedule != null) {
            cancelSchedule(mAutoCharacReqSchedule);
            mAutoCharacReqSchedule = null;
        }

        if (mAutoStatusReqSchedule != null) {
            cancelSchedule(mAutoStatusReqSchedule);
            mAutoStatusReqSchedule = null;
        }
    }

    private void onScheduleExit(PacketSchedule schedule) {
        if (schedule == mAutoCharacReqSchedule) {
            mAutoCharacReqSchedule = null;
        }

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

        // Check context's capabilities according to received packet.
        if (!checkCapabilityByPacket(ksPacket)) {
            return PARSE_OK_NONE;
        }

        // In slave mode, each single device doesn't need to parse its part from combined packet.
        if (isSlave()) {
            final int pktSubId = ksPacket.deviceSubId;
            final int devSubId = getDeviceSubId().value();
            if (KSAddress.toDeviceSubId(pktSubId).hasFull() && pktSubId != devSubId) {
                return PARSE_OK_NONE;
            }
        }

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
        packet.commandType = cmd;
        packet.data = (data == null) ? new byte[0] : data;

        if (cmd == CMD_STATUS_REQ || cmd == CMD_CHARACTERISTIC_REQ) {
            // If the device is of group, query for all (0x?F) with the group.
            if ((packet.deviceSubId & 0xF0) != 0 && checkCapabilityByPacket(packet)) {
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
