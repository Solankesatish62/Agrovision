package com.agrovision.kiosk.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.agrovision.kiosk.data.database.converter.StringListConverter;
import com.agrovision.kiosk.data.database.dao.MedicineDao;
import com.agrovision.kiosk.data.database.entity.MedicineEntity;

/**
 * AppDatabase
 *
 * Central Room database for AgroVision kiosk.
 *
 * PURPOSE:
 * - Store small, stable metadata
 * - Persist reference data safely
 *
 * HARD RULES:
 * - NO Bitmaps
 * - NO OCR text
 * - NO session data
 * - Application Context only
 */
@Database(
        entities = {
                MedicineEntity.class
        },
        version = 1,
        exportSchema = false
)
@TypeConverters({
        StringListConverter.class
})
public abstract class AppDatabase extends RoomDatabase {

    // Singleton instance (volatile for thread safety)
    private static volatile AppDatabase INSTANCE;

    /**
     * Returns the singleton AppDatabase instance.
     *
     * IMPORTANT:
     * - Always use Application Context
     * - Never use Activity context
     */
    public static AppDatabase getInstance(Context context) {

        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {

                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "agrovision.db"
                            )
                            // Destructive migration is acceptable for MVP
                            // because this DB stores reference data only
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /* =======================
       DAO Accessors
       ======================= */

    /**
     * Provides access to Medicine DAO.
     */
    public abstract MedicineDao medicineDao();
}
