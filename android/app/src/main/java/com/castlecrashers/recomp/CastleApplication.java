package com.castlecrashers.recomp;

import android.app.Application;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CastleApplication extends Application {
    private static final String TAG = "CastleBootstrap";
    private static final Object LOCK = new Object();
    private static File internalLog;
    private static File externalLog;
    private static File fatalLog;
    private static Thread.UncaughtExceptionHandler previousHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        File internalDir = new File(getFilesDir(), "logs");
        if (!internalDir.exists() && !internalDir.mkdirs() && !internalDir.isDirectory()) {
            Log.e(TAG, "Cannot create internal logs directory: " + internalDir);
        }
        internalLog = new File(internalDir, "bootstrap.log");
        fatalLog = new File(internalDir, "fatal.log");

        File externalDir = getExternalFilesDir("logs");
        if (externalDir != null) {
            if (!externalDir.exists()) externalDir.mkdirs();
            externalLog = new File(externalDir, "bootstrap.log");
        }

        log("APPLICATION_CREATE pid=" + Process.myPid() + " sdk=" + Build.VERSION.SDK_INT
                + " device=" + Build.MANUFACTURER + "/" + Build.MODEL
                + " abis=" + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
        log("INTERNAL_LOG=" + internalLog.getAbsolutePath());
        if (externalLog != null) log("EXTERNAL_LOG=" + externalLog.getAbsolutePath());
        installCrashHandler();
    }

    static void log(String message) {
        String line = timestamp() + " " + message + "\n";
        Log.i(TAG, message);
        synchronized (LOCK) {
            append(internalLog, line);
            append(externalLog, line);
        }
    }

    private void installCrashHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringBuilder text = new StringBuilder(timestamp()).append(" UNCAUGHT thread=")
                    .append(thread.getName()).append('\n');
            for (StackTraceElement element : error.getStackTrace()) text.append("  at ").append(element).append('\n');
            Throwable cause = error.getCause();
            while (cause != null) {
                text.append("Caused by: ").append(cause).append('\n');
                for (StackTraceElement element : cause.getStackTrace()) text.append("  at ").append(element).append('\n');
                cause = cause.getCause();
            }
            String report = text.toString();
            Log.e(TAG, report, error);
            synchronized (LOCK) {
                append(internalLog, report);
                append(externalLog, report);
                append(fatalLog, report);
                append(externalLog == null ? null : new File(externalLog.getParentFile(), "fatal.log"), report);
            }
            if (previousHandler != null) previousHandler.uncaughtException(thread, error);
            else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    private static void append(File file, String text) {
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            Log.e(TAG, "Could not append log to " + file, e);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }
}
