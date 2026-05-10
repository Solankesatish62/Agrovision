package com.agrovision.kiosk.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * BoundingBoxOverlay
 *
 * Custom view to draw bounding boxes over the camera preview.
 */
public class BoundingBoxOverlay extends View {

    private List<RectF> boxes = new ArrayList<>();
    private final Paint boxPaint;
    private final Paint dotPaint;

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
    }

    /**
     * Updates the boxes to be drawn.
     * Coordinates should be already mapped to this view's dimensions.
     */
    public void setBoxes(List<RectF> boxes) {
        this.boxes = boxes != null ? boxes : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (RectF box : boxes) {
            canvas.drawRect(box, boxPaint);
            
            // 🚀 DEBUG: Draw a small red dot in the center to verify alignment
            canvas.drawCircle(box.centerX(), box.centerY(), 10f, dotPaint);
        }
    }
}
