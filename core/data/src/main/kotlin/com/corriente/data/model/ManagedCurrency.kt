package com.corriente.data.model

import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.seed.ISO_CURRENCIES
import com.corriente.money.CurrencyCode

/**
 * Строка экрана управления валютами (T1.2): справочная валюта ISO-4217 вместе с
 * пользовательскими настройками её отображения ([symbol], [displayScale]) и признаком
 * участия в обороте ([isActive], I-14 — это настройка, а не свойство справочника).
 *
 * [name] — англоязычное название из [ISO_CURRENCIES]; в БД оно не хранится (в таблице
 * `currency` нужны только код и параметры отображения), поэтому подставляется здесь.
 */
data class ManagedCurrency(
    val code: CurrencyCode,
    val name: String,
    val symbol: String,
    val minorUnits: Int,
    val displayScale: Int,
    val isActive: Boolean,
)

private val ISO_NAME_BY_CODE: Map<String, String> = ISO_CURRENCIES.associate { it.code to it.name }

fun CurrencyEntity.toManaged(): ManagedCurrency = ManagedCurrency(
    code = CurrencyCode(code),
    name = ISO_NAME_BY_CODE[code] ?: code,
    symbol = symbol,
    minorUnits = minorUnits,
    displayScale = displayScale,
    isActive = isActive,
)
