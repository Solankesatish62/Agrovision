package com.agrovision.kiosk.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.agrovision.kiosk.camera.ScanMode;

import java.util.ArrayList;
import java.util.List;

/**
 * BoundingBoxOverlay
 *
 * Custom view to draw bounding boxes and ROI guide overlays over the camera preview.
 * Also draws dynamic phase headings above the Center ROI box.
 */
public class BoundingBoxOverlay extends View {

    private ScanMode scanMode = ScanMode.CENTER_ROI;
    private ScanPhase scanPhase = ScanPhase.SCANNING;
    private List<RectF> boxes = new ArrayList<>();

    private final Paint boxPaint;
    private final Paint dotPaint;
    private final Paint roiGuidePaint;
    private final Paint roiCornerPaint;
    private final Paint roiDimPaint;

    // Heading paints above Center ROI
    private final Paint headerBgPaint;
    private final Paint headerBorderPaint;
    private final Paint headerTextMarathiPaint;
    private final Paint headerTextEnglishPaint;
    private final Paint headerDotPaint;

    private final RectF headerBgRect = new RectF();

    // Center ROI relative dimensions (60% width x 60% height)
    private static final float ROI_WIDTH_PERCENT = 0.60f;
    private static final float ROI_HEIGHT_PERCENT = 0.60f;

    public BoundingBoxOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.RED);
        dotPaint.setStyle(Paint.Style.FILL);

        roiGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        roiGuidePaint.setColor(Color.parseColor("#4CAF50")); // Green guide
        roiGuidePaint.setStyle(Paint.Style.STROKE);
        roiGuidePaint.setStrokeWidth(6f);

        roiCornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        roiCornerPaint.setColor(Color.parseColor("#00FF88")); // Bright accent corners
        roiCornerPaint.setStyle(Paint.Style.STROKE);
        roiCornerPaint.setStrokeWidth(12f);
        roiCornerPaint.setStrokeCap(Paint.Cap.ROUND);

        roiDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        roiDimPaint.setColor(Color.parseColor("#30000000")); // Subtle dimming outside ROI
        roiDimPaint.setStyle(Paint.Style.FILL);

        // Header Paints
        headerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBgPaint.setColor(Color.parseColor("#D0000000")); // Dark semi-transparent capsule
        headerBgPaint.setStyle(Paint.Style.FILL);

        headerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBorderPaint.setColor(Color.parseColor("#22C55E")); // Green border
        headerBorderPaint.setStyle(Paint.Style.STROKE);
        headerBorderPaint.setStrokeWidth(3.5f * density);

        headerTextMarathiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerTextMarathiPaint.setColor(Color.WHITE);
        headerTextMarathiPaint.setTextSize(16f * density);
        headerTextMarathiPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerTextMarathiPaint.setTextAlign(Paint.Align.CENTER);

        headerTextEnglishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerTextEnglishPaint.setColor(Color.parseColor("#00FF88")); // Accent mint color
        headerTextEnglishPaint.setTextSize(11f * density);
        headerTextEnglishPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerTextEnglishPaint.setTextAlign(Paint.Align.CENTER);

        headerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerDotPaint.setColor(Color.parseColor("#00FF88"));
        headerDotPaint.setStyle(Paint.Style.FILL);
    }

    public void setScanMode(ScanMode scanMode) {
        this.scanMode = scanMode;
        postInvalidate();
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public void setScanPhase(ScanPhase scanPhase) {
        if (scanPhase != null && this.scanPhase != scanPhase) {
            this.scanPhase = scanPhase;
            postInvalidate();
        }
    }

    public ScanPhase getScanPhase() {
        return scanPhase;
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
    protected void onDraw(@NonNull Canvas canvas) {
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

        // 4. Draw Dynamic Heading Badge Above Center ROI
        drawDynamicPhaseHeading(canvas, roi);
    }

    private void drawDynamicPhaseHeading(Canvas canvas, RectF roi) {
        if (scanPhase == null) return;

        float density = getResources().getDisplayMetrics().density;
        float centerX = roi.centerX();

        float bannerWidth = Math.min(roi.width() * 0.95f, 280f * density);
        float bannerHeight = 52f * density;
        float paddingBottom = 12f * density;

        float bottom = roi.top - paddingBottom;
        float top = bottom - bannerHeight;

        // Ensure header stays within top of view
        if (top < 10f * density) {
            top = 10f * density;
            bottom = top + bannerHeight;
        }

        float left = centerX - bannerWidth / 2f;
        float right = centerX + bannerWidth / 2f;

        headerBgRect.set(left, top, right, bottom);
        float cornerRadius = bannerHeight / 2f;

        // Draw pill background & border
        canvas.drawRoundRect(headerBgRect, cornerRadius, cornerRadius, headerBgPaint);
        canvas.drawRoundRect(headerBgRect, cornerRadius, cornerRadius, headerBorderPaint);

        // Draw active phase dot
        float dotRadius = 5f * density;
        float dotX = left + 18f * density;
        float dotY = top + bannerHeight / 2f;
        canvas.drawCircle(dotX, dotY, dotRadius, headerDotPaint);

        // Draw Marathi & English Text
        float textX = centerX + 6f * density; // Offset slightly for balance with left dot
        float marathiY = top + 22f * density;
        float englishY = top + 41f * density;

        canvas.drawText(scanPhase.getMarathiText(), textX, marathiY, headerTextMarathiPaint);
        canvas.drawText(scanPhase.getEnglishSubtitle(), textX, englishY, headerTextEnglishPaint);
    }

    private void drawLegacyYoloBoxes(Canvas canvas) {
        for (RectF box : boxes) {
            canvas.drawRect(box, boxPaint);
            canvas.drawCircle(box.centerX(), box.centerY(), 10f, dotPaint);
        }
    }
}
