package com.agrovision.kiosk.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.agrovision.kiosk.camera.ScanMode;

import java.util.ArrayList;
import java.util.List;

/**
 * BoundingBoxOverlay
 *
 * Custom view to draw bounding boxes and ROI guide overlays over the camera preview.
 */
public class BoundingBoxOverlay extends View {

    private ScanMode scanMode = ScanMode.CENTER_ROI;
    private List<RectF> boxes = new ArrayList<>();

    private final Paint boxPaint;
    private final Paint dotPaint;
    private final Paint roiGuidePaint;
    private final Paint roiCornerPaint;
    private final Paint roiDimPaint;

    // Center ROI relative dimensions (60% width x 60% height)
    private static final float ROI_WIDTH_PERCENT = 0.60f;
    private static final float ROI_HEIGHT_PERCENT = 0.60f;

    public BoundingBoxOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);
        boxPaint.setAntiAlias(true);

        dotPaint = new Paint();
        dotPaint.setColor(Color.RED);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        roiGuidePaint = new Paint();
        roiGuidePaint.setColor(Color.parseColor("#4CAF50")); // Green guide
        roiGuidePaint.setStyle(Paint.Style.STROKE);
        roiGuidePaint.setStrokeWidth(6f);
        roiGuidePaint.setAntiAlias(true);

        roiCornerPaint = new Paint();
        roiCornerPaint.setColor(Color.parseColor("#00FF88")); // Bright accent corners
        roiCornerPaint.setStyle(Paint.Style.STROKE);
        roiCornerPaint.setStrokeWidth(12f);
        roiCornerPaint.setStrokeCap(Paint.Cap.ROUND);
        roiCornerPaint.setAntiAlias(true);

        roiDimPaint = new Paint();
        roiDimPaint.setColor(Color.parseColor("#30000000")); // Subtle dimming outside ROI
        roiDimPaint.setStyle(Paint.Style.FILL);
    }

    public void setScanMode(ScanMode scanMode) {
        this.scanMode = scanMode;
        postInvalidate();
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * Returns the Center ROI rectangle in View coordinates.
     */
    public RectF getCenterRoiRectInView() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return new RectF(0, 0, 0, 0);
        }

        float roiWidth = w * ROI_WIDTH_PERCENT;
        float roiHeight = h * ROI_HEIGHT_PERCENT;

        float left = (w - roiWidth) / 2f;
        float top = (h - roiHeight) / 2f;
        float right = left + roiWidth;
        float bottom = top + roiHeight;

        return new RectF(left, top, right, bottom);
    }

    /**
     * Updates the boxes to be drawn for LEGACY_YOLO mode.
     */
    public void setBoxes(List<RectF> boxes) {
        this.boxes = boxes != null ? boxes : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (scanMode == ScanMode.CENTER_ROI) {
            drawCenterRoiOverlay(canvas);
        } else {
            drawLegacyYoloBoxes(canvas);
        }
    }

    private void drawCenterRoiOverlay(Canvas canvas) {
        RectF roi = getCenterRoiRectInView();
        if (roi.width() <= 0 || roi.height() <= 0) return;

        float w = getWidth();
        float h = getHeight();

        // 1. Draw outer dimming
        canvas.drawRect(0, 0, w, roi.top, roiDimPaint);
        canvas.drawRect(0, roi.top, roi.left, roi.bottom, roiDimPaint);
        canvas.drawRect(roi.right, roi.top, w, roi.bottom, roiDimPaint);
        canvas.drawRect(0, roi.bottom, w, h, roiDimPaint);

        // 2. Draw rounded guide rectangle
        float rx = 24f;
        float ry = 24f;
        canvas.drawRoundRect(roi, rx, ry, roiGuidePaint);

        // 3. Draw corner accents
        float cornerLen = 40f;

        // Top-Left
        canvas.drawLine(roi.left, roi.top + cornerLen, roi.left, roi.top, roiCornerPaint);
        canvas.drawLine(roi.left, roi.top, roi.left + cornerLen, roi.top, roiCornerPaint);

        // Top-Right
        canvas.drawLine(roi.right - cornerLen, roi.top, roi.right, roi.top, roiCornerPaint);
        canvas.drawLine(roi.right, roi.top, roi.right, roi.top + cornerLen, roiCornerPaint);

        // Bottom-Left
        canvas.drawLine(roi.left, roi.bottom - cornerLen, roi.left, roi.bottom, roiCornerPaint);
        canvas.drawLine(roi.left, roi.bottom, roi.left + cornerLen, roi.bottom, roiCornerPaint);

        // Bottom-Right
        canvas.drawLine(roi.right - cornerLen, roi.bottom, roi.right, roi.bottom, roiCornerPaint);
        canvas.drawLine(roi.right, roi.bottom - cornerLen, roi.right, roi.bottom, roiCornerPaint);
    }

    private void drawLegacyYoloBoxes(Canvas canvas) {
        for (RectF box : boxes) {
            canvas.drawRect(box, boxPaint);
            canvas.drawCircle(box.centerX(), box.centerY(), 10f, dotPaint);
        }
    }
}
