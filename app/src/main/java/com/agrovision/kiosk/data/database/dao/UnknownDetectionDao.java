package com.agrovision.kiosk.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.agrovision.kiosk.data.database.entity.UnknownDetectionEntity;

import java.util.List;

@Dao
public interface UnknownDetectionDao {

    @Insert
    long insert(UnknownDetectionEntity detection);

    @Query("SELECT * FROM unknown_detections WHERE isSynced = 0")
    List<UnknownDetectionEntity> getUnsynced();

    @Update
    void update(UnknownDetectionEntity detection);

    @Query("DELETE FROM unknown_detections WHERE isSynced = 1")
    void clearSynced();
}
