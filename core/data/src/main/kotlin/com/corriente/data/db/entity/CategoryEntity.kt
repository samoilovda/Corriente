package com.corriente.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CategoryKind { EXPENSE, INCOME }

/** Откуда взялась категория: вручную заведена пользователем или создана импортом (MONEFY_IMPORT.md §5). */
enum class CategoryOrigin { USER, IMPORT }

/**
 * Категория (ARCHITECTURE.md §3.2). Одна степень вложенности — [parentId] ссылается только
 * на категорию верхнего уровня, дерево глубже одного уровня не поддерживается сознательно
 * (BUILD_PLAN.md §5.3: без абстракций сверх необходимого).
 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
        ),
    ],
    indices = [
        Index("parent_id"),
        // ARCHITECTURE.md §3.2 задаёт этот индекс частичным (WHERE is_archived = 0), но Room
        // не поддерживает частичные индексы через аннотацию @Index. Упрощение: уникальность
        // действует и на архивные категории тоже — на практике не мешает (переименовать перед
        // архивированием при конфликте — секундное дело), но если это станет неудобно, индекс
        // придётся создавать сырым SQL в RoomDatabase.Callback.onCreate.
        Index(value = ["name", "kind"], unique = true, name = "ux_category_name_kind"),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val kind: CategoryKind,
    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,
    val color: Int,
    val icon: String? = null,
    @ColumnInfo(name = "origin", defaultValue = "USER")
    val origin: CategoryOrigin = CategoryOrigin.USER,
    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    /**
     * Батч импорта, создавший эту категорию (origin = IMPORT). Нужен, чтобы откат импорта
     * удалял только свои осиротевшие категории, а не все IMPORT-категории всех батчей (F1.5).
     * Схема v2.
     */
    @ColumnInfo(name = "import_batch_id")
    val importBatchId: String? = null,
)
