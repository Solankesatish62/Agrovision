package com.agrovision.kiosk.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.agrovision.kiosk.data.database.entity.MedicineEntity;

import java.util.List;

/**
 * MedicineDao
 *
 * Data Access Object for the medicines table.
 *
 * RULES:
 * - DAO does NOT contain business logic
 * - DAO methods must be called off the UI thread
 * - DAO only works with Entity objects
 */
@Dao
public interface MedicineDao {

    /**
     * Inserts or updates a list of medicines.
     *
     * OnConflictStrategy.REPLACE ensures:
     * - Catalog refresh does not crash
     * - Latest data always wins
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MedicineEntity> medicines);

    /**
     * Fetch a single medicine by ID.
     *
     * Returns null if not found.
     */
    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    MedicineEntity getById(String id);

    /**
     * Fetch all medicines.
     *
     * Used for:
     * - Initial load
     * - Debug
     * - Cache warm-up
     */
    @Query("SELECT * FROM medicines")
    List<MedicineEntity> getAll();

    /**
     * Deletes all medicines.
     *
     * Used for:
     * - Full refresh
     * - Debug reset
     */
    @Query("DELETE FROM medicines")
    void clearAll();
}
