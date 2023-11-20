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

package kr.or.kashi.hde.base;

import android.util.Log;

import java.lang.StringBuilder;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

/** @hide */
public class PropertyInflater {
    private static final String TAG = PropertyInflater.class.getSimpleName();

    public static Map<String, PropertyValue> inflate(String className) {
        try {
            return inflate(Class.forName(className));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new TreeMap<String, PropertyValue>();
    }

    public static Map<String, PropertyValue> inflate(Class<?> clazz) {
        return inflate(clazz, new TreeMap<String, PropertyValue>());
    }

    public static Map<String, PropertyValue> inflate(Class<?> clazz, Map<String, PropertyValue> outDefaultProps) {
        parseAnnotationFields(clazz, outDefaultProps);

        Class<?> parent = clazz.getSuperclass();
        if (parent != null) {
            return inflate(parent, outDefaultProps);
        }

        return outDefaultProps;
    }

    /** @hide */
    public static void parseAnnotationFields(Class<?> clazz, Map<String, PropertyValue> outDefaultProps) {
        final Field fields[] = clazz.getDeclaredFields();
        for (int i=0; i<fields.length; i++) {
            PropertyDef propAnno = fields[i].getAnnotation(PropertyDef.class);
            if (propAnno == null) continue;
            if (!String.class.equals(fields[i].getType())) continue;

            final String propName;
            try {
                propName = (String) fields[i].get(null);
            } catch (IllegalAccessException e) {
                continue;
            }

            Class<?> valueClass = propAnno.valueClass();

            // If value class is not a primitive type, try to guess real type.
            if (valueClass.isInterface() && valueClass.isAnnotation()) {
                Field[] valueClassFields = valueClass.getDeclaredFields();
                if (valueClassFields.length > 0) {
                    // Changes the class type of value to parse default value as same as type of field.
                    valueClass = valueClassFields[0].getType();
                }
            }

            final String defValue = propAnno.defValue();

            if (String.class.equals(valueClass)) {
                String defvs = (!defValue.isEmpty()) ? defValue : propAnno.defValueS();
                outDefaultProps.put(propName, new PropertyValue<String>(propName, defvs));
            } else if (Integer.class.equals(valueClass) || int.class.equals(valueClass)) {
                final int defvi = (!defValue.isEmpty()) ? Integer.parseInt(defValue) : propAnno.defValueI();
                outDefaultProps.put(propName, new PropertyValue<Integer>(propName, defvi));
            } else if (Long.class.equals(valueClass) || long.class.equals(valueClass)) {
                final long defvl = (!defValue.isEmpty()) ? Long.parseLong(defValue) : propAnno.defValueL();
                outDefaultProps.put(propName, new PropertyValue<Long>(propName, defvl));
            } else if (Boolean.class.equals(valueClass) || boolean.class.equals(valueClass)) {
                final boolean defvb = (!defValue.isEmpty()) ? Boolean.parseBoolean(defValue) : propAnno.defValueB();
                outDefaultProps.put(propName, new PropertyValue<Boolean>(propName, defvb));
            } else if (Float.class.equals(valueClass) || float.class.equals(valueClass)) {
                final float defvf = (!defValue.isEmpty()) ? Float.parseFloat(defValue) : propAnno.defValueF();
                outDefaultProps.put(propName, new PropertyValue<Float>(propName, defvf));
            } else if (Double.class.equals(valueClass) || double.class.equals(valueClass)) {
                final double defvd = (!defValue.isEmpty()) ? Double.parseDouble(defValue) : propAnno.defValueD();
                outDefaultProps.put(propName, new PropertyValue<Double>(propName, defvd));
            } else {
                Log.w(TAG, "no conversion case for the value class " + valueClass + " of '" + propName + "'");
            }
        }
    }
}
