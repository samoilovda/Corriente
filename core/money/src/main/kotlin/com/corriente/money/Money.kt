package com.corriente.money

/**
 * Сумма в минорных единицах валюты (копейки, центы, ...). Инвариант I-1: деньги никогда
 * не представлены Double/Float — только Long минорных единиц.
 */
@JvmInline
value class Minor(val raw: Long)

/**
 * Класс исключения, который выбрасывается при попытке смешать разные валюты в одной
 * арифметической операции (инвариант I-2). Несовпадение валют — программная ошибка,
 * а не состояние, которое нужно "уладить" автоматической конвертацией.
 */
class CurrencyMismatchException(a: CurrencyCode, b: CurrencyCode) :
    IllegalArgumentException("Currency mismatch: $a vs $b")

/**
 * Сумма денег: минорные единицы + код валюты. Основной тип для всех денежных величин
 * в приложении (ARCHITECTURE.md §2.1, ADR-002).
 *
 * Инварианты, которые здесь соблюдаются:
 *  - I-1: никакого Double/Float.
 *  - I-2: арифметика только между одинаковыми валютами, иначе исключение.
 *  - I-3: сложение/вычитание/отрицание через Math.*Exact — переполнение падает, а не молчит.
 */
data class Money(val amount: Minor, val currency: CurrencyCode) {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Minor(Math.addExact(amount.raw, other.amount.raw)), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Minor(Math.subtractExact(amount.raw, other.amount.raw)), currency)
    }

    operator fun unaryMinus(): Money = Money(Minor(Math.negateExact(amount.raw)), currency)

    /** Умножение на целое — единственная безопасная операция без риска дробного округления. */
    operator fun times(factor: Int): Money =
        Money(Minor(Math.multiplyExact(amount.raw, factor.toLong())), currency)

    operator fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.raw.compareTo(other.amount.raw)
    }

    val isNegative: Boolean get() = amount.raw < 0
    val isPositive: Boolean get() = amount.raw > 0
    val isZero: Boolean get() = amount.raw == 0L

    fun absolute(): Money = if (isNegative) -this else this

    private fun requireSameCurrency(other: Money) {
        if (currency != other.currency) throw CurrencyMismatchException(currency, other.currency)
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(Minor(0), currency)
    }
}

/**
 * Сумма списка денег одной валюты. Падает при пустом списке (нет валюты, к которой привести
 * ноль) и при разных валютах в списке (I-2) — вызывающий код обязан сгруппировать по валюте
 * заранее, а не полагаться на скрытую конвертацию.
 */
fun List<Money>.sumMoney(): Money {
    require(isNotEmpty()) { "Cannot sum an empty list of Money: no currency to attach zero to" }
    return reduce { a, b -> a + b }
}
