package com.corriente.app.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Скругление строки внутри группы: внешние углы крупные, внутренние мелкие — группа читается как одна плитка. */
fun groupItemShape(index: Int, size: Int, outer: Dp = 20.dp, inner: Dp = 4.dp): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == size - 1) outer else inner,
        bottomEnd = if (index == size - 1) outer else inner,
    )
