package com.agrovision.kiosk.ui.result.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.agrovision.kiosk.R;

import java.util.Collections;
import java.util.List;

/**
 * ResultImageAdapter
 *
 * PURPOSE:
 * - Render visual representations of the medicine (bottles, disease effects).
 * - Decoupled from data fetching logic.
 */
public final class ResultImageAdapter
        extends RecyclerView.Adapter<ResultImageAdapter.VH> {

    private final List<Integer> imageResIds;
    // later this can be List<String> (URLs) for remote images

    public ResultImageAdapter(@NonNull List<Integer> imageResIds) {
        this.imageResIds = imageResIds != null ? imageResIds : Collections.emptyList();
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
        h.image.setImageResource(imageResIds.get(position));
    }

    @Override
    public int getItemCount() {
        return imageResIds == null ? 0 : imageResIds.size();

}

    static final class VH extends RecyclerView.ViewHolder {

        final ImageView image;

        VH(View v) {
            super(v);
            image = v.findViewById(R.id.imgResult);
        }
    }
}
