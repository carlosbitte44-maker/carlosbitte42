package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY dataCadastro DESC")
    fun getAllLabels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels WHERE id = :id LIMIT 1")
    suspend fun getLabelById(id: Long): LabelEntity?

    @Query("""
        SELECT * FROM labels 
        WHERE nome LIKE :query 
           OR rua LIKE :query 
           OR cep LIKE :query 
           OR pedido LIKE :query 
           OR sku LIKE :query 
           OR codigoBarras LIKE :query 
           OR qrCode LIKE :query 
           OR produto LIKE :query 
           OR cidade LIKE :query 
           OR condominio LIKE :query 
           OR bloco LIKE :query 
           OR apartamento LIKE :query
        ORDER BY dataCadastro DESC
    """)
    fun searchLabels(query: String): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: LabelEntity): Long

    @Update
    suspend fun updateLabel(label: LabelEntity)

    @Delete
    suspend fun deleteLabel(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteLabelById(id: Long)
}
