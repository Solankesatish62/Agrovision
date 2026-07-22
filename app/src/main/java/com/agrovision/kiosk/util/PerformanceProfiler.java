package com.agrovision.kiosk.util;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AgroVision Performance Profiler
 * 
 * Objective: Measure every millisecond spent from camera frame to result screen.
 */
public final class PerformanceProfiler {
    private static final String TAG = "AgroVisionProfiler";
    public static final boolean ENABLE_PROFILING = true;

    private static final Map<String, Long> startTimes = new LinkedHashMap<>();
    private static final Map<String, Long> durations = new LinkedHashMap<>();
    private static final AtomicInteger scanIdGenerator = new AtomicInteger(0);
    private static int currentScanId = -1;
    private static boolean isLocked = false;

    // Thresholds for warnings
    private static final Map<String, Long> TARGETS = new HashMap<>();

    private static final Map<String, String> logs = new LinkedHashMap<>();

    static {
        TARGETS.put("Camera Frame", 16L);
        TARGETS.put("YUV -> RGB", 100L);
        TARGETS.put("Rotate Bitmap", 50L);
        TARGETS.put("Crop Bitmap", 30L);
        TARGETS.put("Resize Bitmap", 100L);
        TARGETS.put("Pixel Normalization", 100L);
        TARGETS.put("Tensor Copy", 50L);
        TARGETS.put("Interpreter.run()", 500L);
        TARGETS.put("Output Tensor Read", 50L);
        TARGETS.put("Decode Predictions", 100L);
        TARGETS.put("NMS", 50L);
        TARGETS.put("YOLO", 800L);
        TARGETS.put("Overlay Draw", 30L);
        TARGETS.put("Bounding Stability", 500L);
        TARGETS.put("OCR", 1000L);
        TARGETS.put("Medicine Matching", 100L);
        TARGETS.put("TOTAL LATENCY", 4000L);
    }

    public static void startScan() {
        if (!ENABLE_PROFILING || isLocked) return;
        reset();
        currentScanId = scanIdGenerator.incrementAndGet();
        start("TOTAL LATENCY");
        Log.d(TAG, "=========================================================");
        Log.d(TAG, "SCAN #" + currentScanId + " STARTED");
    }

    public static void lock() {
        isLocked = true;
    }

    public static void unlock() {
        isLocked = false;
    }

    public static void start(String sectionName) {
        if (!ENABLE_PROFILING) return;
        startTimes.put(sectionName, SystemClock.elapsedRealtimeNanos());
        logInfo(sectionName, "START");
    }

    public static void log(String sectionName, String message) {
        if (!ENABLE_PROFILING) return;
        logs.put(sectionName + "_" + System.nanoTime(), message);
        logInfo(sectionName, message);
    }

    public static void checkpoint(String sectionName, String detail) {
        if (!ENABLE_PROFILING) return;
        long now = SystemClock.elapsedRealtimeNanos();
        Long start = startTimes.get(sectionName);
        if (start != null) {
            long elapsed = (now - start) / 1000000;
            logInfo(sectionName, "CHECKPOINT [" + detail + "]: " + elapsed + " ms");
        }
    }

    public static void end(String sectionName) {
        if (!ENABLE_PROFILING) return;
        long now = SystemClock.elapsedRealtimeNanos();
        Long start = startTimes.get(sectionName);
        if (start != null) {
            long durationMs = (now - start) / 1000000;
            durations.put(sectionName, durationMs);
            logInfo(sectionName, "END - Total: " + durationMs + " ms");
        }
    }

    private static void logInfo(String sectionName, String event) {
        String threadName = Thread.currentThread().getName();
        String mainThreadSuffix = LooperUtils.isMainThread() ? " (MAIN)" : "";
        
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMem = runtime.maxMemory() / 1024 / 1024;

        Log.d(TAG, String.format("SCAN #%d | %s | %s | Thread: %s%s | Mem: %dMB/%dMB",
                currentScanId, sectionName, event, threadName, mainThreadSuffix, usedMem, maxMem));
    }

    public static void printSummary() {
        if (!ENABLE_PROFILING) return;
        end("TOTAL LATENCY");

        Log.d(TAG, "=========================================================");
        Log.d(TAG, "YOLO MICRO PROFILE #" + currentScanId);
        
        printStage("Frame Arrival", "Camera Frame");
        printStage("YUV -> RGB", "YUV -> RGB");
        printStage("Rotate", "Rotate Bitmap");
        printStage("Crop", "Crop Bitmap");
        printStage("Resize", "Resize Bitmap");
        printStage("Normalize", "Pixel Normalization");
        printStage("Tensor Copy", "Tensor Copy");
        printStage("Interpreter.run()", "Interpreter.run()");
        printStage("Read Output", "Output Tensor Read");
        printStage("Decode Boxes", "Decode Predictions");
        printStage("NMS", "NMS");
        printStage("Overlay", "Overlay Draw");

        Log.d(TAG, "------------------------------------");
        Long total = durations.get("TOTAL LATENCY");
        Log.d(TAG, "TOTAL: " + (total != null ? total : 0) + " ms");
        Log.d(TAG, "=========================================================");

        // Analysis
        Log.d(TAG, "ANALYSIS:");
        String slowest = "";
        long maxTime = -1;
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            if ("TOTAL LATENCY".equals(entry.getKey())) continue;
            if (entry.getValue() > maxTime) {
                maxTime = entry.getValue();
                slowest = entry.getKey();
            }
        }
        Log.d(TAG, "1. Slowest stage: " + slowest + " (" + maxTime + " ms)");
        
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            Long target = TARGETS.get(entry.getKey());
            if (target != null && entry.getValue() > target) {
                Log.w(TAG, "2. Stage '" + entry.getKey() + "' exceeds target! (" + entry.getValue() + " ms > " + target + " ms)");
            }
        }
    }

    private static void printStage(String label, String sectionName) {
        Long duration = durations.get(sectionName);
        if (duration != null) {
            Log.d(TAG, String.format("%-20s %d ms", label, duration));
        } else {
            Log.d(TAG, String.format("%-20s N/A", label));
        }
    }

    public static void reset() {
        startTimes.clear();
        durations.clear();
        logs.clear();
    }

    
    private static class LooperUtils {
        static boolean isMainThread() {
            return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        }
    }
}
