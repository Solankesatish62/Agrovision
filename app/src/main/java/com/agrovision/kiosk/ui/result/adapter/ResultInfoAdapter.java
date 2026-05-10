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
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

/**
 * ResultInfoAdapter
 *
 * Renders medical information cards with specific styling from Figma.
 */
public final class ResultInfoAdapter extends RecyclerView.Adapter<ResultInfoAdapter.VH> {

    private final List<ResultInfoItem> items;
    private int highlightedPosition = -1;

    public ResultInfoAdapter(List<ResultInfoItem> items) {
        this.items = (items != null) ? items : Collections.emptyList();
    }

    public void setHighlightedPosition(int position) {
        int previous = this.highlightedPosition;
        this.highlightedPosition = position;
        if (previous != -1) notifyItemChanged(previous);
        if (position != -1) notifyItemChanged(position);
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

        // Highlight logic
        if (position == highlightedPosition) {
            h.card.setStrokeColor(h.itemView.getContext().getColor(R.color.bg_info_success));
            h.card.setStrokeWidth(4);
        } else {
            h.card.setStrokeWidth(0);
        }

        // 🎨 Design mapping from Figma (Crops=Green, Pests=Blue, Timing=Orange, etc.)
        switch (item.type) {
            case CROP:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.bg_info_success_light));
                h.icon.setImageResource(android.R.drawable.ic_menu_today); // Replace with Leaf icon
                break;
            case PEST:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.bg_info_pest_light));
                h.icon.setImageResource(android.R.drawable.ic_menu_help); // Replace with Bug icon
                break;
            case USAGE:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.bg_info_info_light));
                h.icon.setImageResource(android.R.drawable.ic_menu_manage); // Replace with Beaker icon
                break;
            case TIMING:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.bg_info_timing_light));
                h.icon.setImageResource(android.R.drawable.ic_menu_recent_history); // Replace with Clock icon
                break;
            case CAUTION:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.bg_info_warning_light));
                h.icon.setImageResource(android.R.drawable.ic_dialog_alert); // Alert icon
                break;
            default:
                h.card.setCardBackgroundColor(h.itemView.getContext().getColor(R.color.white));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView text;

        VH(View v) {
            super(v);
            card = v.findViewById(R.id.cardContainer);
            icon = v.findViewById(R.id.icon);
            text = v.findViewById(R.id.text);
        }
    }
}
