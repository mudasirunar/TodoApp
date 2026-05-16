package com.example.mytodoapp.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTaskList(value: List<TodoTask>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTaskList(value: String): List<TodoTask>? {
        val listType = object : TypeToken<List<TodoTask>>() {}.type
        return gson.fromJson(value, listType)
    }
}
