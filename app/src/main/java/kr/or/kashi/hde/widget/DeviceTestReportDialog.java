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

package kr.or.kashi.hde.widget;

import static android.os.Build.VERSION.SDK_INT;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collections;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.test.DeviceTestCallback;

public class DeviceTestReportDialog extends Dialog implements DeviceTestCallback {
    private static final String TAG = DeviceTestReportDialog.class.getSimpleName();
    private static final float DIALOG_WIDTH_RATIO = 0.9f;
    private static final float DIALOG_HEIGHT_RATIO = 0.9f;

    private final Context mContext;
    private final Handler mHandler;
    private final WindowManager mWindowManager;
    private final StorageManager mStorageManager;
    private StringBuilder mHtmlBuilder = new StringBuilder();

    private Button mSaveButton;
    private Button mCloseButton;
    private TextView mResultText;
    private WebView mReportWebView;
    private ProgressBar mReportProgress;

    public DeviceTestReportDialog(Context context) {
        super(context);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

        if (SDK_INT >= Build.VERSION_CODES.R) {
            mStorageManager.registerStorageVolumeCallback(
                    context.getMainExecutor(), new StorageManager.StorageVolumeCallback() {
                @Override public void onStateChanged(@NonNull StorageVolume volume) {
                    checkStorageState();
                }
            });
        }

        setContentView(R.layout.device_test_report);

        mSaveButton = findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(view -> onSaveHtml());
        mCloseButton = findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(view -> dismiss());
        mResultText = findViewById(R.id.result_text);
        mReportWebView = findViewById(R.id.report_webview);
        mReportWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                mReportProgress.setVisibility(ProgressBar.VISIBLE);
                return super.shouldOverrideUrlLoading(view, request);
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mReportProgress.setVisibility(ProgressBar.VISIBLE);
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                mReportProgress.setVisibility(ProgressBar.GONE);
            }
        });
        mReportProgress = findViewById(R.id.report_progress);
    }

    public void show() {
        super.show();

        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        int width = (int) (metrics.widthPixels * DIALOG_WIDTH_RATIO);
        int height = (int) (metrics.heightPixels * DIALOG_HEIGHT_RATIO);
        getWindow().setLayout(width, height);

        mReportWebView.loadData(mHtmlBuilder.toString(), "text/html; charset=utf-8", "UTF-8");
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkStorageState();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onTestRunnerStarted() {
        mHtmlBuilder = new StringBuilder();
        mHtmlBuilder.append("<html>\n");
        mHtmlBuilder.append("<body>\n");
        mHtmlBuilder.append("<table border=1 style='width:100%'>\n");
    }

    @Override
    public void onTestRunnerFinished() {
        mHtmlBuilder.append("</table>\n");
        mHtmlBuilder.append("</body>\n");
        mHtmlBuilder.append("</html>\n");
    }

    @Override
    public void onDeviceTestStarted(HomeDevice device) {
        mHtmlBuilder.append("<tr>");
        mHtmlBuilder.append("<td colspan=2>");
        mHtmlBuilder.append("<b>");
        mHtmlBuilder.append("\u0387 "); // center dot
        mHtmlBuilder.append("Device (");
        mHtmlBuilder.append(HomeDevice.typeToString(device.getType()));
        mHtmlBuilder.append(", ");
        mHtmlBuilder.append(device.getAddress());
        mHtmlBuilder.append(")");
        mHtmlBuilder.append("</b>");
        mHtmlBuilder.append("</td>");
        mHtmlBuilder.append("</tr>\n");
    }

    @Override
    public void onDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {
        mHtmlBuilder.append("<tr>");

        mHtmlBuilder.append("<td>");
        mHtmlBuilder.append("&nbsp&nbsp&nbsp&nbsp");
        mHtmlBuilder.append("- ");
        mHtmlBuilder.append("<i>" + test.getName() + "</i>");
        mHtmlBuilder.append(" ... ");
        mHtmlBuilder.append("</td>");

        mHtmlBuilder.append("<td>");
        if (result.wasSuccessful()) {
            mHtmlBuilder.append("<p style='color:blue;'>PASS</p>");
        } else  {
            if (hasUnsupportedOperationException(test, result)) {
                mHtmlBuilder.append("<p style='color:gray;'>N/A</p>");
            } else {
                mHtmlBuilder.append("<p style='color:red;'>FAIL</p>");
            }
        }
        mHtmlBuilder.append("</td>");

        mHtmlBuilder.append("</tr>\n");
    }

    @Override
    public void onDeviceTestFinished(HomeDevice device) {
    }

    private static boolean hasUnsupportedOperationException(Test test, TestResult result) {
        for (TestFailure fail: Collections.list(result.errors())) {
            if (fail.failedTest() == test) {
                if (fail.thrownException() instanceof UnsupportedOperationException) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkStorageState() {
        if (getExternalStorageDir() == null) {
            mResultText.setText("NO USB storage attached to save report!");
        } else {
            mResultText.setText("");
        }
    }

    private File getExternalStorageDir() {
        for (StorageVolume volume : mStorageManager.getStorageVolumes()) {
            if (!volume.isPrimary() && !volume.isEmulated() && volume.isRemovable()) {
                return volume.getDirectory();
            }
        }
        return null;
    }

    private void onSaveHtml() {
        File storageDir = getExternalStorageDir();
        if (storageDir == null) {
            return;
        }

        if (SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", mContext.getPackageName(), null);
                intent.setData(uri);
                mContext.startActivity(intent);
                return;
            }
        }

        File downloadDir = new File(storageDir, "Download");
        downloadDir.mkdir();

        long currentTime = SystemClock.uptimeMillis();
        String dateString = new SimpleDateFormat("yyyyMMdd_HHmmss").format(currentTime);
        String fileName = "homedevice_autotest_report_" + dateString + ".html";

        File reportFile = new File(downloadDir, fileName);

        try {
            Files.write(reportFile.toPath(), mHtmlBuilder.toString().getBytes());
        } catch (IOException e) {
            mResultText.setText("Can't write to " + reportFile.getPath());
            return;
        }

        mResultText.setText("Report has been saved to '" + reportFile.getPath() + "'");
    }
}
