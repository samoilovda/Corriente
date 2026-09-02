package com.corriente.app.ui.recurring

import com.corriente.data.db.dao.RecurrenceDao
import com.corriente.data.db.entity.RecurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [RecurrenceDao] для юнит-тестов ViewModel (R2.4). */
class FakeRecurrenceDao : RecurrenceDao {

    val rows = MutableStateFlow<List<RecurrenceEntity>>(emptyList())

    override suspend fun insert(recurrence: RecurrenceEntity) {
        rows.value = rows.value + recurrence
    }

    override suspend fun update(recurrence: RecurrenceEntity) {
        rows.value = rows.value.map { if (it.id == recurrence.id) recurrence else it }
    }

    override suspend fun delete(recurrence: RecurrenceEntity) {
        rows.value = rows.value.filterNot { it.id == recurrence.id }
    }

    override fun observeAll(): Flow<List<RecurrenceEntity>> = rows.map { it.sortedBy { r -> r.nextRunOn } }

    override suspend fun getAll(): List<RecurrenceEntity> = rows.value

    override suspend fun getById(id: String): RecurrenceEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun recordRun(id: String, nextRunOn: String, lastCreatedTxnId: String?) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(nextRunOn = nextRunOn, lastCreatedTxnId = lastCreatedTxnId) else it
        }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
