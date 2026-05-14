package com.example.mytodoapp.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// This class tells Room how to handle the List<TodoTask>
class Converters {
    companion object {
        private val gson = Gson()
        private val taskListType = object : TypeToken<List<TodoTask>>() {}.type
    }

    @TypeConverter
    fun fromTaskList(value: List<TodoTask>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTaskList(value: String): List<TodoTask> {
        return gson.fromJson(value, taskListType)
    }
}
