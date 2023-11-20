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
 * A device class for blocking standby or overload power.
 */
public class PowerSaver extends HomeDevice {
    private static final String PROP_PREFIX = "ps.";

    /**
     * States
     */
    public @interface State {
        long OVERLOAD_DETECTED = 1L << 0;
        long STANDBY_DETECTED = 1L << 1;
    }

    /**
     * Settings
     */
    public @interface Setting {
        long STANDBY_BLOCKING_ON = 1L << 0;
    }

    /** Property: Bit fields that is indicating wheter each {@link State} is supproted */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_SUPPORTED_STATES    = PROP_PREFIX + "states.support";

    /** Property: Bit fields that is indicating each state is detected or not */
    @PropertyDef(valueClass=State.class, formatHint="bits")
    public static final String PROP_CURRENT_STATES      = PROP_PREFIX + "states.current";

    /** Property: Bit fields that is indicating wheter each {@link Setting} is supproted */
    @PropertyDef(valueClass=Setting.class, formatHint="bits")
    public static final String PROP_SUPPORTED_SETTINGS  = PROP_PREFIX + "settings.support";

    /** Property: Bit fields that is indicating each setting is set or not */
    @PropertyDef(valueClass=Setting.class, formatHint="bits")
    public static final String PROP_CURRENT_SETTINGS    = PROP_PREFIX + "settings.current";

    /** Property: The level (watt) of standby power consumption that has been set to device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_STANDBY_CONSUMPTION = PROP_PREFIX + "consumption.standby";

    /** Property: The level (watt) of current power consumption that is retrieved from device */
    @PropertyDef(valueClass=Float.class)
    public static final String PROP_CURRENT_CONSUMPTION = PROP_PREFIX + "consumption.current";

    /**
     * Construct new instance. Don't call this directly.
     */
    public PowerSaver(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.POWER_SAVER;
    }

    /**
     * Returns {@code true} if given state is supported on this device.
     * @return {@code true} if supported.
     */
    public boolean hasState(@State long state) {
        return (getProperty(PROP_SUPPORTED_STATES, Long.class) & state) != 0L;
    }

    /**
     * Gets whether if given state is detected or not.
     * @param state One of {@link State}.
     * @return True if the state is detected.
     */
    public boolean getState(@State long state) {
        return (getStates() & state) != 0L;
    }

    /**
     * Retrieves all the detecting states.
     * @return Bits of {@link State}.
     */
    public @State long getStates() {
        return getProperty(PROP_CURRENT_STATES, Long.class);
    }

    /**
     * Sets multiple states to be set or not. If a state is read-only, it will
     * be ignored.
     * @param states Bits of {@link State}
     * @param set {@code true} for set or {@code false} for unset
     */
    public void setStates(@State long states, boolean set) {
        final long curStates = (Long) getStagedProperty(PROP_CURRENT_STATES, Long.class);
        final long newStates = (set) ? (curStates | states) : (curStates & ~states);
        setProperty(PROP_CURRENT_STATES, Long.class, newStates);
    }

    /**
     * Returns {@code true} if given setting is supporting in this device.
     * @return {@code true} if supported, otherwise {@code false}
     */
    public boolean hasSetting(long setting) {
        return (getProperty(PROP_SUPPORTED_SETTINGS, Long.class) & setting) != 0L;
    }

    /**
     * Gets if given setting is on or not.
     * @param setting One of {@link Setting}.
     * @return True if the setting is on.
     */
    public boolean getSetting(@Setting long setting) {
        return (getSettings() & setting) != 0L;
    }

    /**
     * Retrieves all the setting states.
     * @return Bits of {@link Setting}.
     */
    public long getSettings() {
        return getProperty(PROP_CURRENT_SETTINGS, Long.class);
    }

    /**
     * Sets multiple settings to be set or not. If a setting is read-only,
     * it will be ignored.
     * @param settings Bits of {@link Setting}
     * @param set {@code true} for set or {@code false} for unset
     */
    public void setSettings(@Setting long settings, boolean set) {
        final long curSettings = (Long) getStagedProperty(PROP_CURRENT_SETTINGS, Long.class);
        final long newSettings = (set) ? (curSettings | settings) : (curSettings & ~settings);
        setProperty(PROP_CURRENT_SETTINGS, Long.class, newSettings);
    }

    /**
     * Gets current power consumption
     * @return The power value in the unit of watt
     */
    public float getCurrentConsumption() {
        return getProperty(PROP_CURRENT_CONSUMPTION, Float.class);
    }

    /**
     * Gets the consumption level that is set on device to block standby power.
     * @return The power value in the unit of watt
     */
    public float getStandbyConsumption() {
        return getProperty(PROP_STANDBY_CONSUMPTION, Float.class);
    }

    /**
     * Sets the level of standby power consumption so that the device can block.
     * power if the comsumption reaches under the set level.
     * @param watt The power value (watt) that should be set as standby power level.
     */
    public void setStandbyConsumption(float watt) {
        setProperty(PROP_STANDBY_CONSUMPTION, Float.class, watt);
    }

}
