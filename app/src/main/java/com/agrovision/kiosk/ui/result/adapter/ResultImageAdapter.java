package com.agrovision.kiosk.ui.result.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.util.PerformanceProfiler;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * ResultImageAdapter
 *
 * PURPOSE:
 * - Render visual representations of the medicine.
 * - Supports loading images from Firebase Storage with Glide caching.
 */
public final class ResultImageAdapter
        extends RecyclerView.Adapter<ResultImageAdapter.VH> {

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

        String section = "Image Loading " + (position + 1);
        PerformanceProfiler.start(section);

        Glide.with(h.itemView.getContext())
                .load(url)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        PerformanceProfiler.log(section, "Load Failed");
                        PerformanceProfiler.end(section);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        PerformanceProfiler.log(section, "Cache Hit: " + (dataSource != DataSource.REMOTE));
                        PerformanceProfiler.log(section, "Display Time");
                        PerformanceProfiler.end(section);
                        return false;
                    }
                })
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.ic_launcher_background)
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(h.image);
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
