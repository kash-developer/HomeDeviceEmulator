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

import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.PacketSchedule;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.device.PowerSaver;
import kr.or.kashi.hde.util.Utils;

import java.util.Map;

/**
 * [KS X 4506] The standby-power-saver implementation for PowerSaver
 */
public class KSPowerSaver extends KSDeviceContextBase {
    private static final String TAG = "KSPowerSaver";
    private static final boolean DBG = true;

    public static final int CMD_STANDBY_POWER_GETTING_REQ = 0x31;
    public static final int CMD_STANDBY_POWER_GETTING_RSP = 0xB1;
    public static final int CMD_STANDBY_POWER_SETTING_REQ = 0x43;
    public static final int CMD_STANDBY_POWER_SETTING_RSP = 0xC3;

    public static final int CHANNEL_STATE_BYTES = 3;
    public static final int CHANNEL_CHARC_BYTES = 1;

    private int mChannelCountInGroup = 0;
    private PacketSchedule mStanbyPowerGettingSchedule = null;

    public KSPowerSaver(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, PowerSaver.class);

        if (isMaster()) {
            final PropertyTask onChannelControlTask = this::onChannelControlTask;
            setPropertyTask(HomeDevice.PROP_ONOFF, onChannelControlTask);
            setPropertyTask(PowerSaver.PROP_CURRENT_SETTINGS, onChannelControlTask);
            setPropertyTask(PowerSaver.PROP_STANDBY_CONSUMPTION, this::onStandbyPowerSettingTask);
        }
    }

    @Override
    public void onDetachedFromStream() {
        // TODO: Use some batch cleanup interface
        if (mStanbyPowerGettingSchedule != null) {
            cancelSchedule(mStanbyPowerGettingSchedule);
            mStanbyPowerGettingSchedule = null;
        }

        super.onDetachedFromStream(); // Call super
    }

    @Override
    public void requestUpdate(PropertyMap props) {
        super.requestUpdate(props); // Call super

        if (!isDetected()) {
            return;
        }

        if (mStanbyPowerGettingSchedule != null) {
            cancelSchedule(mStanbyPowerGettingSchedule);
            mStanbyPowerGettingSchedule = null;
        }

        // TODO: Use some batch registration interface
        KSPacket packet = createPacket(CMD_STANDBY_POWER_GETTING_REQ);

        // Querying status of power settings against group device is more efficient
        // comparing to each single devices, so convert the device sub id to group id
        // if it's accociated to a group. see also, KSDeviceContextBase.createPacket()
        if ((packet.deviceSubId & 0xF0) != 0) {
            packet.deviceSubId |= 0x0F;
        }

        PacketSchedule schedule = new PacketSchedule.Builder(packet)
                .setRepeatCount(0 /* infinity */)
                .setRepeatInterval(1000L /* TODO: */)
                .build();

        if (schedulePacket(schedule)) {
            mStanbyPowerGettingSchedule = schedule;
        } else {
            sendPacket(packet);
        }
    }

    @Override
    public @ParseResult int parsePayload(KSPacket packet, PropertyMap outProps) {
        // TODO: Use some new interface of parsing action.
        switch (packet.commandType) {
            case CMD_GROUP_CONTROL_REQ:
                return parseGroupControlReq(packet, outProps);

            case CMD_STANDBY_POWER_GETTING_REQ:
                return parseStandbyPowerGettingReq(packet, outProps);

            case CMD_STANDBY_POWER_SETTING_REQ:
                return parseStandbyPowerSettingReq(packet, outProps);

            case CMD_STANDBY_POWER_GETTING_RSP:
            case CMD_STANDBY_POWER_SETTING_RSP:
                return parseStandbyPowerRsp(packet, outProps);
        }
        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        if (isSingleDevice()) {
            makeChannelStateBytes(getReadPropertyMap(), data);
        } else {
            for (KSPowerSaver child: getChildren(KSPowerSaver.class)) {
                child.makeChannelStateBytes(child.getReadPropertyMap(), data);
            }
        }

        // Send response packet
        sendPacket(createPacket(CMD_STATUS_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 4) { // At least, 4 = error byte(1) + single channel data(3)
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();

        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);

        if (pktSubId.isSingle() || pktSubId.isSingleOfGroup()) {
            // Parse just single data since this is single device.
            return parseChannelStateBytes(packet.data, 1, outProps);
        } else if (pktSubId.isFullOfGroup()) {
            // From group data, parse only one channel associated to this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            if (thisSingleId > 0x0 && thisSingleId < 0xF) {
                final int thisSingleIndex = thisSingleId - 1;
                final int dataOffset = 1 + (thisSingleIndex * CHANNEL_STATE_BYTES);
                return parseChannelStateBytes(packet.data, dataOffset, outProps);
            } else {
                Log.w(TAG, "parse-status-rsp: out of id range: " + thisSingleId);
            }
        } else {
            Log.w(TAG, "parse-status-rsp: not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    protected void makeChannelStateBytes(PropertyMap props, ByteArrayBuffer outData) {
        final boolean isOn = props.get(HomeDevice.PROP_ONOFF, Boolean.class);
        final long curStates = props.get(PowerSaver.PROP_CURRENT_STATES, Long.class);
        final long curSettings = props.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
        final float curWatt = props.get(PowerSaver.PROP_CURRENT_CONSUMPTION, Float.class);

        String numStr = Utils.formatString(curWatt, 4, 1);
        final int bcd1kw = numStr.charAt(0) - '0';
        final int bcd100w  = numStr.charAt(1) - '0';
        final int bcd10w = numStr.charAt(2) - '0';
        final int bcd1w  = numStr.charAt(3) - '0';
        final int bcd0d1w = numStr.charAt(5) - '0';

        int stateData = 0;
        if (isOn) stateData |= (1 << 4);
        if ((curStates & PowerSaver.State.OVERLOAD_DETECTED) != 0) stateData |= (1 << 5);
        if ((curStates & PowerSaver.State.STANDBY_DETECTED) != 0) stateData |= (1 << 6);
        if ((curSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0) stateData |= (1 << 7);

        outData.append(stateData | (bcd1kw & 0x0F));
        outData.append(((bcd100w & 0x0F) << 4) | (bcd10w & 0x0F));
        outData.append(((bcd1w & 0x0F) << 4) | (bcd0d1w & 0x0F));
    }

    private @ParseResult int parseChannelStateBytes(byte[] data, int offset, PropertyMap outProps) {
        int channelSize = Math.min(CHANNEL_STATE_BYTES, data.length-offset);
        if (channelSize < CHANNEL_STATE_BYTES) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of channel " + channelSize);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int states  = ((data[offset + 0] & 0xF0));
        final int bcd1kw  = ((data[offset + 0] & 0x0F));        // 1000W
        final int bcd100w = ((data[offset + 1] & 0xF0) >> 4);   // 100W
        final int bcd10w  = ((data[offset + 1] & 0x0F));        // 10W
        final int bcd1w   = ((data[offset + 2] & 0xF0) >> 4);   // 1W
        final int bcd0d1w = ((data[offset + 2] & 0x0F));        // 0.1W

        // Chennel on/off state
        boolean isOn = ((states & (1 << 4)) != 0);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);

        // Power states
        long newStates = 0;
        if ((states & (1 << 5)) != 0) newStates |= PowerSaver.State.OVERLOAD_DETECTED;
        if ((states & (1 << 6)) != 0) newStates |= PowerSaver.State.STANDBY_DETECTED;
        outProps.put(PowerSaver.PROP_CURRENT_STATES, newStates);

        // Settings
        long newSettings = 0;
        if ((states & (1 << 7)) != 0) newSettings |= PowerSaver.Setting.STANDBY_BLOCKING_ON;
        outProps.put(PowerSaver.PROP_CURRENT_SETTINGS, newSettings);

        // Current power consumption
        float currentWatt = 0.0f;
        currentWatt += bcd1kw  * 1000.0f;
        currentWatt += bcd100w * 100.0f;
        currentWatt += bcd10w  * 10.0f;
        currentWatt += bcd1w   * 1.0f;
        currentWatt += bcd0d1w * 0.1f;
        currentWatt = Math.round(currentWatt * 10) / 10.0f;
        outProps.put(PowerSaver.PROP_CURRENT_CONSUMPTION, currentWatt);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicReq(KSPacket packet, PropertyMap outProps) {
        // No data to parse from request packet.

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        if (isSingleDevice()) {
            data.append(1);
            makeChannelCapabilityByte(getReadPropertyMap(), data);
        } else {
            data.append(getChildCount());
            for (KSPowerSaver child: getChildren(KSPowerSaver.class)) {
                child.makeChannelCapabilityByte(child.getReadPropertyMap(), data);
            }
        }

        // Send response packet
        sendPacket(createPacket(CMD_CHARACTERISTIC_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 3) { // At least, 3 = error byte(1) + channel count(1) + single channel state(1)
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        mChannelCountInGroup = packet.data[1] & 0xFF;

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();

        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);

        if (pktSubId.isSingle() || pktSubId.isSingleOfGroup()) {
            // Parse just single data since this is single device.
            parseChannelCapabilityByte(packet.data[2], outProps);
            return PARSE_OK_PEER_DETECTED;
        } else if (pktSubId.isFullOfGroup()) {
            // From group data, parse only one channel associated to this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            final int thisSingleIndex = thisSingleId - 1;
            if (thisSingleIndex >= 0 && thisSingleIndex < mChannelCountInGroup) {
                final int dataOffset = 2 + (thisSingleIndex * CHANNEL_CHARC_BYTES);
                if (dataOffset < packet.data.length) {
                    parseChannelCapabilityByte(packet.data[dataOffset], outProps);
                    return PARSE_OK_PEER_DETECTED;
                } else {
                    Log.w(TAG, "parse-chr-rsp: data is shorter than expected");
                }
            }
        } else {
            Log.w(TAG, "parse-chr-rsp: not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    protected void makeChannelCapabilityByte(PropertyMap props, ByteArrayBuffer outData) {
        final long supportedStates = props.get(PowerSaver.PROP_SUPPORTED_STATES, Long.class);
        final long supportedSettings = props.get(PowerSaver.PROP_SUPPORTED_SETTINGS, Long.class);

        int data = 0;
        if ((supportedStates & PowerSaver.State.OVERLOAD_DETECTED) != 0L) data |= (1 << 5);
        if ((supportedStates & PowerSaver.State.STANDBY_DETECTED) != 0L) data |= (1 << 6);
        if ((supportedSettings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0L) data |= (1 << 7);

        outData.append(data);
    }

    private void parseChannelCapabilityByte(byte data, PropertyMap outProps) {
        long newStateSupports = 0;
        if ((data & (1 << 5)) != 0) newStateSupports |= PowerSaver.State.OVERLOAD_DETECTED;
        if ((data & (1 << 6)) != 0) newStateSupports |= PowerSaver.State.STANDBY_DETECTED;
        outProps.put(PowerSaver.PROP_SUPPORTED_STATES, newStateSupports);

        long newSettingSupports = 0;
        if ((data & (1 << 7)) != 0) newSettingSupports |= PowerSaver.Setting.STANDBY_BLOCKING_ON;
        outProps.put(PowerSaver.PROP_SUPPORTED_SETTINGS, newSettingSupports);
    }

    @Override
    protected @ParseResult int parseSingleControlReq(KSPacket packet, PropertyMap outProps) {
        // Parse control data and apply it.
        if (isSingleDevice()) {
            parseSingleControlReqData(packet.data[0], outProps);
        } else {
            for (int i=0; i<getChildCount() && i < packet.data.length; i++) {
                final KSPowerSaver child = getChildAt(KSPowerSaver.class, i);
                child.parseSingleControlReqData(packet.data[i], child.mRxPropertyMap);
                child.commitPropertyChanges(child.mRxPropertyMap);
            }
        }

        // Just responds request control data.
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        if (isSingleDevice()) {
            data.append(makeChannelControlRspByte(outProps));
        } else {
            for (KSLight child: getChildren(KSLight.class)) {
                data.append(makeChannelControlRspByte(child.getReadPropertyMap()));
            }
        }
        sendPacket(createPacket(CMD_SINGLE_CONTROL_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        final String FUNTAG = "parse-single-ctrl-rsp";

        if (packet.data.length < 2) { // At least, 2 = error byte(1) + control result(1)
            if (DBG) Log.w(TAG, FUNTAG + ": wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, FUNTAG + ": error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();

        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);

        if (pktSubId.isSingle() || pktSubId.isSingleOfGroup()) {
            // Parse just single set of data since this is single device.
            return parseSingleControlRspData(packet.data[1], outProps);
        } else if (pktSubId.isFullOfGroup()) {
            // From group data, parse only exact set of data associated to this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            if (thisSingleId > 0x0 && thisSingleId < 0xF) {
                final int thisSingleIndex = thisSingleId - 1;
                final int dataOffset = 1 + thisSingleIndex;
                return parseSingleControlRspData(packet.data[dataOffset], outProps);
            } else {
                Log.w(TAG, FUNTAG + ": out of id range: " + thisSingleId);
            }
        } else {
            Log.w(TAG, FUNTAG + ": not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    private @ParseResult int parseSingleControlReqData(byte data, PropertyMap outProps) {
        final boolean channelOn = ((data & (1 << 0)) != 0);
        final boolean blockingOn = ((data & (1 << 1)) != 0);
        final boolean channelChanged = ((data & (1 << 4)) != 0);
        final boolean blockingChanged = ((data & (1 << 5)) != 0);

        if (channelChanged) outProps.put(HomeDevice.PROP_ONOFF, channelOn);
        if (blockingChanged) {
            long settings = outProps.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
            if (blockingOn) {
                settings |= PowerSaver.Setting.STANDBY_BLOCKING_ON;
            } else {
                settings &= ~PowerSaver.Setting.STANDBY_BLOCKING_ON;
            }
            outProps.put(PowerSaver.PROP_CURRENT_SETTINGS, settings);
        }

        return PARSE_OK_ACTION_PERFORMED;
    }

    private @ParseResult int parseSingleControlRspData(byte data, PropertyMap outProps) {
        final boolean isOn = ((data & (1 << 0)) != 0);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);

        long settings = outProps.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);
        if ((data & (1 << 1)) != 0) {
            settings |= PowerSaver.Setting.STANDBY_BLOCKING_ON;
        } else {
            settings &= ~PowerSaver.Setting.STANDBY_BLOCKING_ON;
        }
        outProps.put(PowerSaver.PROP_CURRENT_SETTINGS, settings);

        return PARSE_OK_ACTION_PERFORMED;
    }

    protected @ParseResult int parseGroupControlReq(KSPacket packet, PropertyMap outProps) {
        final int control = packet.data[0] & 0xFF;
        outProps.put(HomeDevice.PROP_ONOFF, (control == 1));

        for (KSPowerSaver child: getChildren(KSPowerSaver.class)) {
            child.parseGroupControlReq(packet, child.mRxPropertyMap);
            child.commitPropertyChanges(child.mRxPropertyMap);
        }

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseStandbyPowerGettingReq(KSPacket packet, PropertyMap outProps) {
        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        if (isSingleDevice()) {
            makeStandbyPowerData(outProps, data);
        } else {
            for (KSPowerSaver child: getChildren(KSPowerSaver.class)) {
                child.makeStandbyPowerData(child.getReadPropertyMap(), data);
            }
        }

        sendPacket(createPacket(CMD_STANDBY_POWER_GETTING_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseStandbyPowerSettingReq(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) return PARSE_OK_NONE;

        if (isSingleDevice()) {
            parseStandbyPowerData(packet.data[0], packet.data[1], outProps);
        } else {
            int dataCount = packet.data.length / 2;
            for (int i=0; i<dataCount && i<getChildCount(); i++) {
                final KSPowerSaver child = getChildAt(KSPowerSaver.class, i);
                final byte data1 = packet.data[i*2];
                final byte data2 = packet.data[i*2+1];
                child.parseStandbyPowerData(data1, data2, child.mRxPropertyMap);
                child.commitPropertyChanges(child.mRxPropertyMap);
            }
        }

        final ByteArrayBuffer data = new ByteArrayBuffer();
        data.append(0); // no error
        if (isSingleDevice()) {
            makeStandbyPowerData(outProps, data);
        } else {
            for (KSPowerSaver child: getChildren(KSPowerSaver.class)) {
                child.makeStandbyPowerData(child.getReadPropertyMap(), data);
            }
        }

        sendPacket(createPacket(CMD_STANDBY_POWER_SETTING_RSP, data.toArray()));

        return PARSE_OK_STATE_UPDATED;
    }

    protected @ParseResult int parseStandbyPowerRsp(KSPacket packet, PropertyMap outProps) {
        final String FUNTAG = "parse-standbypower-rsp";

        if (packet.data.length < 3) { // At least, 3 = error byte(1) + standby power values(2)
            if (DBG) Log.w(TAG, FUNTAG + ": wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, FUNTAG + ": error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();

        if (!thisSubId.isSingle() && !thisSubId.isSingleOfGroup()) {
            // Device object that represents as parent of single devices doesn't
            // need to parse any state data since it's depending on child object
            // and stateless.
            return PARSE_OK_NONE;
        }

        final KSAddress.DeviceSubId pktSubId = KSAddress.toDeviceSubId(packet.deviceSubId);

        if (pktSubId.isSingle() || pktSubId.isSingleOfGroup()) {
            // Parse just single set of data since this is single device.
            final byte data1 = packet.data[1];
            final byte data2 = packet.data[2];
            return parseStandbyPowerData(data1, data2, outProps);
        } else if (pktSubId.isFullOfGroup()) {
            // From group data, parse only exact set of data associated to this single device.
            final int thisSingleId = thisSubId.value() & 0x0F;
            if (thisSingleId > 0x0 && thisSingleId < 0xF) {
                final int thisSingleIndex = thisSingleId - 1;
                final int data1Offset = 1 + (thisSingleIndex * 2);
                final int data2Offset = data1Offset + 1;
                if (data1Offset < packet.data.length && data2Offset < packet.data.length) {
                    final byte data1 = packet.data[data1Offset];
                    final byte data2 = packet.data[data2Offset];
                    return parseStandbyPowerData(data1, data2, outProps);
                }
            } else {
                Log.w(TAG, FUNTAG + ": out of id range: " + thisSingleId);
            }
        } else {
            Log.w(TAG, FUNTAG + ": not implemented case, should never reach this");
        }

        return PARSE_OK_NONE;
    }

    private @ParseResult int parseStandbyPowerData(byte data1, byte data2, PropertyMap outProps) {
        final int bcd100w = ((data1 & 0xF0) >> 4);   // 100W
        final int bcd10w  = ((data1 & 0x0F));        // 10W
        final int bcd1w   = ((data2 & 0xF0) >> 4);   // 1W
        final int bcd0d1w = ((data2 & 0x0F));        // 0.1W

        float standbyWatt = 0;
        standbyWatt += bcd100w * 100.0f;
        standbyWatt += bcd10w  * 10.0f;
        standbyWatt += bcd1w   * 1.0f;
        standbyWatt += bcd0d1w * 0.1f;

        standbyWatt = Math.round(standbyWatt * 10) / 10.0f;

        outProps.put(PowerSaver.PROP_STANDBY_CONSUMPTION, standbyWatt);

        return PARSE_OK_STATE_UPDATED;
    }

    protected boolean onChannelControlTask(PropertyMap reqProps, PropertyMap outProps) {
        boolean consumed = false;

        final KSAddress.DeviceSubId thisSubId = ((KSAddress)getAddress()).getDeviceSubId();
        if (thisSubId.isSingle() || thisSubId.isSingleOfGroup()) {
            sendPacket(createPacket(CMD_SINGLE_CONTROL_REQ, makeChannelControlByte(reqProps)));
            consumed |= true;
        } else if (thisSubId.isFull() || thisSubId.isFullOfGroup() || thisSubId.isAll()) {
            final boolean isOn = reqProps.get(HomeDevice.PROP_ONOFF, Boolean.class);
            final byte data = (byte) (isOn ? 0x01 : 0x00);
            sendPacket(createPacket(CMD_GROUP_CONTROL_REQ, data));
            consumed |= true;
        } else {
            Log.w(TAG, "on-ctrl-action: should never reach this");
        }

        return consumed;
    }

    private byte makeChannelControlByte(PropertyMap reqProps) {
        final boolean chOn = (reqProps.get(HomeDevice.PROP_ONOFF, Boolean.class));
        final long settings = reqProps.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);

        byte data = 0;

        if (chOn) {
            data |= (byte)(1 << 0);
        }

        if ((settings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0) {
            data |= (byte)(1 << 1);
        }

        data |= (byte)(1 << 4); // HACK: Set always as channel state has been changed
        data |= (byte)(1 << 5); // HACK: Set always as standby blocking setting has been changed

        return data;
    }

    private int makeChannelControlRspByte(PropertyMap props) {
        final boolean chOn = (props.get(HomeDevice.PROP_ONOFF, Boolean.class));
        final long settings = props.get(PowerSaver.PROP_CURRENT_SETTINGS, Long.class);

        int data = 0;

        if (chOn) {
            data |= (byte)(1 << 0);
        }

        if ((settings & PowerSaver.Setting.STANDBY_BLOCKING_ON) != 0) {
            data |= (byte)(1 << 1);
        }

        return data;
    }

    private boolean onStandbyPowerSettingTask(PropertyMap reqProps, PropertyMap outProps) {
        final ByteArrayBuffer data = new ByteArrayBuffer();
        if (isSingleDevice()) {
            makeStandbyPowerData(reqProps, data);
        } else {
            for (int i=0; i<getChildCount(); i++) {
                // HACK: Just put same watt for all channels.
                makeStandbyPowerData(reqProps, data);
            }
        }
        sendPacket(createPacket(CMD_STANDBY_POWER_SETTING_REQ, data.toArray()));
        return true;
    }

    private void makeStandbyPowerData(PropertyMap props, ByteArrayBuffer outData) {
        final float watt = props.get(PowerSaver.PROP_STANDBY_CONSUMPTION, Float.class);
        float rest = Math.min(watt, 900.0F);
        final int bcd100w = (int)(rest / 100.0f); rest -= ((float)bcd100w * 100.0f);
        final int bcd10w  = (int)(rest / 10.0f);  rest -= ((float)bcd10w * 10.0f);
        final int bcd1w   = (int)(rest / 1.0f);   rest -= ((float)bcd1w * 1.0f);
        final int bcd0d1w = (int)(rest / 0.1f);   rest -= ((float)bcd0d1w * 0.1f);
        outData.append((byte)((bcd100w << 4) | bcd10w));
        outData.append((byte)((bcd1w << 4) | bcd0d1w));
    }
}
