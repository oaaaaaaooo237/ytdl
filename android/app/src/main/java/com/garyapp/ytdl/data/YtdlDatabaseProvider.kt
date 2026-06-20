package com.garyapp.ytdl.data

import android.content.Context
import androidx.room.Room

object YtdlDatabaseProvider {
    @Volatile
    private var instance: YtdlDatabase? = null

    fun get(context: Context): YtdlDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                YtdlDatabase::class.java,
                "ytdl.db",
            ).build().also { instance = it }
        }
    }
}
