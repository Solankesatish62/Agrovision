package com.agrovision.kiosk.data.model;

import java.util.Objects;

public final class Advertisement {
    private final String adId;
    private final String name;
    private final String advertiserName;
    private final String imageUrl;
    private final long startAt;
    private final long endAt;
    private final int priority;
    private final boolean enabled;

    public Advertisement(String adId, String name, String advertiserName, String imageUrl, long startAt, long endAt, int priority, boolean enabled) {
        this.adId = adId;
        this.name = name;
        this.advertiserName = advertiserName;
        this.imageUrl = imageUrl;
        this.startAt = startAt;
        this.endAt = endAt;
        this.priority = priority;
        this.enabled = enabled;
    }

    public String getAdId() { return adId; }
    public String getName() { return name; }
    public String getAdvertiserName() { return advertiserName; }
    public String getImageUrl() { return imageUrl; }
    public long getStartAt() { return startAt; }
    public long getEndAt() { return endAt; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Advertisement that = (Advertisement) o;
        return Objects.equals(adId, that.adId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adId);
    }
}
