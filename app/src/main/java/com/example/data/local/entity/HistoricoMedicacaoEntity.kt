package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.HistoricoMedicacao

/**
 * Entidade Room que registra cada baixa/ocorrência de administração de medicação na data de referência.
 */
@Entity(
    tableName = "historico_medicacoes",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentoEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamento_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IdosoEntity::class,
            parentColumns = ["id"],
            childColumns = ["idoso_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["medicamento_id", "data_referencia"]),
        Index(value = ["idoso_id"]),
        Index(value = ["status"])
    ]
)
data class HistoricoMedicacaoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "medicamento_id")
    val medicamentoId: Long,
    @ColumnInfo(name = "idoso_id")
    val idosoId: Long,
    @ColumnInfo(name = "data_referencia")
    val dataReferencia: String,
    @ColumnInfo(name = "data_hora_prescrita")
    val dataHoraPrescrita: String = "",
    @ColumnInfo(name = "data_hora_executada")
    val dataHoraExecutada: String,
    val status: String,
    @ColumnInfo(name = "nome_cuidador")
    val nomeCuidador: String = "Cuidador de Plantão",
    val observacao: String = "",
    @ColumnInfo(name = "registrado_em")
    val registradoEm: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_ADMINISTRADO = HistoricoMedicacao.STATUS_ADMINISTRADO
        const val STATUS_ATRASADO = HistoricoMedicacao.STATUS_ATRASADO
        const val STATUS_RECUSADO = HistoricoMedicacao.STATUS_RECUSADO

        @JvmStatic
        fun fromModel(historico: HistoricoMedicacao, dataReferencia: String): HistoricoMedicacaoEntity {
            return HistoricoMedicacaoEntity(
                id = if (historico.id > 0) historico.id.toLong() else 0L,
                medicamentoId = historico.idMedicamento.toLong(),
                idosoId = historico.idIdoso.toLong(),
                dataReferencia = dataReferencia,
                dataHoraPrescrita = historico.dataHoraPrescrita,
                dataHoraExecutada = historico.dataHoraExecutada,
                status = historico.status,
                nomeCuidador = historico.nomeCuidador,
                observacao = historico.observacao
            )
        }
    }
}
