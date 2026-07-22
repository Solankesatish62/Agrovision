package com.agrovision.kiosk.vision.detection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import com.agrovision.kiosk.util.PerformanceProfiler;
import com.agrovision.kiosk.util.InferenceInvestigator;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

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

    private static final String DEFAULT_MODEL_PATH = "models/best_full_integer_quant.tflite";

    private final Interpreter interpreter;
    private NnApiDelegate nnApiDelegate = null;
    private GpuDelegate gpuDelegate = null;

    private final int inputWidth;
    private final int inputHeight;

    private final int numBoxes;
    private final int valuesPerBox;

    private final boolean isTransposedOutput;

    private final ByteBuffer inputBuffer;
    private final float[][][] outputBuffer;
    private final int[] pixelBuffer;

    private final boolean isInputQuantized;
    private final float inputScale;
    private final int inputZeroPoint;

    private final boolean isOutputQuantized;
    private final float outputScale;
    private final int outputZeroPoint;
    private final byte[][][] quantizedOutputBuffer;

    private final ImageProcessor imageProcessor;
    private final TensorImage tensorImage;

    public TfliteYoloModel(@NonNull Context context) {
        this(context, DEFAULT_MODEL_PATH);
    }

    public TfliteYoloModel(@NonNull Context context, @NonNull String assetPath) {

        try {
            MappedByteBuffer model = loadModel(context.getApplicationContext(), assetPath);

            long allocStart = System.currentTimeMillis();
            
            // 🚀 FAIL-SAFE INITIALIZATION
            Interpreter tempInterpreter = null;
            try {
                // Try GPU first
                Interpreter.Options gpuOptions = new Interpreter.Options();
                GpuDelegate.Options gOpts = new GpuDelegate.Options();
                gOpts.setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED);
                gpuDelegate = new GpuDelegate(gOpts);
                gpuOptions.addDelegate(gpuDelegate);
                
                tempInterpreter = new Interpreter(model, gpuOptions);
                Log.i("YOLO_MODEL", "GPU Acceleration enabled successfully.");
            } catch (Exception e) {
                Log.w("YOLO_MODEL", "GPU failed (No shader support?), falling back to CPU: " + e.getMessage());
                if (gpuDelegate != null) {
                    gpuDelegate.close();
                    gpuDelegate = null;
                }
                
                // Fallback to CPU + XNNPACK
                Interpreter.Options cpuOptions = new Interpreter.Options();
                cpuOptions.setNumThreads(4);
                cpuOptions.setUseXNNPACK(true);
                
                // Optional: Try NNAPI as a middle ground
                try {
                    nnApiDelegate = new NnApiDelegate();
                    cpuOptions.addDelegate(nnApiDelegate);
                } catch (Exception ex) {
                    Log.w("YOLO_MODEL", "NNAPI fallback also failed, using pure CPU.");
                }
                
                tempInterpreter = new Interpreter(model, cpuOptions);
            }

            interpreter = tempInterpreter;
            interpreter.allocateTensors();
            InferenceInvestigator.setAllocationTime(System.currentTimeMillis() - allocStart);

            // 🚀 INVESTIGATION: LOG CONFIG & HARDWARE
            InferenceInvestigator.logInterpreterConfig(4, true, true);
            InferenceInvestigator.logHardwareInfo(context);

            AssetFileDescriptor fd = context.getAssets().openFd(assetPath);
            InferenceInvestigator.logModelInfo(assetPath, fd.getDeclaredLength(), interpreter);
            fd.close();

            Tensor inputTensor = interpreter.getInputTensor(0);
            int[] inputShape = inputTensor.shape();
            inputHeight = inputShape[1];
            inputWidth = inputShape[2];
            isInputQuantized = inputTensor.dataType() != DataType.FLOAT32;
            inputScale = inputTensor.quantizationParams().getScale();
            inputZeroPoint = inputTensor.quantizationParams().getZeroPoint();

            Tensor outputTensor = interpreter.getOutputTensor(0);
            int[] outputShape = outputTensor.shape();
            isOutputQuantized = outputTensor.dataType() != DataType.FLOAT32;
            outputScale = outputTensor.quantizationParams().getScale();
            outputZeroPoint = outputTensor.quantizationParams().getZeroPoint();

            // 🚀 STEP 5: PRE-ALLOCATE BUFFERS (AVOID RE-ALLOCATION)
            int bytesPerElement = isInputQuantized ? 1 : 4;
            inputBuffer = ByteBuffer.allocateDirect(bytesPerElement * inputWidth * inputHeight * 3)
                    .order(ByteOrder.nativeOrder());

            pixelBuffer = new int[inputWidth * inputHeight];

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
            if (isOutputQuantized) {
                quantizedOutputBuffer = new byte[1][valuesPerBox][numBoxes];
            } else {
                quantizedOutputBuffer = null;
            }

            // 🚀 STRATEGIC OPTIMIZATION: Initialize ImageProcessor for fast preprocessing
            ImageProcessor.Builder builder = new ImageProcessor.Builder()
                    .add(new ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR));
            
            if (!isInputQuantized) {
                // For float models, normalize to [0, 1]
                builder.add(new NormalizeOp(0f, 255f));
                tensorImage = new TensorImage(DataType.FLOAT32);
            } else {
                // For quantized models, UINT8 is usually preferred
                tensorImage = new TensorImage(DataType.UINT8);
            }
            imageProcessor = builder.build();

            PerformanceProfiler.log("YOLO Model", "Model Name: " + assetPath);
            PerformanceProfiler.log("YOLO Model", "Input Shape: [" + inputHeight + ", " + inputWidth + "]");
            PerformanceProfiler.log("YOLO Model", "Output Shape: " + java.util.Arrays.toString(outputShape));
            PerformanceProfiler.log("YOLO Model", "Quantized: " + isInputQuantized);
            PerformanceProfiler.log("YOLO Model", "Threads: 4");
            PerformanceProfiler.log("YOLO Model", "XNNPACK: true");
            PerformanceProfiler.log("YOLO Model", "NNAPI: true");

        } catch (Exception e) {
            throw new IllegalStateException("YOLO init failed", e);
        }
    }

    @Override
    public List<RawDetection> runInference(Bitmap bitmap) {

        if (bitmap == null) return Collections.emptyList();

        try {
            // 🚀 STEP 1: PREPROCESSING (RESIZE + NORMALIZE)
            preprocess(bitmap);
            
            long start = System.currentTimeMillis();
            PerformanceProfiler.start("Interpreter.run()");
            if (isOutputQuantized) {
                interpreter.run(inputBuffer, quantizedOutputBuffer);
                // Dequantize
                for (int v = 0; v < valuesPerBox; v++) {
                    for (int n = 0; n < numBoxes; n++) {
                        outputBuffer[0][v][n] = (quantizedOutputBuffer[0][v][n] - outputZeroPoint) * outputScale;
                    }
                }
            } else {
                interpreter.run(inputBuffer, outputBuffer);
            }
            PerformanceProfiler.end("Interpreter.run()");
            long duration = System.currentTimeMillis() - start;

            // 🚀 INVESTIGATION: RECORD RUN
            InferenceInvestigator.recordInference(duration);
            
            PerformanceProfiler.start("Output Tensor Read");
            // No explicit read needed for float[][][], it's filled by run()
            PerformanceProfiler.end("Output Tensor Read");

            PerformanceProfiler.start("Decode Predictions");
            List<RawDetection> raw = parseOutput(bitmap.getWidth(), bitmap.getHeight());
            PerformanceProfiler.end("Decode Predictions");

            PerformanceProfiler.start("NMS");
            List<RawDetection> result = applyNms(raw, 0.45f);
            PerformanceProfiler.end("NMS");

            return result;

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /* ================= PREPROCESS ================= */

    private void preprocess(Bitmap bitmap) {
        PerformanceProfiler.start("Image Preprocessing");
        
        // 🚀 OPTIMIZATION: Use TFLite Support Library's ImageProcessor (Native/Optimized)
        // This replaces the manual slow Java loops for resize and normalization.
        tensorImage.load(bitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        
        inputBuffer.rewind();
        ByteBuffer processedBuffer = processedImage.getBuffer();
        processedBuffer.rewind();
        inputBuffer.put(processedBuffer);
        
        PerformanceProfiler.end("Image Preprocessing");
        PerformanceProfiler.log("Image Preprocessing", "Original: " + bitmap.getWidth() + "x" + bitmap.getHeight() + " -> " + inputWidth + "x" + inputHeight);
    }

    /* ================= OUTPUT PARSING ================= */

    private List<RawDetection> parseOutput(int srcW, int srcH) {

        List<RawDetection> detections = new ArrayList<>();
        int boxesDecoded = 0;

        for (int i = 0; i < numBoxes; i++) {
            boxesDecoded++;
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

        PerformanceProfiler.log("Decode Predictions", "Boxes Decoded: " + boxesDecoded);
        PerformanceProfiler.log("Decode Predictions", "Classes: " + (valuesPerBox - 4));

        return detections;
    }

    private float get(int b, int box, int val) {
        return isTransposedOutput
                ? outputBuffer[b][val][box]
                : outputBuffer[b][box][val];
    }

    /* ================= NMS ================= */

    private List<RawDetection> applyNms(List<RawDetection> input, float iouThreshold) {

        int boxesBefore = input.size();
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
        
        PerformanceProfiler.log("NMS", "Boxes Before: " + boxesBefore);
        PerformanceProfiler.log("NMS", "Boxes After: " + result.size());

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
        if (gpuDelegate != null) {
            gpuDelegate.close();
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
