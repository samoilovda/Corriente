package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Справочник валют (ARCHITECTURE.md §3.2). Засеивается полным списком активных валют
 * ISO-4217 при первом запуске ([com.corriente.data.seed.IsoCurrencies]) — инвариант I-14:
 * добавление валюты в оборот — это [isActive] = true, а не миграция схемы.
 *
 * [minorUnits] хранится по стандарту ISO-4217 (сколько десятичных знаков в минорной единице).
 * [displayScale] — сколько знаков реально показывать пользователю; по умолчанию равен
 * [minorUnits], пользователь может уменьшить его в настройках (T1.2), не трогая хранимые данные.
 */
@Entity(tableName = "currency")
data class CurrencyEntity(
    @PrimaryKey
    val code: String,
    @ColumnInfo(name = "minor_units")
    val minorUnits: Int,
    @ColumnInfo(name = "display_scale")
    val displayScale: Int,
    val symbol: String,
    @ColumnInfo(name = "is_active", defaultValue = "0")
    val isActive: Boolean = false,
    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,
)
