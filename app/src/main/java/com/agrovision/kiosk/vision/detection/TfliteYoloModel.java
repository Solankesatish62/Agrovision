package com.agrovision.kiosk.vision.detection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;

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
            options.setNumThreads(4);

            // 🚀 STEP 3: ENABLE HARDWARE ACCELERATION
            try {
                nnApiDelegate = new NnApiDelegate();
                options.addDelegate(nnApiDelegate);
            } catch (Exception e) {
                // Fallback to CPU if NNAPI fails
                options.setUseNNAPI(true);
            }

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
            // 🚀 STEP 1: RESIZE TO MODEL INPUT SIZE
            preprocess(bitmap);
            
            interpreter.run(inputBuffer, outputBuffer);

            List<RawDetection> raw = parseOutput(bitmap.getWidth(), bitmap.getHeight());

            return applyNms(raw, 0.45f);

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /* ================= PREPROCESS ================= */

    private void preprocess(Bitmap bitmap) {
        // 🚀 STEP 1: REDUCE INPUT IMAGE SIZE (Bitmap.createScaledBitmap)
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
        
        resized.getPixels(pixelBuffer, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        inputBuffer.rewind();
        for (int p : pixelBuffer) {
            inputBuffer.putFloat(((p >> 16) & 0xFF) / 255f);
            inputBuffer.putFloat(((p >> 8) & 0xFF) / 255f);
            inputBuffer.putFloat((p & 0xFF) / 255f);
        }

        // Clean up temporary resized bitmap
        if (resized != bitmap) {
            resized.recycle();
        }
    }

    /* ================= OUTPUT PARSING ================= */

    private List<RawDetection> parseOutput(int srcW, int srcH) {

        List<RawDetection> detections = new ArrayList<>();

        for (int i = 0; i < numBoxes; i++) {

            float conf = isTransposedOutput
                    ? outputBuffer[0][4][i]
                    : outputBuffer[0][i][4];

            if (conf <= 0.25f) continue;

            float cx = get(0, i, 0);
            float cy = get(0, i, 1);
            float w  = get(0, i, 2);
            float h  = get(0, i, 3);

            float xMultiplier = (cx <= 1.1f && w <= 1.1f) ? srcW : (float) srcW / inputWidth;
            float yMultiplier = (cy <= 1.1f && h <= 1.1f) ? srcH : (float) srcH / inputHeight;

            float left = (cx - w / 2f) * xMultiplier;
            float top = (cy - h / 2f) * yMultiplier;
            float right = (cx + w / 2f) * xMultiplier;
            float bottom = (cy + h / 2f) * yMultiplier;

            detections.add(new RawDetection(left, top, right, bottom, conf, 0));
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
