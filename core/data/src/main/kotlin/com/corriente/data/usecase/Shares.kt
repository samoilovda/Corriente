package com.corriente.data.usecase

/**
 * Проценты от суммы [grand] по методу наибольших остатков: floor каждой доли плюс раздача
 * остатка (100 − сумма floor'ов) тем позициям, у которых дробная часть больше.
 * I-3: умножение через `Math.multiplyExact`.
 */
fun largestRemainderShares(values: List<Long>, grand: Long): List<Int> {
    if (grand <= 0L || values.isEmpty()) return List(values.size) { 0 }
    val scaled = values.map { Math.multiplyExact(it, 100L) }
    val floors = scaled.map { (it / grand).toInt() }.toIntArray()
    val leftover = (100 - floors.sum()).coerceIn(0, values.size)
    scaled.indices
        .sortedByDescending { scaled[it] % grand }
        .take(leftover)
        .forEach { floors[it] += 1 }
    return floors.toList()
}
