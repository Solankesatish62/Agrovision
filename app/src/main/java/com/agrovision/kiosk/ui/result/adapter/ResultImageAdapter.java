package com.agrovision.kiosk.ui.result.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.agrovision.kiosk.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * ResultImageAdapter
 *
 * PURPOSE:
 * - Render visual representations of the medicine.
 * - Supports loading images directly from Assets.
 */
public final class ResultImageAdapter
        extends RecyclerView.Adapter<ResultImageAdapter.VH> {

    private static final String TAG = "ResultImageAdapter";
    private final List<String> imageUrls;

    public ResultImageAdapter(@NonNull List<String> imageUrls) {
        this.imageUrls = imageUrls != null ? imageUrls : Collections.emptyList();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result_image, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String url = imageUrls.get(position);
        if (url == null) return;

        // asset paths are like: file:///android_asset/images/ulala_1.jpg
        if (url.startsWith("file:///android_asset/")) {
            String assetPath = url.replace("file:///android_asset/", "");
            
            try (InputStream is = h.itemView.getContext().getAssets().open(assetPath)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    h.image.setImageBitmap(bitmap);
                } else {
                    Log.e(TAG, "Failed to decode bitmap for: " + assetPath);
                    h.image.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error loading asset image: " + assetPath, e);
                h.image.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView image;

        VH(View v) {
            super(v);
            image = v.findViewById(R.id.imgResult);
        }
    }
}
