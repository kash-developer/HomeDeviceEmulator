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

package kr.or.kashi.hde.device;

import kr.or.kashi.hde.base.PropertyDef;
import kr.or.kashi.hde.DeviceContextBase;
import kr.or.kashi.hde.HomeDevice;

/**
 * A device class for controlling the gas valve.
 */
public class GasValve extends HomeDevice {
    private static final String PROP_PREFIX = "gv.";

    /**
     * States
     */
    public @interface State {
        long GAS_VALVE      = 1L << 0;
        long INDUCTION      = 1L << 1;
    }

    /**
     * Alarms
     */
    public @interface Alarm {
        long EXTINGUISHER_BUZZING   = 1L << 1;
        long GAS_LEAKAGE_DETECTED   = 1L << 2;
    }

    /** Property of bits of supported {@link State}s. */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_SUPPORTED_STATES    = PROP_PREFIX + "supported_states";

    /** Property of bits that is indicating which states are activated or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_STATES      = PROP_PREFIX + "current_states";

    /** Property of bits of supported {@link Alarm}s. */
    @PropertyDef(valueClass=Alarm.class, formatHint="bits")
    public static final String PROP_SUPPORTED_ALARMS    = PROP_PREFIX + "supported_alarms";

    /** Property of bits that is indicating which alarms are triggered or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_ALARMS      = PROP_PREFIX + "current_alarms";

    /**
     * Construct new instance. Don't call this directly.
     */
    public GasValve(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.GAS_VALVE;
    }

    /**
     * Returns whether if given state is supported or not.
     * @param state One {@code long} id of {@link State}.
     * @return True if the state is supported.
     */
    public boolean isSupportedState(@State long state) {
        return (getPropertyL(PROP_SUPPORTED_STATES) & state) != 0L;
    }

    /**
     * Retrieves the current state of activation.
     * @param state One of {@link State}
     * @return Whether given state is activated or not.
     */
    public boolean getState(@State long state) {
        return (getAllStates() & state) != 0L;
    }

    /**
     * Returns the all states that are defined {@link State}. Some states
     * can be {@code false} according to the supporting.
     * @see #isSupportedState(long)
     * @return Bits of {@link State} for all states
     */
    public @State long getAllStates() {
        return getPropertyL(PROP_CURRENT_STATES);
    }

    /**
     * Sets specific states to be set or not.
     * @param states Bits of {@link State}
     * @param set true if given state(s) have to be set.
     */
    public void setStates(long states, boolean set) {
        final long curStates = (Long) getStagedProperty(PROP_CURRENT_STATES, Long.class);
        final long newStates = (set) ? (curStates | states) : (curStates & ~states);
        setProperty(PROP_CURRENT_STATES, Long.class, newStates);
    }

    /**
     * Returns whether if given alarm is supported or not.
     * @param alarm One {@code long} id of {@link Alarm}.
     * @return True if the alarm is supported.
     */
    public boolean isSupportedAlarm(@Alarm long alarm) {
        return (getPropertyL(PROP_SUPPORTED_ALARMS) & alarm) != 0L;
    }

    /**
     * Determine if given alarm is triggered.
     * @param alarm One of {@link Alarm}
     * @return Whether given alarm is triggered or not.
     */
    public boolean isAlarmed(@Alarm long alarm) {
        return (getAlarmBits() & alarm) != 0L;
    }

    /**
     * Returns the all alarms that are defined {@link Alarm}. Each bits of
     * alarms are set be {@code true} if triggered, or {@code false} if
     * not triggered or not supported.
     * @see #isSupportedAlarm(long)
     * @return Bits of {@link Alarm} for all alarms
     */
    public @Alarm long getAlarmBits() {
        return getPropertyL(PROP_CURRENT_ALARMS);
    }

    /**
     * Clear bits of triggered alarms.
     * @param alarms Bits of {@link Alarm}
     */
    public void clearAlarms(@Alarm long alarms) {
        final long curAlarms = (Long) getStagedProperty(PROP_CURRENT_ALARMS, Long.class);
        final long newAlarms = (curAlarms & ~alarms); // Clear bits
        setProperty(PROP_CURRENT_ALARMS, Long.class, newAlarms);
    }
}
