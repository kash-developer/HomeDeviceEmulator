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
 * A device class that has mutiple sensors especially for security.
 */
public class BinarySensors extends HomeDevice {
    private static final String PROP_PREFIX = "ss.";

    /**
     * Sensors
     */
    public @interface Sensor {
        long SENSOR_1  = 1L << 0;
        long SENSOR_2  = 1L << 1;
        long SENSOR_3  = 1L << 2;
        long SENSOR_4  = 1L << 3;
        long SENSOR_5  = 1L << 4;
        long SENSOR_6  = 1L << 5;
        long SENSOR_7  = 1L << 6;
        long SENSOR_8  = 1L << 7;
    }

    /** Property : Get or settable bits for the enabled state of each sensors */
    @PropertyDef(valueClass=Sensor.class, formatHint="bits")
    public static final String PROP_ENABLED_SENSORS = PROP_PREFIX + "sensors.enabled";

    /** Property : Get or settable bits of each sensors on/off state */
    @PropertyDef(valueClass=Sensor.class, formatHint="bits")
    public static final String PROP_SENSOR_STATES   = PROP_PREFIX + "sensors.state";

    /**
     * Construct new instance. Don't call this directly.
     */
    public BinarySensors(DeviceContextBase deviceContext) {
        super(deviceContext);
    }

    /** @hide */
    public @Type int getType() {
        return HomeDevice.Type.SENSOR;
    }

    /**
     * Whether if a {@link Sensor} has been enabled or not.
     * @return {@code true} if the sensor is enabled.
     */
    public boolean isEnabled(@Sensor long sensor) {
        return (getProperty(PROP_ENABLED_SENSORS, Long.class) & sensor) == sensor;
    }

    /**
     * Enable or disable specific sensors.
     * @param sensor Bits for {@link Sensor}(s).
     * @param enabled True if {@param sensor} should be enabled.
     */
    public void setEnabled(@Sensor long sensor, boolean enabled) {
        final long cur = (Long) getStagedProperty(PROP_ENABLED_SENSORS, Long.class);
        final long set = (enabled) ? (cur | sensor) : (cur & ~sensor);
        setProperty(PROP_ENABLED_SENSORS, Long.class, set);
    }

    /**
     * Returns true if sensor is in detecting.
     * @param sensor One of {@link Sensor}.
     * @return {@code true} if the sensor is the state of detecting.
     */
    public boolean isDetecting(@Sensor long sensor) {
        return (getProperty(PROP_SENSOR_STATES, Long.class) & sensor) == sensor;
    }
}
