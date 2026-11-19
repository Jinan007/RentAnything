package com.clippost.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClipEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** One shared database for the screen and the background worker. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clippost.db"
                ).build().also { instance = it }
            }
    }
}
