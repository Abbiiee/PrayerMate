package com.ebaa.prayermate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PrayerTimesAdapter extends RecyclerView.Adapter<PrayerTimesAdapter.PrayerViewHolder> {

    private List<PrayerTime> prayerTimes;

    public PrayerTimesAdapter(List<PrayerTime> prayerTimes) {
        this.prayerTimes = prayerTimes;
    }

    @NonNull
    @Override
    public PrayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_prayer_time, parent, false);
        return new PrayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrayerViewHolder holder, int position) {
        PrayerTime prayerTime = prayerTimes.get(position);

        holder.tvPrayerName.setText(prayerTime.getName());
        holder.tvPrayerTime.setText(prayerTime.getTime());

        // استخدام الإيموجي بدلاً من الأيقونة
        if (prayerTime.hasEmoji()) {
            // إخفاء الأيقونة وإظهار الإيموجي
            holder.ivPrayerIcon.setVisibility(View.GONE);
            holder.tvPrayerEmoji.setVisibility(View.VISIBLE);
            holder.tvPrayerEmoji.setText(prayerTime.getEmoji());
        } else {
            // استخدام الأيقونة العادية
            holder.ivPrayerIcon.setVisibility(View.VISIBLE);
            holder.tvPrayerEmoji.setVisibility(View.GONE);
            holder.ivPrayerIcon.setImageResource(prayerTime.getIconResId());
        }
    }

    @Override
    public int getItemCount() {
        return prayerTimes.size();
    }

    static class PrayerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPrayerIcon;
        TextView tvPrayerName, tvPrayerTime, tvPrayerEmoji;

        public PrayerViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPrayerIcon = itemView.findViewById(R.id.ivPrayerIcon);
            tvPrayerName = itemView.findViewById(R.id.tvPrayerName);
            tvPrayerTime = itemView.findViewById(R.id.tvPrayerTime);
            tvPrayerEmoji = itemView.findViewById(R.id.tvPrayerEmoji);
        }
    }
}