package com.corriente.data.db.entity

import androidx.room.Entity

/**
 * Key-value настройки приложения (ARCHITECTURE.md §3.2) — базовая валюта, провайдер и т.п.,
 * чтобы не заводить миграцию схемы на каждую новую настройку.
 */
@Entity(tableName = "app_setting", primaryKeys = ["key"])
data class AppSettingEntity(
    val key: String,
    val value: String,
)
