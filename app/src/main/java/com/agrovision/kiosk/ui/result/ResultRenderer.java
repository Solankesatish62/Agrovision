package com.agrovision.kiosk.ui.result;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.agrovision.kiosk.ui.result.adapter.ResultImageAdapter;
import com.agrovision.kiosk.ui.result.adapter.ResultInfoAdapter;
import com.agrovision.kiosk.ui.result.model.ResultInfoItem;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;

import java.util.List;

/**
 * ResultRenderer
 *
 * RESPONSIBILITY:
 * - Decide which result layout to show (KNOWN vs UNKNOWN)
 * - Render ScanResult data into UI
 *
 * RULES:
 * - NO business logic
 * - NO state transitions
 * - NO networking
 * - NO timers
 */
public final class ResultRenderer {

    private static final String TAG = "ResultRenderer";

    // Layout containers
    private final View standardLayout;
    private final View unknownLayout;

    // Standard UI
    private final TextView tvMedicineName;
    private final ViewPager2 imagePager;
    private final RecyclerView infoList;

    // Unknown UI
    private final TextView tvUnknownHeader;

    public ResultRenderer(
            View standardLayout,
            View unknownLayout,
            TextView tvMedicineName,
            ViewPager2 imagePager,
            RecyclerView infoList,
            TextView tvUnknownHeader
    ) {
        this.standardLayout = standardLayout;
        this.unknownLayout = unknownLayout;
        this.tvMedicineName = tvMedicineName;
        this.imagePager = imagePager;
        this.infoList = infoList;
        this.tvUnknownHeader = tvUnknownHeader;
    }

    /**
     * Render ONE ScanResult.
     */
    public void render(ScanResult result) {

        if (result.resultType == ResultType.UNKNOWN) {
            renderUnknown(result);
        } else {
            renderKnown(result);
        }
    }

    /* =========================================================
       INTERNAL RENDERING
       ========================================================= */

    private void renderKnown(ScanResult result) {

        // Toggle layouts
        unknownLayout.setVisibility(View.GONE);
        standardLayout.setVisibility(View.VISIBLE);

        // Title
        tvMedicineName.setText(result.displayName);

        // Images
        renderImages(result.imageUrls);

        // Info cards
        renderInfoCards(result.infoItems);
    }

    private void renderUnknown(ScanResult result) {

        // Toggle layouts
        standardLayout.setVisibility(View.GONE);
        unknownLayout.setVisibility(View.VISIBLE);

        // 🚀 Rule 7: Update message for UX
        String message = String.format(
                "अपरिचित उत्पादन: %s\n\nहे औषध ओळखले गेले नाही. पुन्हा स्कॅन करण्यासाठी परत जात आहे...", 
                result.rawOcrText
        );
        
        tvUnknownHeader.setText(message);
    }

    private void renderImages(List<String> imageUrls) {
        Log.d(TAG, "Rendering images, count: " + (imageUrls != null ? imageUrls.size() : 0));

        if (imageUrls == null || imageUrls.isEmpty()) {
            imagePager.setAdapter(null);
            return;
        }

        imagePager.setAdapter(
                new ResultImageAdapter(imageUrls)
        );
    }

    private void renderInfoCards(List<ResultInfoItem> infoItems) {

        if (infoItems == null || infoItems.isEmpty()) {
            infoList.setAdapter(null);
            return;
        }

        infoList.setAdapter(
                new ResultInfoAdapter(infoItems)
        );
    }
}
