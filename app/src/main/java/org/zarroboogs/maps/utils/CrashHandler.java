package org.zarroboogs.maps.utils;

import android.content.Context;
import android.os.Environment;
import org.zarroboogs.maps.hub.NavigationHubManager;
import org.zarroboogs.maps.hub.BleTelemetryRing;
import org.zarroboogs.maps.utils.FileLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String CRASH_DIR = "MapsCrashLogs";
    private static final String CRASH_FILE_PREFIX = "crash_";
    private static final String CRASH_FILE_SUFFIX = ".log";

    private static CrashHandler sInstance;
    private Context mContext;
    private Thread.UncaughtExceptionHandler mDefaultHandler;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        if (sInstance == null) {
            synchronized (CrashHandler.class) {
                if (sInstance == null) {
                    sInstance = new CrashHandler();
                }
            }
        }
        return sInstance;
    }

    public void init(Context context) {
        mContext = context.getApplicationContext();
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        FileLogger.d(TAG, "CrashHandler initialized");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                FileLogger.e(TAG, "Error : ", e);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }

        new Thread(() -> {
            saveCrashInfoToFile(ex);
        }).start();

        return true;
    }

    private void saveCrashInfoToFile(Throwable ex) {
        try {
            File crashDir = new File(Environment.getExternalStorageDirectory(), CRASH_DIR);
            FileLogger.d(TAG, "Crash directory path: " + crashDir.getAbsolutePath());
            
            if (!crashDir.exists()) {
                boolean created = crashDir.mkdirs();
                FileLogger.d(TAG, "Created crash directory: " + created);
            }
            
            if (!crashDir.exists() || !crashDir.canWrite()) {
                FileLogger.e(TAG, "Cannot write to crash directory, trying app-specific directory");
                crashDir = new File(mContext.getExternalFilesDir(null), CRASH_DIR);
                if (!crashDir.exists()) {
                    crashDir.mkdirs();
                }
            }

            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = CRASH_FILE_PREFIX + time + CRASH_FILE_SUFFIX;
            File crashFile = new File(crashDir, fileName);
            
            FileLogger.d(TAG, "Attempting to save crash log to: " + crashFile.getAbsolutePath());

            try (PrintWriter pw = new PrintWriter(new FileWriter(crashFile))) {
                pw.println("===== Crash Log =====");
                pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                pw.println("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                pw.println("Android Version: " + android.os.Build.VERSION.RELEASE);
                pw.println("App Version: " + getAppVersionName() + " (" + getAppVersionCode() + ")");
                pw.println();

                pw.println("===== Exception =====");
                ex.printStackTrace(pw);
                pw.println();

                pw.println("===== Stack Trace =====");
                StackTraceElement[] stackTrace = ex.getStackTrace();
                for (StackTraceElement element : stackTrace) {
                    pw.println(element.toString());
                }
                pw.println();

                Throwable cause = ex.getCause();
                while (cause != null) {
                    pw.println("===== Caused By =====");
                    cause.printStackTrace(pw);
                    cause = cause.getCause();
                }

                // Dump BLE telemetry ring buffer
                try {
                    BleTelemetryRing ring = NavigationHubManager.INSTANCE.getTelemetryRing();
                    if (ring != null && ring.isNotEmpty()) {
                        pw.println();
                        pw.print(ring.dump());
                    }
                } catch (Exception ignored) {
                    pw.println();
                    pw.println("===== BLE Telemetry: (unavailable) =====");
                }

                pw.flush();
                FileLogger.d(TAG, "Crash log saved successfully to: " + crashFile.getAbsolutePath());
                FileLogger.d(TAG, "File exists: " + crashFile.exists() + ", File size: " + crashFile.length() + " bytes");

            } catch (IOException e) {
                FileLogger.e(TAG, "Failed to save crash log", e);
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "Error in saveCrashInfoToFile", e);
        }
    }

    private String getAppVersionName() {
        try {
            return mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int getAppVersionCode() {
        try {
            return mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0)
                    .versionCode;
        } catch (Exception e) {
            return 0;
        }
    }
}
