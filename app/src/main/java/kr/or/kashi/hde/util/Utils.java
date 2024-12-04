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

package kr.or.kashi.hde.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public final class Utils {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public static final long SECOND_MS = 1000;
    public static final long MINUTE_MS = 60 * SECOND_MS;
    public static final long HOUR_MS = 60 * MINUTE_MS;
    public static final long DAY_MS = 24 * HOUR_MS;

    public static float[] toFloatArray(List<Float> list) {
        final int size = list.size();
        final float[] array = new float[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static int[] toIntArray(List<Integer> list) {
        final int size = list.size();
        final int[] array = new int[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static byte[] toByteArray(List<Byte> list) {
        final int size = list.size();
        final byte[] array = new byte[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static String toHexString(byte b) {
        char[] buf = new char[2]; // We always want two digits.
        buf[0] = HEX_DIGITS[(b >> 4) & 0xf];
        buf[1] = HEX_DIGITS[b & 0xf];
        return new String(buf);
    }

    public static String toHexString(byte[] b) {
        return toHexString(b, b.length);
    }

    public static String toHexString(byte[] b, int length) {
        if (b == null) {
            return null;
        }
        int len = Math.min(length, b.length);
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            sb.append(toHexString(b[i]));
            if (i < len-1) sb.append(" ");
        }
        return sb.toString();
    }

    public static String toHexString(ByteBuffer byteBuffer) {
        byteBuffer.flip();
        int size = byteBuffer.remaining();
        if (size <= 0) return "";
        byte[] buffer = new byte[size];
        byteBuffer.get(buffer);
        return toHexString(buffer);
    }

    public static String formatString(double num, int dc1, int dc2) {
        final String numStr = String.format("%.0" + dc2 + "f", num);

        int limitCount = dc1 + dc2;
        if (dc2 > 0) limitCount++; // dot

        if (limitCount <= numStr.length()) {
            return numStr.substring(numStr.length()-limitCount);
        }

        StringBuilder sb = new StringBuilder();
        for (int i=0; i<(limitCount-numStr.length()); i++) {
            sb.append('0');
        }
        sb.append(numStr);
        return sb.toString();
    }

    public static float roundToNearest(float value, float multiple) {
        return Math.round(value / multiple) * multiple;
    }

    public static Point getDisplaySize(Context context) {
        Point size = new Point(0, 0);
        DisplayManager dm = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealSize(size);
        return size;
    }

    public static void changeDensity(Context context, int densityDpi) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        Configuration config = context.getResources().getConfiguration();
        displayMetrics.densityDpi = densityDpi;
        config.densityDpi = densityDpi;
        displayMetrics.setTo(displayMetrics);
        config.setTo(config);
        context.getResources().updateConfiguration(config, displayMetrics);
    }

    public static Context createContextByDensity(Context context, int densityDpi) {
        Configuration config = context.getResources().getConfiguration();
        config.densityDpi = densityDpi;
        return context.createConfigurationContext(config);
    }

    public static Context createDensityAdjustedContextByHeight(Context context, int heightPixels) {
        return Utils.createContextByDensity(context, Utils.getTargetDensityByHeight(context, heightPixels));
    }

    public static int getTargetDensityByHeight(Context context, int heightPixels) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        // e.g.) 600(heightPixels) : 1080(displayMetrics.heightPixels) = 160 : targetDendity
        return (displayMetrics.heightPixels * 160) / heightPixels;
    }

    public static void adjustDensityByHeight(Context context, int heightPixels) {
        changeDensity(context, getTargetDensityByHeight(context, heightPixels));
    }

    public static long daysToMillis(double days) {
        return (long)(days * DAY_MS);
    }

    public static double millisToDays(long ms) {
        return Math.round(((double)ms / (double)DAY_MS) * 100000.0) / 100000.0;
    }

    public static void hideKeyboard(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static boolean copyFile(File srcFile, File dstFile) {
        final byte[] buffer = new byte[16 * 1024];

        try {
            final FileInputStream fis = new FileInputStream(srcFile);
            final FileOutputStream fos = new FileOutputStream(dstFile);

            int len;
            while ((len = fis.read(buffer)) >= 0) {
                fos.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static void installUnknownPackage(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
