package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.data.local.entity.HistoricoMedicacaoEntity
import com.example.data.local.entity.MedicamentoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room para acesso reativo aos medicamentos e filtragem precisa de doses pendentes e atrasadas.
 * Utiliza `NOT EXISTS` por `data_referencia` e `DISTINCT` em conjunto com `pendente = 1`
 * para impedir que registros já concluídos ou duplicados apareçam nas consultas de pendências.
 */
@Dao
interface MedicamentoRoomDao {

    @Query("SELECT * FROM medicamentos WHERE status_ativo = 1 ORDER BY horario_inicial ASC, nome_idoso ASC, id ASC")
    fun observarTodosAtivos(): Flow<List<MedicamentoEntity>>

    @Query(
        "SELECT DISTINCT m.* FROM medicamentos m " +
            "WHERE m.status_ativo = 1 " +
            "AND m.pendente = 1 " +
            "AND m.ultima_administracao_data != :dataHoje " +
            "AND m.ultimo_status_historico NOT IN ('ADMINISTRADO', 'CONCLUIDO', 'RECUSADO') " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM historico_medicacoes h " +
            "WHERE h.medicamento_id = m.id " +
            "AND h.data_referencia = :dataHoje " +
            "AND h.status IN ('ADMINISTRADO', 'ATRASADO', 'CONCLUIDO', 'RECUSADO')" +
            ") " +
            "ORDER BY m.horario_inicial ASC, m.nome_idoso ASC, m.id ASC"
    )
    fun observarPendentesDoDia(dataHoje: String): Flow<List<MedicamentoEntity>>

    @Query(
        "SELECT DISTINCT m.* FROM medicamentos m " +
            "WHERE m.status_ativo = 1 " +
            "AND m.pendente = 1 " +
            "AND m.ultima_administracao_data != :dataHoje " +
            "AND m.ultimo_status_historico NOT IN ('ADMINISTRADO', 'CONCLUIDO', 'RECUSADO') " +
            "AND length(trim(m.horario_inicial)) >= 5 " +
            "AND ((CAST(substr(trim(m.horario_inicial), 1, 2) AS INTEGER) * 60) " +
            "+ CAST(substr(trim(m.horario_inicial), 4, 2) AS INTEGER) + 15) < :agoraMinutos " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM historico_medicacoes h " +
            "WHERE h.medicamento_id = m.id " +
            "AND h.data_referencia = :dataHoje " +
            "AND h.status IN ('ADMINISTRADO', 'ATRASADO', 'CONCLUIDO', 'RECUSADO')" +
            ") " +
            "ORDER BY m.horario_inicial ASC, m.nome_idoso ASC, m.id ASC"
    )
    fun observarAtrasadosPendentes(dataHoje: String, agoraMinutos: Int): Flow<List<MedicamentoEntity>>

    @Query(
        "SELECT DISTINCT m.* FROM medicamentos m " +
            "WHERE m.status_ativo = 1 " +
            "AND m.idoso_id = :idosoId " +
            "AND m.pendente = 1 " +
            "AND m.ultima_administracao_data != :dataHoje " +
            "AND m.ultimo_status_historico NOT IN ('ADMINISTRADO', 'CONCLUIDO', 'RECUSADO') " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM historico_medicacoes h " +
            "WHERE h.medicamento_id = m.id " +
            "AND h.data_referencia = :dataHoje " +
            "AND h.status IN ('ADMINISTRADO', 'ATRASADO', 'CONCLUIDO', 'RECUSADO')" +
            ") " +
            "ORDER BY m.horario_inicial ASC, m.id ASC"
    )
    fun observarPendentesPorIdoso(idosoId: Long, dataHoje: String): Flow<List<MedicamentoEntity>>

    @Query(
        "SELECT * FROM historico_medicacoes " +
            "WHERE data_referencia = :dataHoje " +
            "ORDER BY registrado_em DESC, id DESC"
    )
    fun observarHistoricoDoDia(dataHoje: String): Flow<List<HistoricoMedicacaoEntity>>

    @Upsert
    suspend fun upsert(medicamento: MedicamentoEntity): Long

    @Upsert
    suspend fun upsertTodos(medicamentos: List<MedicamentoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirHistorico(historico: HistoricoMedicacaoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirHistoricos(historicos: List<HistoricoMedicacaoEntity>)

    @Delete
    suspend fun excluir(medicamento: MedicamentoEntity): Int

    @Query("DELETE FROM medicamentos WHERE id = :id")
    suspend fun excluirPorId(id: Long): Int

    @Query("DELETE FROM medicamentos WHERE id NOT IN (:idsAtivos)")
    suspend fun removerAusentes(idsAtivos: List<Long>)

    @Query("DELETE FROM medicamentos")
    suspend fun limparTodosMedicamentos()

    @Query("DELETE FROM historico_medicacoes WHERE data_referencia = :dataHoje")
    suspend fun limparHistoricoDoDia(dataHoje: String)

    @Query(
        "UPDATE medicamentos SET " +
            "pendente = 0, " +
            "ultima_administracao_data = :dataHoje, " +
            "ultimo_status_historico = :statusHistorico, " +
            "estoque = CASE WHEN :novoEstoque != '' THEN :novoEstoque ELSE estoque END, " +
            "atualizado_em = :atualizadoEm " +
            "WHERE id = :medicamentoId"
    )
    suspend fun marcarComoConcluidoNoDia(
        medicamentoId: Long,
        dataHoje: String,
        statusHistorico: String,
        novoEstoque: String,
        atualizadoEm: Long = System.currentTimeMillis()
    ): Int

    @Query(
        "UPDATE medicamentos SET " +
            "pendente = 1, " +
            "ultimo_status_historico = '', " +
            "atualizado_em = :atualizadoEm " +
            "WHERE status_ativo = 1 AND ultima_administracao_data != :dataHoje"
    )
    suspend fun resetarPendenciasParaNovoDia(
        dataHoje: String,
        atualizadoEm: Long = System.currentTimeMillis()
    ): Int

    @Transaction
    suspend fun registrarBaixaDose(
        medicamentoId: Long,
        idosoId: Long,
        horarioPrevisto: String,
        dataHoje: String,
        dataHoraExecutada: String,
        statusHistorico: String,
        nomeCuidador: String,
        observacao: String,
        novoEstoque: String
    ) {
        inserirHistorico(
            HistoricoMedicacaoEntity(
                medicamentoId = medicamentoId,
                idosoId = idosoId,
                dataReferencia = dataReferenciaNormalizada(dataHoje),
                dataHoraPrescrita = horarioPrevisto,
                dataHoraExecutada = dataHoraExecutada,
                status = statusHistorico,
                nomeCuidador = nomeCuidador,
                observacao = observacao
            )
        )
        marcarComoConcluidoNoDia(
            medicamentoId = medicamentoId,
            dataHoje = dataReferenciaNormalizada(dataHoje),
            statusHistorico = statusHistorico,
            novoEstoque = novoEstoque
        )
    }

    private fun dataReferenciaNormalizada(dataHoje: String): String = dataHoje.trim()
}
