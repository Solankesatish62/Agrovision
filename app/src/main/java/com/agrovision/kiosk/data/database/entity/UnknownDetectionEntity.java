package com.agrovision.kiosk.data.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * UnknownDetectionEntity
 *
 * Stores OCR text and metadata for medicines not found in the catalog.
 * This data is synced to Firebase for system training.
 */
@Entity(tableName = "unknown_detections")
public class UnknownDetectionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * The raw OCR text that was detected.
     */
    @NonNull
    public String rawOcrText;

    /**
     * Local path to the captured image of the bottle.
     * Nullable if image capture failed.
     */
    public String localImagePath;

    /**
     * Timestamp of the detection.
     */
    public long timestamp;

    /**
     * Sync status flag.
     * false = pending upload to Firebase
     * true = upload successful
     */
    public boolean isSynced;

    public UnknownDetectionEntity(@NonNull String rawOcrText, String localImagePath, long timestamp) {
        this.rawOcrText = rawOcrText;
        this.localImagePath = localImagePath;
        this.timestamp = timestamp;
        this.isSynced = false;
    }
}
