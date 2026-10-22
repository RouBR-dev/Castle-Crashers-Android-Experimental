package com.castlecrashers.recomp;

import android.os.Bundle;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CastleActivity extends SDLActivity {
    private static final String TAG = "CastleActivity";
    private static final Object LOG_LOCK = new Object();
    private static File dataRoot;
    private static File logFile;
    private static File crashFile;
    private static volatile boolean loggingReady;
    private File gameRoot;
    private Thread.UncaughtExceptionHandler previousExceptionHandler;

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "rexruntime", "castle" };
    }

    @Override
    protected void onCreate(Bundle state) {
        dataRoot = getFilesDir();
        gameRoot = new File(dataRoot, "game");
        CastleApplication.log("ACTIVITY_CREATE reached; files=" + dataRoot.getAbsolutePath());
        initializeFileLogging();
        installCrashHandler();
        startLogcatCapture();
        log("onCreate begin; package=" + getPackageName() + "; sdk=" + android.os.Build.VERSION.SDK_INT
                + "; abi=" + android.os.Build.SUPPORTED_ABIS[0] + "; files=" + dataRoot.getAbsolutePath());
        try {
            log("Bundled game data preparation started");
            installBundledGame();
            writeRuntimeConfig();
            log("Bundled game data ready; gameRoot=" + gameRoot.getAbsolutePath()
                    + "; files=" + countFiles(gameRoot) + "; bytes=" + directoryBytes(gameRoot));
        } catch (Throwable e) {
            logThrowable("FATAL: game data preparation failed", e);
            throw new RuntimeException("Castle Crashers data preparation failed; see files/logs/app.log", e);
        }
        super.onCreate(state);
        log("onCreate completed");
    }

    @Override
    public void loadLibraries() {
        log("loadLibraries begin");
        redirectNativeOutput();
        super.loadLibraries();
        String files = dataRoot != null ? dataRoot.getAbsolutePath() : getFilesDir().getAbsolutePath();
        nativeSetenv("HOME", files);
        nativeSetenv("XDG_DATA_HOME", files);
        nativeSetenv("CASTLE_GAME_DIR", new File(files, "game").getAbsolutePath());
        android.content.SharedPreferences prefs = getSharedPreferences("castle_boot_options", MODE_PRIVATE);
        boolean useTurnip = prefs.getInt("driverMode", 0) == 1;
        String driverDir = prefs.getString("turnipDir", "");
        String driverName = prefs.getString("turnipName", "");
        File driverFile = driverDir.isEmpty() || driverName.isEmpty() ? null : new File(driverDir, driverName);
        if (useTurnip && driverFile != null && driverFile.isFile()) {
            String driverDirWithSeparator = driverDir.endsWith(File.separator) ? driverDir : driverDir + File.separator;
            nativeSetenv("SKATE3_VULKAN_DRIVER_DIR", driverDirWithSeparator);
            nativeSetenv("SKATE3_VULKAN_DRIVER_NAME", driverName);
            nativeSetenv("SKATE3_NATIVE_LIBRARY_DIR", getApplicationInfo().nativeLibraryDir);
            nativeSetenv("SKATE3_INTERNAL_FILES_DIR", files);
            log("Custom Vulkan driver selected: " + driverFile.getAbsolutePath()
                    + "; hooks=" + getApplicationInfo().nativeLibraryDir);
        } else {
            nativeSetenv("SKATE3_VULKAN_DRIVER_DIR", "");
            nativeSetenv("SKATE3_VULKAN_DRIVER_NAME", "");
            nativeSetenv("SKATE3_NATIVE_LIBRARY_DIR", "");
            nativeSetenv("SKATE3_INTERNAL_FILES_DIR", "");
            log("Using Android system Vulkan driver");
        }
        log("loadLibraries completed; HOME=" + files + "; game=" + new File(files, "game").getAbsolutePath());
    }

    @Override
    protected void onResume() {
        log("onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        log("onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        super.onDestroy();
    }

    private void initializeFileLogging() {
        File logs = new File(dataRoot, "logs");
        if (!logs.isDirectory() && !logs.mkdirs() && !logs.isDirectory()) {
            Log.e(TAG, "Cannot create persistent log directory: " + logs);
            return;
        }
        logFile = new File(logs, "app.log");
        crashFile = new File(logs, "crash.log");
        loggingReady = true;
        log("========== process start ==========");
        log("Log files: " + logFile.getAbsolutePath() + " and " + crashFile.getAbsolutePath());
    }

    private void startLogcatCapture() {
        try {
            File outputDir = getExternalFilesDir("logs");
            if (outputDir == null) outputDir = new File(dataRoot, "logs");
            if (!outputDir.isDirectory() && !outputDir.mkdirs() && !outputDir.isDirectory()) {
                throw new IOException("Cannot create logcat output directory " + outputDir);
            }
            File output = new File(outputDir, "logcat.log");
            Process process = new ProcessBuilder("logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(output)).start();
            log("Logcat capture started; pid=" + android.os.Process.myPid() + "; file=" + output.getAbsolutePath());
            Thread watcher = new Thread(() -> {
                try {
                    int result = process.waitFor();
                    log("Logcat capture process ended; exit=" + result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logThrowable("Logcat capture watcher interrupted", e);
                }
            }, "CastleLogcatWatcher");
            watcher.setDaemon(true);
            watcher.start();
        } catch (IOException e) {
            logThrowable("Could not start logcat capture", e);
        }
    }

    private void installCrashHandler() {
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            logThrowable("UNCAUGHT JAVA EXCEPTION on thread " + thread.getName(), error);
            appendCrashReport(thread, error);
            if (previousExceptionHandler != null) {
                previousExceptionHandler.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    private static void log(String message) {
        String line = timestamp() + " I/" + TAG + ": " + message;
        Log.i(TAG, message);
        appendLine(logFile, line);
    }

    private static void logThrowable(String message, Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String report = timestamp() + " E/" + TAG + ": " + message + "\n" + sw;
        Log.e(TAG, message, error);
        appendLine(logFile, report);
    }

    private static void appendCrashReport(Thread thread, Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        appendLine(crashFile, timestamp() + " FATAL thread=" + thread.getName() + "\n" + sw);
    }

    private static void appendLine(File file, String text) {
        if (!loggingReady || file == null) return;
        synchronized (LOG_LOCK) {
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write " + file, e);
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private void redirectNativeOutput() {
        try {
            File outputDir = getExternalFilesDir("logs");
            if (outputDir == null) outputDir = new File(dataRoot, "logs");
            File nativeLog = new File(outputDir, "native.log");
            File parent = nativeLog.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Cannot create " + parent);
            }
            FileDescriptor fd = Os.open(nativeLog.getAbsolutePath(),
                    OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_APPEND, 0600);
            try {
                Os.dup2(fd, 1);
                Os.dup2(fd, 2);
            } finally {
                Os.close(fd);
            }
            log("Native stdout/stderr redirected to " + nativeLog.getAbsolutePath());
        } catch (ErrnoException | IOException e) {
            logThrowable("Could not redirect native stdout/stderr", e);
        }
    }

    private void installBundledGame() throws IOException {
        File marker = new File(gameRoot, ".assets_installed_v1");
        File previousMarker = new File(gameRoot, ".assets_installed");
        if ((marker.isFile() || previousMarker.isFile()) && new File(gameRoot, "default.xex").isFile()) {
            log("Bundled data already installed; marker=" + (marker.isFile() ? marker : previousMarker));
            return;
        }
        long freeBytes = new StatFs(dataRoot.getAbsolutePath()).getAvailableBytes();
        log("Asset marker absent/incomplete; available bytes=" + freeBytes + "; extracting APK assets");
        if (freeBytes < 150L * 1024L * 1024L) {
            throw new IOException("Not enough free internal storage for game data: " + freeBytes + " bytes available");
        }
        long startedAt = android.os.SystemClock.elapsedRealtime();
        if (gameRoot.exists()) deleteRecursively(gameRoot);
        if (!gameRoot.mkdirs() && !gameRoot.isDirectory()) {
            throw new IOException("Could not create game directory " + gameRoot);
        }
        copyAssetTree("game_data", gameRoot);
        File xex = new File(gameRoot, "default.xex");
        if (!xex.isFile() || xex.length() == 0) throw new IOException("Required default.xex missing or empty at " + xex);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write("castle-crashers-assets-v1".getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        log("Asset extraction finished in " + (android.os.SystemClock.elapsedRealtime() - startedAt) + " ms");
    }

    private void copyAssetTree(String assetPath, File dest) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            if (!dest.exists() && !dest.mkdirs()) throw new IOException("Could not create directory " + dest);
            for (String child : children) copyAssetTree(assetPath + "/" + child, new File(dest, child));
            return;
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create directory " + parent);
        try (InputStream in = getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[256 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } catch (IOException e) {
            throw new IOException("Failed copying APK asset " + assetPath + " to " + dest, e);
        }
    }

    private void writeRuntimeConfig() throws IOException {
        File config = new File(dataRoot, "castle.toml");
        android.content.SharedPreferences prefs = getSharedPreferences("castle_boot_options", MODE_PRIVATE);
        int gpuChoice = prefs.getInt("gpu", 0);
        int resolutionChoice = prefs.getInt("resolution", 0);
        int presentChoice = prefs.getInt("present", 0);
        int framesChoice = prefs.getInt("frames", 0);
        String resolution = resolutionChoice == 0 ? "720p" : resolutionChoice == 1 ? "1080p" : "";
        int gpuDevice = gpuChoice == 1 ? 0 : -1;
        boolean automaticPresent = presentChoice == 1;
        int framesInFlight = framesChoice == 0 ? 1 : 2;
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(config), StandardCharsets.UTF_8)) {
            writer.write("game_data_root = \"" + gameRoot.getAbsolutePath() + "\"\n");
            writer.write("user_data_root = \"" + new File(dataRoot, "user").getAbsolutePath() + "\"\n");
            writer.write("resolution = \"" + resolution + "\"\n");
            writer.write("vulkan_device = " + gpuDevice + "\n");
            writer.write("vulkan_allow_present_mode_immediate = " + automaticPresent + "\n");
            writer.write("vulkan_allow_present_mode_mailbox = " + automaticPresent + "\n");
            writer.write("vulkan_allow_present_mode_fifo_relaxed = " + automaticPresent + "\n");
            writer.write("vulkan_max_frames_in_flight = " + framesInFlight + "\n");
        }
        log("Runtime config written: " + config.getAbsolutePath() + "; resolution=" + resolution
                + "; gpu=" + gpuDevice + "; autoPresent=" + automaticPresent + "; frames=" + framesInFlight);
    }

    private static long countFiles(File file) {
        if (file.isFile()) return 1;
        long count = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) count += countFiles(child);
        return count;
    }

    private static long directoryBytes(File file) {
        if (file.isFile()) return file.length();
        long bytes = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) bytes += directoryBytes(child);
        return bytes;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (!file.delete() && file.exists()) Log.w(TAG, "Could not delete old file " + file);
    }
}
