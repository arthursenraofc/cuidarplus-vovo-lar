package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.RegistroMedicoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room para acesso reativo e assíncrono aos Registros Médicos dos residentes da ILPI.
 */
@Dao
interface RegistroMedicoDao {

    @Query("SELECT * FROM registros_medicos WHERE idoso_id = :idosoId ORDER BY data_registro DESC, id DESC")
    fun observarPorIdoso(idosoId: Long): Flow<List<RegistroMedicoEntity>>

    @Query(
        "SELECT * FROM registros_medicos " +
            "WHERE idoso_id = :idosoId AND tipo_registro = :tipoRegistro " +
            "ORDER BY data_registro DESC, id DESC"
    )
    fun observarPorIdosoETipo(idosoId: Long, tipoRegistro: String): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos ORDER BY data_registro DESC, id DESC")
    fun observarTodos(): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE alerta_critico = 1 ORDER BY data_registro DESC, id DESC")
    fun observarAlertasCriticos(): Flow<List<RegistroMedicoEntity>>

    @Query("SELECT * FROM registros_medicos WHERE id = :id LIMIT 1")
    fun observarPorId(id: Long): Flow<RegistroMedicoEntity?>

    @Query("SELECT COUNT(*) FROM registros_medicos WHERE idoso_id = :idosoId")
    fun observarContagemPorIdoso(idosoId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(registro: RegistroMedicoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(registros: List<RegistroMedicoEntity>): List<Long>

    @Update
    suspend fun atualizar(registro: RegistroMedicoEntity): Int

    @Delete
    suspend fun excluir(registro: RegistroMedicoEntity): Int

    @Query("DELETE FROM registros_medicos WHERE id = :id")
    suspend fun excluirPorId(id: Long): Int

    @Query("DELETE FROM registros_medicos WHERE idoso_id = :idosoId")
    suspend fun excluirPorIdoso(idosoId: Long): Int
}
