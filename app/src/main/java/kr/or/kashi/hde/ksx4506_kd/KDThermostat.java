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

import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.DeviceStatePollee;
import kr.or.kashi.hde.base.ByteArrayBuffer;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.HomePacket;
import kr.or.kashi.hde.MainContext;
import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.device.Thermostat;
import kr.or.kashi.hde.ksx4506.KSAddress;
import kr.or.kashi.hde.ksx4506.KSDeviceContextBase;
import kr.or.kashi.hde.ksx4506.KSPacket;
import kr.or.kashi.hde.ksx4506.KSUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
  * Non-standard implementation of [KS X 4506] the thermostat
 */
public class KDThermostat extends KSThermostat {
    private static final String TAG = "KDThermostat";
    private static final boolean DBG = true;

    public static final int CMD_POWER_OFF_REQ = 0x50;
    public static final int CMD_POWER_OFF_RSP = 0xD0;

    public boolean mFirstStatusRetreived = false;
    public boolean mPowerControlSupported = false;
    public long mSavedFunctionState = 0;

    public KDThermostat(MainContext mainContext, Map defaultProps) {
        super(mainContext, defaultProps);
    }

    private void resetInternalStates() {
        if (isMaster()) {
            mFirstStatusRetreived = false;
            mPowerControlSupported = false;
        } else {
            mFirstStatusRetreived = true;
            mPowerControlSupported = true;
        }
    }

    @Override
    public void onAttachedToStream() {
        resetInternalStates();
        super.onAttachedToStream(); // call super
    }

    @Override
    public void setPollPhase(@DeviceStatePollee.Phase int phase, long interval) {
        super.setPollPhase(phase, interval); // call super
        if (phase == DeviceStatePollee.Phase.NAPPING) {
            resetInternalStates();
        }
    }

    @Override
    public @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps) {
        KSPacket ksPacket = (KSPacket) packet;

        switch (ksPacket.commandType) {
            case CMD_POWER_OFF_REQ: {
                if (packet.data().length < 1) return PARSE_OK_NONE;
                final boolean newOn = (packet.data()[0] == 0);
                outProps.put(HomeDevice.PROP_ONOFF, newOn);
                syncOnOffToFunctionState(outProps, outProps);

                // HACK/WTF: Must response as only group packet form!
                final KDThermostat device = (getParent() != null) ? ((KDThermostat)getParent()) : (this);
                final ByteArrayBuffer data = new ByteArrayBuffer();
                device.makeStatusRspData(getReadPropertyMap(), data);
                sendPacket(createPacket(CMD_POWER_OFF_RSP, data.toArray()));
                return PARSE_OK_ACTION_PERFORMED;
            }

            case CMD_POWER_OFF_RSP: {
                if (!mPowerControlSupported) {
                    mPowerControlSupported = true;
                    syncVendorPropsToParentAndSiblings(outProps);
                    updateOnOffProperty(outProps);
                }
                return PARSE_OK_ACTION_PERFORMED;
            }
        }

        int res = super.parsePayload(packet, outProps); // call super

        mSavedFunctionState = outProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
        outProps.put(HomeDevice.PROP_ONOFF, (mSavedFunctionState != 0L));

        return res;
    }

    @Override
    protected @ParseResult int parseStatusRsp(KSPacket packet, PropertyMap outProps) {
        @ParseResult int result = super.parseStatusRsp(packet, outProps); // call super
        if (result > PARSE_OK_NONE) {
            if (isSingleDevice()) {
                final boolean onoff = updateOnOffProperty(outProps);
                if (!mFirstStatusRetreived) {
                    mFirstStatusRetreived = true;
                    syncVendorPropsToParentAndSiblings(outProps);
                    sendControlPacket(CMD_POWER_OFF_REQ, (byte)(onoff ? 0x00 : 0x01));
                }
            } else {
                if (!mFirstStatusRetreived) {
                    mFirstStatusRetreived = true;
                    syncVendorPropsFromFirstChild();
                }
            }
        }
        return result;
    }

    @Override
    protected boolean onPowerControlTask(PropertyMap reqProps, PropertyMap outProps) {
        super.onPowerControlTask(reqProps, outProps); // call super
        sendOnOffControlPacket(reqProps);
        return true;
    }

    @Override
    protected boolean onPowerControlTaskInSlave(PropertyMap reqProps, PropertyMap outProps) {
        super.onPowerControlTaskInSlave(reqProps, outProps); // call super
        syncOnOffToFunctionState(reqProps, outProps);
        return true;
    }

    protected boolean onFunctionControlTaskInSlave(PropertyMap reqProps, PropertyMap outProps) {
        super.onFunctionControlTask(reqProps, outProps); // call super
        mSavedFunctionState = reqProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
        outProps.put(Thermostat.PROP_FUNCTION_STATES, mSavedFunctionState);
        outProps.put(HomeDevice.PROP_ONOFF, (mSavedFunctionState != 0L));
        return true;
    }

    private void syncOnOffToFunctionState(PropertyMap reqProps, PropertyMap outProps) {
        final boolean onoff = reqProps.get(HomeDevice.PROP_ONOFF, Boolean.class);
        if (onoff) {
            if (mSavedFunctionState == 0L) mSavedFunctionState |= Thermostat.Function.HEATING;
            outProps.put(Thermostat.PROP_FUNCTION_STATES, mSavedFunctionState);
            outProps.put(HomeDevice.PROP_ONOFF, true);
        } else {
            mSavedFunctionState = outProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
            outProps.put(Thermostat.PROP_FUNCTION_STATES, 0L);
            outProps.put(HomeDevice.PROP_ONOFF, false);
        }
    }

    private void syncVendorPropsToParentAndSiblings(PropertyMap props) {
        final DeviceContextBase parent = getParent();
        if (parent == null) return;
        syncVendorPropsToOther(props, parent);
        for (DeviceContextBase sibling: parent.getChildren()) {
            syncVendorPropsToOther(props, sibling);
        }
    }

    private void syncVendorPropsFromFirstChild() {
        KDThermostat firstChild = getChildAt(KDThermostat.class, 0);
        if (firstChild != null) {
            syncVendorPropsToOther(firstChild.getReadPropertyMap(), this);
        }
    }

    private void syncVendorPropsToOther(PropertyMap props, DeviceContextBase other) {
        if (props == null || other == null) return;

        if (other instanceof KDThermostat) {
            final KDThermostat otherKdt = (KDThermostat) other;
            otherKdt.mFirstStatusRetreived = mFirstStatusRetreived;
            otherKdt.mPowerControlSupported = mPowerControlSupported;
        }

        List<PropertyValue> otherProps = new ArrayList<>();
        otherProps.add(props.get(HomeDevice.PROP_VENDOR_CODE));
        otherProps.add(props.get(HomeDevice.PROP_VENDOR_NAME));
        other.updateProperties(otherProps);
    }

    private boolean updateOnOffProperty(PropertyMap outProps) {
        // HACK: No field to get the state of power off in standard,
        // so let's guess it from each state of functions.
        final long states = outProps.get(Thermostat.PROP_FUNCTION_STATES, Long.class);
        final boolean onoff = mPowerControlSupported ? (states != 0L) : ((states & Thermostat.Function.HEATING) != 0);
        outProps.put(HomeDevice.PROP_ONOFF, onoff);
        return onoff;
    }

    private void sendOnOffControlPacket(PropertyMap reqProps) {
        if (mPowerControlSupported) {
            final boolean isOn = (Boolean) reqProps.get(HomeDevice.PROP_ONOFF).getValue();
            sendControlPacket(CMD_POWER_OFF_REQ, (byte)(isOn ? 0x00 : 0x01));
        } else {
            byte state = (byte) (reqProps.get(HomeDevice.PROP_ONOFF, Boolean.class) ? 1 : 0);
            sendControlPacket(CMD_HEATING_STATE_REQ, state);
        }
    }
}
