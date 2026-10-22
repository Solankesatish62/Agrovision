package com.agrovision.kiosk.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.agrovision.kiosk.data.database.entity.SuccessfulScanEntity;

import java.util.List;

@Dao
public interface SuccessfulScanDao {

    @Insert
    long insert(SuccessfulScanEntity scan);

    @Query("SELECT * FROM successful_scans ORDER BY timestamp DESC")
    List<SuccessfulScanEntity> getAll();

    @Query("SELECT COUNT(*) FROM successful_scans")
    int getCount();
}
