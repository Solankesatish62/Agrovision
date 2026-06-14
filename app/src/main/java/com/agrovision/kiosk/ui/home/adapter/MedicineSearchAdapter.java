package com.agrovision.kiosk.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.agrovision.kiosk.R;
import java.util.ArrayList;
import java.util.List;

public class MedicineSearchAdapter extends RecyclerView.Adapter<MedicineSearchAdapter.ViewHolder> {
    private List<String> items = new ArrayList<>();

    public void setItems(List<String> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medicine_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvName.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvMedicineName);
        }
    }
}
