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
import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.device.BatchSwitch;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * [KS X 4506] The batch-switch implementation for BatchSwitch
 */
public class KSBatchSwitch extends KSDeviceContextBase {
    private static final String TAG = "KSBatchSwitch";
    private static final boolean DBG = true;

    // [HACK/DEBUG] Some devices doesn't consider the failure cases.
    public static final boolean HACK_NEVER_FAIL = false;

    public static final int CMD_FULL_CONTROL_REQ = 0x42; // sub-id : 0F or FF
    public static final int CMD_SWITCH_SETTING_RESULT_REQ = 0x43;
    public static final int CMD_SWITCH_SETTING_RESULT_RSP = 0xC3;
    public static final int CMD_ELEVATOR_FLOOR_DISPLAY_REQ = 0x44;
    public static final int CMD_ELEVATOR_FLOOR_DISPLAY_RSP = 0xC4;

    private boolean mGasLockingReqReceived = false;
    private boolean mOutingSettingReqReceived = false;
    private boolean mElevatorCallReqReceived = false;

    public KSBatchSwitch(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps, BatchSwitch.class);

        // Register the tasks to be performed when specific property changes.
        setPropertyTask(HomeDevice.PROP_ONOFF, this::onSetSwitchStatesTask);
        setPropertyTask(BatchSwitch.PROP_SWITCH_STATES, this::onSetSwitchStatesTask);
        setPropertyTask(BatchSwitch.PROP_DISPLAY_STATES, this::onSetDisplayStatesTask);
    }

    @Override
    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_ELEVATOR_FLOOR_DISPLAY_RSP:
                return parseStatusRspBytes(ksPacket.data, outProps);
        }

        return super.parsePayload(packet, outProps);
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-status-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-status-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        return parseStatusRspBytes(packet.data, outProps);
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        // TODO: check if single device, group is not allowed

        if (packet.data.length < 2) {
            if (DBG) Log.w(TAG, "parse-chr-rsp: wrong size of data " + packet.data.length);
            return PARSE_ERROR_MALFORMED_PACKET;
        }

        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-chr-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        long supportedSwitches = 0;
        long supportedDisplays = 0;

        final int data1 = packet.data[1] & 0xFF;
        supportedSwitches |= BatchSwitch.Switch.BATCH_LIGHT_OFF;
        if ((data1 & (1 << 0)) != 0) supportedSwitches |= (BatchSwitch.Switch.GAS_LOCKING);
        if ((data1 & (1 << 1)) != 0) supportedSwitches |= (BatchSwitch.Switch.OUTING_SETTING);
        if ((data1 & (1 << 2)) != 0) supportedSwitches |= (BatchSwitch.Switch.POWER_SAVING);
        if ((data1 & (1 << 3)) != 0) supportedSwitches |= (BatchSwitch.Switch.ELEVATOR_UP_CALL | BatchSwitch.Switch.ELEVATOR_DOWN_CALL);
        if ((data1 & (1 << 4)) != 0) supportedDisplays |= (BatchSwitch.Display.ELEVATOR_FLOOR);

        outProps.put(BatchSwitch.PROP_SUPPORTED_SWITCHES, supportedSwitches);
        outProps.put(BatchSwitch.PROP_SUPPORTED_DISPLAYS, supportedDisplays);

        // Set the display states also on if supported.
        outProps.put(BatchSwitch.PROP_DISPLAY_STATES, supportedDisplays);

        return PARSE_OK_PEER_DETECTED;
    }

    @Override
    protected @ParseResult int parseSingleControlRsp(KSPacket packet, PropertyMap outProps) {
        final int error = packet.data[0] & 0xFF;
        if (error != 0) {
            if (DBG) Log.d(TAG, "parse-single-ctrl-rsp: error occurred! " + error);
            onErrorOccurred(HomeDevice.Error.UNKNOWN);
            return PARSE_OK_ERROR_RECEIVED;
        }

        // HACK: Don't parse the response packet of control request because
        // some device return wrong state although we're expecting at least
        // it would be maintaining the last value if the device can't apply
        // the request immediately.
        // --- BLOCKED BELOW ----------------------------------------------
        // parseStatusRspBytes(packet.data, outProps);

        return PARSE_OK_ACTION_PERFORMED;
    }

    protected @ParseResult int parseStatusRspBytes(byte[] data, PropertyMap outProps) {
        long curStates = (long) outProps.get(BatchSwitch.PROP_SWITCH_STATES).getValue();
        long newStates = curStates;

        if ((data[1] & (1 << 0)) != 0) {
            newStates |= BatchSwitch.Switch.GAS_LOCKING;
            mGasLockingReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: gas_locking req received");
        } else {
            if (mGasLockingReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: gas_locking req cleared, but still waitting for confirming");
            }
        }

        if ((data[1] & (1 << 1)) != 0) {
            newStates |= BatchSwitch.Switch.OUTING_SETTING;
            mOutingSettingReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: outing_setting req received");
        } else {
            if (mOutingSettingReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: outing_setting req cleared, but still waitting for confirming");
            }
        }

        if ((data[1] & (1 << 2)) != 0) {
            newStates &= ~BatchSwitch.Switch.BATCH_LIGHT_OFF;
        } else {
            newStates |= BatchSwitch.Switch.BATCH_LIGHT_OFF;
        }

        if ((data[1] & (1 << 3)) != 0) {
            newStates &= ~BatchSwitch.Switch.POWER_SAVING;
        } else {
            newStates |= BatchSwitch.Switch.POWER_SAVING;
        }

        if ((data[1] & (1 << 4)) != 0) {
            newStates |= BatchSwitch.Switch.ELEVATOR_UP_CALL;
            mElevatorCallReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: elevator_up_call req received");
        } else {
            if (mElevatorCallReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: elevator_call req cleared, but still waitting for confirming");
            }
        }

        if ((data[1] & (1 << 5)) != 0) {
            newStates |= BatchSwitch.Switch.ELEVATOR_DOWN_CALL;
            mElevatorCallReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: elevator_down_call req received");
        } else {
            if (mElevatorCallReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: elevator_call req cleared, but still waitting for confirming");
            }
        }

        if (newStates == curStates) {
            return PARSE_OK_NONE; // nothing changed
        }

        // Update switch state.
        outProps.put(BatchSwitch.PROP_SWITCH_STATES, newStates);

        // Update common on/off state too.
        final boolean isOn = ((newStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) == 0);
        outProps.put(HomeDevice.PROP_ONOFF, isOn);

        return PARSE_OK_STATE_UPDATED;
    }

    protected boolean onSetSwitchStatesTask(PropertyMap reqProps, PropertyMap outProps) {
        long curStates = (Long) getProperty(BatchSwitch.PROP_SWITCH_STATES).getValue();
        long newStates = (Long) reqProps.get(BatchSwitch.PROP_SWITCH_STATES).getValue();
        long difStates = curStates ^ newStates;

        final KSAddress address = (KSAddress)getAddress();
        if (address.getDeviceSubId().isFull() || address.getDeviceSubId().isAll()) {
           // Send full control command
            final KSPacket controlPacket = makeFullControlReq(newStates);
            if (controlPacket != null) {
                sendPacket(controlPacket);
            }
            return true;
        }

        if ((difStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) == 0) {
            // Check also changes of on/off state, and overide switch state if so.
            final boolean curOnOff = (Boolean) getProperty(HomeDevice.PROP_ONOFF).getValue();
            final boolean newOnOff = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
            if (newOnOff != curOnOff) {
                if (newOnOff == false) {
                    newStates |= BatchSwitch.Switch.BATCH_LIGHT_OFF;
                } else {
                    newStates &= ~BatchSwitch.Switch.BATCH_LIGHT_OFF;
                }
                difStates = curStates ^ newStates; // Recalulate diff of current and new.
            }
        }

        if ((difStates & BatchSwitch.Switch.GAS_LOCKING) != 0) {
            // Update the property map with changed value directly since wallpad manages this state.
            outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.GAS_LOCKING, newStates);
            // Reschedule the status request command since the states that must be reported to the device.
            requestUpdate();
        }

        if ((difStates & BatchSwitch.Switch.OUTING_SETTING) != 0) {
            // Update the property map with changed value directly since wallpad manages this state.
            outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.OUTING_SETTING, newStates);
            // Reschedule the status request command since the states that must be reported to the device.
            requestUpdate();
        }

        if ((difStates & BatchSwitch.Switch.ELEVATOR_UP_CALL) != 0) {
            // There's no way to update elevator's state to device, so don't allow set the bit but clear.
            if ((newStates & BatchSwitch.Switch.ELEVATOR_UP_CALL) == 0) {
                outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.ELEVATOR_UP_CALL, 0);
            }
        }

        if ((difStates & BatchSwitch.Switch.ELEVATOR_DOWN_CALL) != 0) {
            // There's no way to update elevator's state to device, so don't allow set the bit but clear.
            if ((newStates & BatchSwitch.Switch.ELEVATOR_DOWN_CALL) == 0) {
                outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.ELEVATOR_DOWN_CALL, 0);
            }
        }

        boolean needSendSingleControlCmd = false;
        needSendSingleControlCmd |= ((difStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) != 0);
        needSendSingleControlCmd |= ((difStates & BatchSwitch.Switch.POWER_SAVING) != 0);
        if (needSendSingleControlCmd) {
            // Send single control command
            final KSPacket controlPacket = makeSingleControlReq(newStates);
            if (controlPacket != null) {
                sendPacket(controlPacket);
            }
        }

        final KSPacket switchSettingResoultPacket = makeSwitchSettingResultPacketIf(reqProps);
        if (switchSettingResoultPacket != null) {
            // Send the result of interaction against user requests that are triggered from the device.
            sendPacket(switchSettingResoultPacket);
            // Reschedule the status request command since some states that the device is interested in possibly has been changed.
            requestUpdate();
        }

        return true;
    }

    protected boolean onSetDisplayStatesTask(PropertyMap reqProps, PropertyMap outProps) {
        final PropertyValue displayStatesProp = reqProps.get(BatchSwitch.PROP_DISPLAY_STATES);
        final long displayStates = (Long) displayStatesProp.getValue();
        final long supportedDisplays = (Long) reqProps.get(BatchSwitch.PROP_SUPPORTED_DISPLAYS).getValue();

        if ((supportedDisplays & displayStates) == 0) {
            return false;
        }

        if (displayStates == BatchSwitch.Display.ELEVATOR_FLOOR) {
            byte[] extraData = displayStatesProp.getExtraData();
            if (extraData != null && extraData.length > 0) {
                if (DBG) Log.d(TAG, "on-action: send elevator floor display req. data len:" + extraData.length);
                sendPacket(createPacket(CMD_ELEVATOR_FLOOR_DISPLAY_REQ, extraData));
            }
        }

        return true;
    }

    @Override
    protected KSPacket makeStatusReq(PropertyMap props) {
        return createPacket(CMD_STATUS_REQ, makeStatusReqData(props));
    }

    protected byte[] makeStatusReqData(PropertyMap props) {
        return makeStatusReqData(props, new byte[] {0});
    }

    protected byte[] makeStatusReqData(PropertyMap props, byte[] data) {
        long states = props.get(BatchSwitch.PROP_SWITCH_STATES, Long.class);

        if ((states & BatchSwitch.Switch.GAS_LOCKING) == 0) {
            data[0] |= (byte)(1 << 0);
        }

        if ((states & BatchSwitch.Switch.OUTING_SETTING) != 0) {
            data[0] |= (byte)(1 << 1);
        }

        return data;
    }

    private KSPacket makeSingleControlReq(long switchStates) {
        final byte[] data = new byte[1];
        data[0] = (byte) 0x00;
        if ((switchStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) == 0) data[0] |= (byte)(1 << 0);
        if ((switchStates & BatchSwitch.Switch.POWER_SAVING) == 0) data[0] |= (byte)(1 << 1);
        return createPacket(CMD_SINGLE_CONTROL_REQ, data);
    }

    private KSPacket makeFullControlReq(long switchStates) {
        final byte[] data = new byte[2];
        data[0] = (byte) (((switchStates & BatchSwitch.Switch.BATCH_LIGHT_OFF) == 0) ? 0x00 : 0xFF);
        data[1] = (byte) (((switchStates & BatchSwitch.Switch.POWER_SAVING) == 0) ? 0x00 : 0xFF);
        return createPacket(CMD_FULL_CONTROL_REQ, data);
    }

    protected KSPacket makeSwitchSettingResultPacketIf(PropertyMap props) {
        if (!getDeviceSubId().isSingle()) return null;

        final byte[] data = makeSwitchSettingResultDataIf(props);
        boolean hasResult = false;
        for (int i=0; i<data.length; i++) if (data[i] != 0) hasResult |= true;

        // Create valid packet only if the data has valid result to send.
        return (hasResult) ? createPacket(CMD_SWITCH_SETTING_RESULT_REQ, data) : null;
    }

    protected byte[] makeSwitchSettingResultDataIf(PropertyMap props) {
        return makeSwitchSettingResultDataIf(props, new byte[] {0});
    }

    protected byte[] makeSwitchSettingResultDataIf(PropertyMap props, byte[] data) {
        final long newStates = (Long) props.get(BatchSwitch.PROP_SWITCH_STATES).getValue();

        if (mGasLockingReqReceived) {
            long gasLockingState = ((newStates & BatchSwitch.Switch.GAS_LOCKING) != 0L) ? 1L : 0L;
            long gasLockingReqGot = 1L;
            long gasLockingFailed = (HACK_NEVER_FAIL) ? (0L) : (1L - gasLockingState);
            data[0] |= (byte) (gasLockingReqGot << 0);
            data[0] |= (byte) (gasLockingFailed << 1);
            mGasLockingReqReceived = false;
            if (DBG) Log.d(TAG, "make-switch-set-result: gas_locking, " +
                                "got:" + gasLockingReqGot + " failed:" + gasLockingFailed);
        }

        if (mOutingSettingReqReceived) {
            long outingSettingState = ((newStates & BatchSwitch.Switch.OUTING_SETTING) != 0L) ? 1 : 0;
            long outingSettingReqGot = 1L;
            long outingSettingFailed = (HACK_NEVER_FAIL) ? (0L) : (1L - outingSettingState);
            data[0] |= (byte) (outingSettingReqGot << 2);
            data[0] |= (byte) (outingSettingFailed << 3);
            mOutingSettingReqReceived = false;
            if (DBG) Log.d(TAG, "make-switch-set-result: outing_setting, " +
                                "got:" + outingSettingReqGot + " failed:" + outingSettingFailed);
        }

        if (mElevatorCallReqReceived) {
            long elevatorCallState = 0L;
            elevatorCallState |= ((newStates & BatchSwitch.Switch.ELEVATOR_UP_CALL) != 0L) ? 1 : 0;
            elevatorCallState |= ((newStates & BatchSwitch.Switch.ELEVATOR_DOWN_CALL) != 0L) ? 1 : 0;
            long elevatorCallReqGot = 1L;
            long elevatorCallFailed = (HACK_NEVER_FAIL) ? (0L) : (1L - elevatorCallState);
            data[0] |= (byte) (elevatorCallReqGot << 4);
            data[0] |= (byte) (elevatorCallFailed << 5);
            mElevatorCallReqReceived = false;
            if (DBG) Log.d(TAG, "make-switch-set-result: elevator_call, " +
                                "got:" + elevatorCallReqGot + " failed:" + elevatorCallFailed);
        }

        return data;
    }
}
