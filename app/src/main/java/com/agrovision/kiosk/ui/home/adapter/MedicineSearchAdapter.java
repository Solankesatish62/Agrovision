package com.agrovision.kiosk.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.agrovision.kiosk.R;
import com.agrovision.kiosk.data.model.Medicine;
import java.util.ArrayList;
import java.util.List;

public class MedicineSearchAdapter extends RecyclerView.Adapter<MedicineSearchAdapter.ViewHolder> {
    private List<Medicine> items = new ArrayList<>();

    public void setItems(List<Medicine> newItems) {
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
        Medicine medicine = items.get(position);
        holder.tvName.setText(medicine.getName());
        
        if (medicine.getChemicalName() != null && !medicine.getChemicalName().isEmpty()) {
            holder.tvChemical.setText(medicine.getChemicalName());
            holder.tvChemical.setVisibility(View.VISIBLE);
        } else {
            holder.tvChemical.setVisibility(View.GONE);
        }
        
        if (medicine.getCompany() != null && !medicine.getCompany().isEmpty()) {
            holder.tvCompany.setText(medicine.getCompany());
            holder.tvCompany.setVisibility(View.VISIBLE);
        } else {
            holder.tvCompany.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvChemical;
        TextView tvCompany;
        ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvMedicineName);
            tvChemical = view.findViewById(R.id.tvChemicalName);
            tvCompany = view.findViewById(R.id.tvCompanyName);
        }
    }
}
