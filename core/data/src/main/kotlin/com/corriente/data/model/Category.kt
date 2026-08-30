package com.corriente.data.model

import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String?,
    val color: Int,
    val icon: String?,
    val origin: CategoryOrigin,
    val displayOrder: Int,
    val isArchived: Boolean,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    color = color,
    icon = icon,
    origin = origin,
    displayOrder = displayOrder,
    isArchived = isArchived,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    color = color,
    icon = icon,
    origin = origin,
    displayOrder = displayOrder,
    isArchived = isArchived,
)
