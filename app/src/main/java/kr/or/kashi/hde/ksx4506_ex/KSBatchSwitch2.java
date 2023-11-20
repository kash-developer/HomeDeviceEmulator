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

package kr.or.kashi.hde.ksx4506_ex;

import android.util.Log;

import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.device.BatchSwitch;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSBatchSwitch;
import kr.or.kashi.hde.ksx4506.KSPacket;

import java.util.Map;

/**
 * Extended implementation of [KS X 4506] the batch-switch
 */
public class KSBatchSwitch2 extends KSBatchSwitch {
    private static final String TAG = "KSBatchSwitch2";
    private static final boolean DBG = true;

    public static final int CMD_LIFE_INFORMATION_DISPLAY_REQ    = 0x51;
    public static final int CMD_3WAYLIGHT_STATUS_UPDATE_REQ     = 0x52;
    public static final int CMD_PARKING_LOCATION_DISPLAY_REQ    = 0x53;
    public static final int CMD_COOKTOP_STATUS_UPDATE_REQ       = 0x55;
    public static final int CMD_COOKTOP_SETTING_RESULT_REQ      = 0x56;
    public static final int CMD_ELEVATOR_ARRIVAL_UPDATE_REQ     = 0x57;

    private boolean mCooktopOffReqReceived = false;
    private boolean mHeaterSavingReqReceived = false;
    private boolean mHeaterSavingOffReceived = false;

    public KSBatchSwitch2(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    @Override
    protected @ParseResult int parseCharacteristicRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int res = super.parseCharacteristicRsp(packet, outProps);
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        long supportedSwitches = (long) outProps.get(BatchSwitch.PROP_SUPPORTED_SWITCHES).getValue();
        long supportedDisplays = (long) outProps.get(BatchSwitch.PROP_SUPPORTED_DISPLAYS).getValue();

        if ((supportedDisplays & BatchSwitch.Display.ELEVATOR_FLOOR) != 0) {
            supportedDisplays |= BatchSwitch.Display.ELEVATOR_ARRIVAL;
        }

        final int data1 = packet.data[1] & 0xFF;
        if ((data1 & (1 << 5)) != 0) supportedDisplays |= BatchSwitch.Display.LIFE_INFORMATION;
        if ((data1 & (1 << 6)) != 0) supportedSwitches |= BatchSwitch.Switch.THREEWAY_LIGHT;
        if ((data1 & (1 << 7)) != 0) supportedDisplays |= BatchSwitch.Display.PARKING_LOCATION;

        final int data2 = packet.data[2] & 0xFF;
        // HACK: bit of the probability of precipitation is not assgined, just set the bit of life information again.
        if ((data2 & (1 << 0)) != 0) supportedDisplays |= BatchSwitch.Display.LIFE_INFORMATION;
        if ((data2 & (1 << 1)) != 0) supportedSwitches |= BatchSwitch.Switch.COOKTOP_OFF;
        if ((data2 & (1 << 2)) != 0) supportedSwitches |= BatchSwitch.Switch.HEATER_SAVING;

        outProps.put(BatchSwitch.PROP_SUPPORTED_SWITCHES, supportedSwitches);
        outProps.put(BatchSwitch.PROP_SUPPORTED_DISPLAYS, supportedDisplays);

        // Set the display states also on if supported.
        outProps.put(BatchSwitch.PROP_DISPLAY_STATES, supportedDisplays);

        return PARSE_OK_PEER_DETECTED;
    }

    @Override
    protected @ParseResult int parseStatusRspBytes(byte[] data, PropertyMap outProps) {
        @ParseResult int res = super.parseStatusRspBytes(data, outProps);
        if (res <= PARSE_ERROR_UNKNOWN) {
            return res;
        }

        long supported = (long) outProps.get(BatchSwitch.PROP_SUPPORTED_SWITCHES).getValue();
        long curStates = (long) outProps.get(BatchSwitch.PROP_SWITCH_STATES).getValue();
        long newStates = curStates;

        if ((data[1] & (1 << 6)) != 0) {
            newStates |= BatchSwitch.Switch.THREEWAY_LIGHT;
        } else {
            newStates &= ~BatchSwitch.Switch.THREEWAY_LIGHT;
        }

        if ((data[1] & (1 << 7)) != 0) {
            newStates |= BatchSwitch.Switch.COOKTOP_OFF;
            mCooktopOffReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: cooktop_off req received");
        } else {
            if (mCooktopOffReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: cooktop_off req cleared, but still waitting for confirming");
            }
        }

        if ((data[2] & (1 << 0)) != 0) {
            newStates |= BatchSwitch.Switch.HEATER_SAVING;
            mHeaterSavingReqReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: heater_saving req received");
        } else {
            if (mHeaterSavingReqReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: heater_saving req cleared, but still waitting for confirming");
            }
        }

        if ((data[2] & (1 << 1)) != 0) {
            newStates &= ~BatchSwitch.Switch.HEATER_SAVING;
            mHeaterSavingOffReceived = true;
            if (DBG) Log.d(TAG, "parse-usr-req-frame: heater_saving off received");
        } else {
            if (mHeaterSavingOffReceived) {
                if (DBG) Log.d(TAG, "parse-usr-req-frame: heater_saving off cleared, but still waitting for confirming");
            }
        }

        if (newStates == curStates) {
            return res;
        }

        outProps.put(BatchSwitch.PROP_SWITCH_STATES, newStates);

        return PARSE_OK_STATE_UPDATED;
    }

    @Override
    protected boolean onSetSwitchStatesTask(PropertyMap reqProps, PropertyMap outProps) {
        /* boolean res = */ super.onSetSwitchStatesTask(reqProps, outProps); // call super

        final long curStates = (Long) getProperty(BatchSwitch.PROP_SWITCH_STATES).getValue();
        final long newStates = reqProps.get(BatchSwitch.PROP_SWITCH_STATES, Long.class);
        final long difStates = curStates ^ newStates;

        if ((difStates & BatchSwitch.Switch.THREEWAY_LIGHT) != 0) {
            // Update the property map with changed value directly since wallpad manages its state.
            outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.THREEWAY_LIGHT, newStates);
            // Send current state of 3-way-light to device
            int threewayOn = ((newStates & BatchSwitch.Switch.THREEWAY_LIGHT) != 0) ? (0x01) : (0x00);
            sendPacket(createPacket(CMD_3WAYLIGHT_STATUS_UPDATE_REQ, (byte)threewayOn));
        }

        if ((difStates & BatchSwitch.Switch.COOKTOP_OFF) != 0) {
            // Update the property map with changed value directly since wallpad manages its state.
            outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.COOKTOP_OFF, newStates);
            // Send current state of cook-top to device
            int cooktopOff = ((newStates & BatchSwitch.Switch.COOKTOP_OFF) != 0) ? (1) : (0);
            sendPacket(createPacket(CMD_COOKTOP_STATUS_UPDATE_REQ, (byte)(1 - cooktopOff)));
        }

        if (mCooktopOffReqReceived) {
            long cooktopOffState = ((newStates & BatchSwitch.Switch.GAS_LOCKING) != 0L) ? 1 : 0;
            long cooktopOffReqGot = 1L;
            long cooktopOffFailed = (HACK_NEVER_FAIL) ? (0L) : (1L - cooktopOffState);

            mCooktopOffReqReceived = false;

            if (DBG) Log.d(TAG, "make-switch-set-result: cooktop_off, " +
                                "got:" + cooktopOffReqGot + " failed:" + cooktopOffFailed);

            // LOOK! The setting result of cooktop should be sent by its dedicated commmand
            // (0x56) instead of standard combined command (CMD_SWITCH_SETTING_RESULT_REQ:0x43).
            byte[] data = new byte[] { (byte)((cooktopOffReqGot << 0) | (cooktopOffFailed << 1)) };
            sendPacket(createPacket(CMD_COOKTOP_SETTING_RESULT_REQ, data));
        }

        if ((difStates & BatchSwitch.Switch.HEATER_SAVING) != 0) {
            // Update the property map with changed value directly since wallpad manages its state.
            outProps.putBits(BatchSwitch.PROP_SWITCH_STATES, BatchSwitch.Switch.HEATER_SAVING, newStates);
            // Reschedule the status request command since the states that must be reported to the device.
            requestUpdate();
        }

        return true;
    }

    @Override
    protected boolean onSetDisplayStatesTask(PropertyMap reqProps, PropertyMap outProps) {
        /* boolean res = */ super.onSetDisplayStatesTask(reqProps, outProps); // call super

        final PropertyValue displayStatesProp = reqProps.get(BatchSwitch.PROP_DISPLAY_STATES);
        final long displayStates = (Long) displayStatesProp.getValue();
        final long supportedDisplays = reqProps.get(BatchSwitch.PROP_SUPPORTED_DISPLAYS, Long.class);

        if ((supportedDisplays & displayStates) == 0) {
            return false; // not supported
        }

        if (displayStates == BatchSwitch.Display.ELEVATOR_ARRIVAL) {
            if (DBG) Log.d(TAG, "on-action: send elevator_arrival.");
            sendPacket(createPacket(CMD_ELEVATOR_ARRIVAL_UPDATE_REQ)); // no payload
        }

        byte[] extraData = displayStatesProp.getExtraData();
        if (extraData == null || extraData.length == 0) {
            return false; // no data to display
        }

        if (displayStates == BatchSwitch.Display.LIFE_INFORMATION) {
            if (DBG) Log.d(TAG, "on-action: send life information. data len:" + extraData.length);
            sendPacket(createPacket(CMD_LIFE_INFORMATION_DISPLAY_REQ, extraData));
        }

        if (displayStates == BatchSwitch.Display.PARKING_LOCATION) {
            if (DBG) Log.d(TAG, "on-action: send parking location. data len:" + extraData.length);
            sendPacket(createPacket(CMD_PARKING_LOCATION_DISPLAY_REQ, extraData));
        }

        return true;
    }

    @Override
    protected byte[] makeStatusReqData(PropertyMap props, byte[] data) {
        super.makeStatusReqData(props, data); // call super

        long states = props.get(BatchSwitch.PROP_SWITCH_STATES, Long.class);

        if ((states & BatchSwitch.Switch.HEATER_SAVING) != 0) {
            data[0] |= (byte)(1 << 2);
        }

        return data;
    }

    @Override
    protected byte[] makeSwitchSettingResultDataIf(PropertyMap props) {
        // Ignore super method since the size of data should be extended.
        return makeSwitchSettingResultDataIf(props, new byte[] {0, 0});
    }

    @Override
    protected byte[] makeSwitchSettingResultDataIf(PropertyMap props, byte[] data) {
        super.makeSwitchSettingResultDataIf(props, data); // call super

        final long newStates = (Long) props.get(BatchSwitch.PROP_SWITCH_STATES).getValue();

        if (mHeaterSavingReqReceived) {
            long heaterSavingState = ((newStates & BatchSwitch.Switch.HEATER_SAVING) != 0L) ? 1L : 0L;
            long heaterSavingReqGot = 1L;
            long heaterSavingFailed = (HACK_NEVER_FAIL) ? (0L) : (1L - heaterSavingState);
            data[1] |= (byte) (heaterSavingReqGot << 0);
            data[1] |= (byte) (heaterSavingFailed << 1);
            mHeaterSavingReqReceived = false;
            if (DBG) Log.d(TAG, "make-switch-set-result: heater_saving, " +
                                "got:" + heaterSavingReqGot + " failed:" + heaterSavingFailed);
        }

        if (mHeaterSavingOffReceived) {
            long state = ((newStates & BatchSwitch.Switch.HEATER_SAVING) != 0L) ? 1L : 0L;
            long confirmed = 1L;
            long failed = (HACK_NEVER_FAIL) ? (0L) : (state);
            data[1] |= (byte) (confirmed << 2);             // bit 2
            data[1] |= (byte) (failed << 3);                // bit 3
            mHeaterSavingOffReceived = false;
            if (DBG) Log.d(TAG, "make-switch-set-result: heater_saving off, " +
                                "confirmed:" + confirmed + " failed:" + failed);
        }

        return data;
    }
}
