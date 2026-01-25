package com.agrovision.kiosk.ui.result.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.ui.result.model.ResultInfoItem;

import java.util.Collections;
import java.util.List;

/**
 * ResultInfoAdapter
 *
 * PURPOSE:
 * - Render detailed medical information in a list format.
 * - Handles different visual styles based on ResultInfoItem.Type.
 */
public final class ResultInfoAdapter
        extends RecyclerView.Adapter<ResultInfoAdapter.VH> {

    private final List<ResultInfoItem> items;

    public ResultInfoAdapter(List<ResultInfoItem> items) {
        this.items = (items != null) ? items : Collections.emptyList();
    }


    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result_info, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ResultInfoItem item = items.get(position);

        h.text.setText(item.text);

        // 🎨 Apply colors and icons based on the screenshot hierarchy
        switch (item.type) {
            case CROP:
                h.container.setBackgroundResource(R.drawable.bg_info_success); // GREEN
                break;

            case PEST:
                h.container.setBackgroundResource(R.drawable.bg_info_pest);    // BLUE
                break;

            case USAGE:
                h.container.setBackgroundResource(R.drawable.bg_info_success); // LIGHT GREEN
                break;

            case TIMING:
                h.container.setBackgroundResource(R.drawable.bg_info_timing);  // ORANGE
                break;

            case DOSAGE:
                h.container.setBackgroundResource(R.drawable.bg_search);       // GREY
                break;

            case CAUTION:
                h.container.setBackgroundResource(R.drawable.bg_info_warning); // YELLOW
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {

        final View container;
        final ImageView icon;
        final TextView text;

        VH(View v) {
            super(v);
            container = v.findViewById(R.id.container);
            icon = v.findViewById(R.id.icon);
            text = v.findViewById(R.id.text);
        }
    }
}
