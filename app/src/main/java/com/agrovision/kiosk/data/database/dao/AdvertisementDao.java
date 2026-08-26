package com.agrovision.kiosk.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.agrovision.kiosk.data.database.entity.AdvertisementEntity;

import java.util.List;

@Dao
public interface AdvertisementDao {
    @Query("SELECT * FROM advertisements WHERE enabled = 1 AND startAt <= :now AND endAt >= :now ORDER BY priority DESC, startAt ASC")
    List<AdvertisementEntity> getActiveAds(long now);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AdvertisementEntity> ads);

    @Query("DELETE FROM advertisements WHERE adId NOT IN (:activeAdIds)")
    void keepOnly(List<String> activeAdIds);

    @Query("DELETE FROM advertisements")
    void deleteAll();
}
