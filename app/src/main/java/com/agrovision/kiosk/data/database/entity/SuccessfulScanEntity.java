package com.agrovision.kiosk.data.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "successful_scans")
public class SuccessfulScanEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String medicineId;
    public long timestamp;
}
