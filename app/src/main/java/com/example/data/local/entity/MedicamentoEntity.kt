package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Medicamento

/**
 * Entidade Room que representa uma prescrição de medicamento vinculada a um residente da ILPI.
 * Mantém estado explícito de pendência diária ([pendente], [ultimaAdministracaoData])
 * para filtragem precisa de doses pendentes e atrasadas sem duplicidade.
 */
@Entity(
    tableName = "medicamentos",
    foreignKeys = [
        ForeignKey(
            entity = IdosoEntity::class,
            parentColumns = ["id"],
            childColumns = ["idoso_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["idoso_id"]),
        Index(value = ["status_ativo", "pendente"]),
        Index(value = ["horario_inicial"])
    ]
)
data class MedicamentoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "idoso_id")
    val idosoId: Long,
    @ColumnInfo(name = "nome_idoso")
    val nomeIdoso: String = "",
    @ColumnInfo(name = "nome_medicamento")
    val nomeMedicamento: String,
    val dosagem: String,
    val estoque: String = "",
    val tipo: String = "Comprimido",
    @ColumnInfo(name = "horario_inicial")
    val horarioInicial: String,
    @ColumnInfo(name = "frequencia_horas")
    val frequenciaHoras: Int = 8,
    @ColumnInfo(name = "observacao_medica")
    val observacaoMedica: String = "",
    @ColumnInfo(name = "status_ativo")
    val statusAtivo: Boolean = true,
    val pendente: Boolean = true,
    @ColumnInfo(name = "ultima_administracao_data")
    val ultimaAdministracaoData: String = "",
    @ColumnInfo(name = "ultimo_status_historico")
    val ultimoStatusHistorico: String = "",
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long = System.currentTimeMillis()
) {
    fun toModel(): Medicamento {
        return Medicamento(
            id.toInt(),
            idosoId.toInt(),
            nomeMedicamento,
            dosagem,
            tipo,
            horarioInicial,
            frequenciaHoras,
            observacaoMedica,
            if (statusAtivo) 1 else 0
        ).apply {
            this.estoque = this@MedicamentoEntity.estoque
            this.nomeIdoso = this@MedicamentoEntity.nomeIdoso
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun fromModel(
            med: Medicamento,
            pendente: Boolean = true,
            ultimaAdministracaoData: String = "",
            ultimoStatusHistorico: String = ""
        ): MedicamentoEntity {
            return MedicamentoEntity(
                id = if (med.id > 0) med.id.toLong() else 0L,
                idosoId = med.idIdoso.toLong(),
                nomeIdoso = med.nomeIdoso.orEmpty(),
                nomeMedicamento = med.nomeMedicamento.orEmpty(),
                dosagem = med.dosagem.orEmpty(),
                estoque = med.estoque,
                tipo = med.tipo,
                horarioInicial = med.horarioInicial.orEmpty(),
                frequenciaHoras = med.frequenciaHoras,
                observacaoMedica = med.observacaoMedica,
                statusAtivo = med.isAtivo,
                pendente = pendente,
                ultimaAdministracaoData = ultimaAdministracaoData,
                ultimoStatusHistorico = ultimoStatusHistorico
            )
        }
    }
}
