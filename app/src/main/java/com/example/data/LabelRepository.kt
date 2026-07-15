package com.example.data

import kotlinx.coroutines.flow.Flow

class LabelRepository(private val labelDao: LabelDao) {
    val allLabels: Flow<List<LabelEntity>> = labelDao.getAllLabels()

    fun searchLabels(query: String): Flow<List<LabelEntity>> {
        return labelDao.searchLabels("%$query%")
    }

    suspend fun getLabelById(id: Long): LabelEntity? {
        return labelDao.getLabelById(id)
    }

    suspend fun insert(label: LabelEntity): Long {
        return labelDao.insertLabel(label)
    }

    suspend fun update(label: LabelEntity) {
        labelDao.updateLabel(label)
    }

    suspend fun delete(label: LabelEntity) {
        labelDao.deleteLabel(label)
    }

    suspend fun deleteById(id: Long) {
        labelDao.deleteLabelById(id)
    }
}
