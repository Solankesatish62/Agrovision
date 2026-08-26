package com.agrovision.kiosk.data.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "advertisements")
public final class AdvertisementEntity {
    @PrimaryKey
    @NonNull
    public String adId;
    public String name;
    public String advertiserName;
    public String imageUrl;
    public String videoUrl;
    public String type; // "image" or "video"
    public long startAt;
    public long endAt;
    public int priority;
    public int duration;
    public boolean enabled;
    public long lastSyncedAt;

    public AdvertisementEntity(@NonNull String adId, String name, String advertiserName, String imageUrl, String videoUrl, String type, long startAt, long endAt, int priority, int duration, boolean enabled) {
        this.adId = adId;
        this.name = name;
        this.advertiserName = advertiserName;
        this.imageUrl = imageUrl;
        this.videoUrl = videoUrl;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
        this.priority = priority;
        this.duration = duration;
        this.enabled = enabled;
        this.lastSyncedAt = System.currentTimeMillis();
    }
}
