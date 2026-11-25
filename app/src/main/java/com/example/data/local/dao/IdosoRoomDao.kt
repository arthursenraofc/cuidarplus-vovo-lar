package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.example.data.local.entity.IdosoComRegistrosMedicos
import com.example.data.local.entity.IdosoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room para acesso reativo e assíncrono aos residentes (Idosos) da ILPI.
 */
@Dao
interface IdosoRoomDao {

    @Query("SELECT * FROM idosos WHERE ativo = 1 ORDER BY nome ASC")
    fun observarTodosAtivos(): Flow<List<IdosoEntity>>

    @Query("SELECT * FROM idosos ORDER BY nome ASC")
    fun observarTodos(): Flow<List<IdosoEntity>>

    @Query("SELECT * FROM idosos WHERE id = :id LIMIT 1")
    fun observarPorId(id: Long): Flow<IdosoEntity?>

    @Query(
        "SELECT * FROM idosos " +
            "WHERE ativo = 1 AND (" +
            "nome LIKE '%' || :termo || '%' " +
            "OR alergias LIKE '%' || :termo || '%' " +
            "OR restricoes_medicas LIKE '%' || :termo || '%' " +
            "OR leito_quarto LIKE '%' || :termo || '%'" +
            ") ORDER BY nome ASC"
    )
    fun buscarPorTermo(termo: String): Flow<List<IdosoEntity>>

    @Transaction
    @Query("SELECT * FROM idosos WHERE id = :idosoId LIMIT 1")
    fun observarIdosoComRegistros(idosoId: Long): Flow<IdosoComRegistrosMedicos?>

    @Transaction
    @Query("SELECT * FROM idosos WHERE ativo = 1 ORDER BY nome ASC")
    fun observarTodosComRegistros(): Flow<List<IdosoComRegistrosMedicos>>

    @Query("SELECT COUNT(*) FROM idosos WHERE ativo = 1")
    fun observarTotalAtivos(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(idoso: IdosoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(idosos: List<IdosoEntity>): List<Long>

    @Upsert
    suspend fun upsert(idoso: IdosoEntity): Long

    @Upsert
    suspend fun upsertTodos(idosos: List<IdosoEntity>)

    @Update
    suspend fun atualizar(idoso: IdosoEntity): Int

    @Delete
    suspend fun excluir(idoso: IdosoEntity): Int

    @Query("DELETE FROM idosos WHERE id = :id")
    suspend fun excluirPorId(id: Long): Int
}
