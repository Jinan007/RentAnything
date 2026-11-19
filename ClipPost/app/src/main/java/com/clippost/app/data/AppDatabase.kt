package com.clippost.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClipEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Version 2 added the "captions" switch. A migration keeps everyone's saved queue and history. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN captions INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 3 added the vertical split-screen settings. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN vertical INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clips ADD COLUMN facecam TEXT NOT NULL DEFAULT 'TOP_RIGHT'")
                db.execSQL("ALTER TABLE clips ADD COLUMN topText TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 4 added the automatic title, caption, tags and comment switch. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN autoMeta INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 5 added the layout style (white or black background, or split screen). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN layoutStyle TEXT NOT NULL DEFAULT 'WHITE'")
            }
        }

        /** One shared database for the screen and the background worker. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clippost.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
