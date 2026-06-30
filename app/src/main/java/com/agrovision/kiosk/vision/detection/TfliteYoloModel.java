package com.agrovision.kiosk.vision.detection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TfliteYoloModel implements YoloModel, AutoCloseable {

    private static final String DEFAULT_MODEL_PATH = "models/best_float32.tflite";

    private final Interpreter interpreter;
    private NnApiDelegate nnApiDelegate = null;

    private final int inputWidth;
    private final int inputHeight;

    private final int numBoxes;
    private final int valuesPerBox;

    private final boolean isTransposedOutput;

    private final ByteBuffer inputBuffer;
    private final float[][][] outputBuffer;
    private final int[] pixelBuffer;

    public TfliteYoloModel(@NonNull Context context) {
        this(context, DEFAULT_MODEL_PATH);
    }

    public TfliteYoloModel(@NonNull Context context, @NonNull String assetPath) {

        try {
            MappedByteBuffer model = loadModel(context.getApplicationContext(), assetPath);

            Interpreter.Options options = new Interpreter.Options();
            // 🚀 SPEED FIX: Use 4 CPU threads + XNNPACK + NNAPI for high-speed inference
            options.setNumThreads(4);
            options.setUseXNNPACK(true);
            options.setUseNNAPI(true); // Enable Hardware Acceleration for Kiosk CPU/GPU

            interpreter = new Interpreter(model, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            inputHeight = inputShape[1];
            inputWidth = inputShape[2];

            // 🚀 STEP 5: PRE-ALLOCATE BUFFERS (AVOID RE-ALLOCATION)
            inputBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3)
                    .order(ByteOrder.nativeOrder());

            pixelBuffer = new int[inputWidth * inputHeight];

            int[] outputShape = interpreter.getOutputTensor(0).shape();

            if (outputShape[1] > outputShape[2]) {
                numBoxes = outputShape[1];
                valuesPerBox = outputShape[2];
                isTransposedOutput = false;
            } else {
                valuesPerBox = outputShape[1];
                numBoxes = outputShape[2];
                isTransposedOutput = true;
            }

            outputBuffer = new float[1][valuesPerBox][numBoxes];

        } catch (Exception e) {
            throw new IllegalStateException("YOLO init failed", e);
        }
    }

    @Override
    public List<RawDetection> runInference(Bitmap bitmap) {

        if (bitmap == null) return Collections.emptyList();

        try {
            long start = System.currentTimeMillis();
            
            // 🚀 STEP 1: RESIZE TO MODEL INPUT SIZE
            preprocess(bitmap);
            
            long prep = System.currentTimeMillis();
            
            interpreter.run(inputBuffer, outputBuffer);

            long infer = System.currentTimeMillis();
            
            List<RawDetection> raw = parseOutput(bitmap.getWidth(), bitmap.getHeight());
            
            Log.d("YOLO_PERF", String.format("Prep: %dms, Infer: %dms", (prep - start), (infer - prep)));

            return applyNms(raw, 0.45f);

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /* ================= PREPROCESS ================= */

    private void preprocess(Bitmap bitmap) {
        // 🚀 OPTIMIZATION: Use scaled pixels directly if possible
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, false);
        
        resized.getPixels(pixelBuffer, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        inputBuffer.rewind();
        // 🚀 SPEED UP: Avoid division in the loop, use multiplication
        float normalizer = 1.0f / 255.0f;
        for (int p : pixelBuffer) {
            inputBuffer.putFloat(((p >> 16) & 0xFF) * normalizer);
            inputBuffer.putFloat(((p >> 8) & 0xFF) * normalizer);
            inputBuffer.putFloat((p & 0xFF) * normalizer);
        }

        if (resized != bitmap) {
            resized.recycle();
        }
    }

    /* ================= OUTPUT PARSING ================= */

    private List<RawDetection> parseOutput(int srcW, int srcH) {

        List<RawDetection> detections = new ArrayList<>();

        for (int i = 0; i < numBoxes; i++) {
            // 🚀 CLASS-AGNOSTIC FIX: Find the highest confidence across all class indices
            // Coordinates are typically indices 0,1,2,3. Classes start at index 4.
            float maxConf = 0f;
            int bestClass = -1;

            for (int c = 4; c < valuesPerBox; c++) {
                float conf = isTransposedOutput ? outputBuffer[0][c][i] : outputBuffer[0][i][c];
                if (conf > maxConf) {
                    maxConf = conf;
                    bestClass = c - 4;
                }
            }

            // 🚀 SENSITIVITY: Lowered to 0.10 for debugging to ensure we catch something
            if (maxConf <= 0.10f) continue;

            float cx = get(0, i, 0);
            float cy = get(0, i, 1);
            float w  = get(0, i, 2);
            float h  = get(0, i, 3);

            // 🚀 ALWAYS RETURN NORMALIZED COORDINATES [0, 1]
            // This prevents ambiguous scaling issues downstream.
            float normCx = (cx > 1.1f) ? cx / inputWidth : cx;
            float normCy = (cy > 1.1f) ? cy / inputHeight : cy;
            float normW  = (w > 1.1f) ? w / inputWidth : w;
            float normH  = (h > 1.1f) ? h / inputHeight : h;

            float left   = normCx - normW / 2f;
            float top    = normCy - normH / 2f;
            float right  = normCx + normW / 2f;
            float bottom = normCy + normH / 2f;

            detections.add(new RawDetection(left, top, right, bottom, maxConf, bestClass));
        }

        return detections;
    }

    private float get(int b, int box, int val) {
        return isTransposedOutput
                ? outputBuffer[b][val][box]
                : outputBuffer[b][box][val];
    }

    /* ================= NMS ================= */

    private List<RawDetection> applyNms(List<RawDetection> input, float iouThreshold) {

        input.sort(Comparator.comparingDouble(RawDetection::confidence).reversed());

        List<RawDetection> result = new ArrayList<>();

        for (RawDetection candidate : input) {
            boolean keep = true;
            for (RawDetection kept : result) {
                if (iou(candidate, kept) > iouThreshold) {
                    keep = false;
                    break;
                }
            }
            if (keep) result.add(candidate);
        }
        return result;
    }

    private float iou(RawDetection a, RawDetection b) {

        RectF ra = a.toRectF();
        RectF rb = b.toRectF();

        float inter =
                Math.max(0, Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left)) *
                        Math.max(0, Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top));

        float union = ra.width() * ra.height() + rb.width() * rb.height() - inter;
        return union <= 0 ? 0 : inter / union;
    }

    /* ================= CLEANUP ================= */

    @Override
    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
        if (nnApiDelegate != null) {
            nnApiDelegate.close();
        }
    }

    private static MappedByteBuffer loadModel(Context context, String path) throws Exception {

        AssetFileDescriptor fd = context.getAssets().openFd(path);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();

        return channel.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(),
                fd.getDeclaredLength());
    }
}
