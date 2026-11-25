package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidade Room que representa um Registro Médico / Evolução Clínica vinculado a um Idoso.
 * Possui chave estrangeira com exclusão em cascata para manter a integridade referencial local.
 */
@Entity(
    tableName = "registros_medicos",
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
        Index(value = ["data_registro"]),
        Index(value = ["tipo_registro"])
    ]
)
data class RegistroMedicoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "idoso_id")
    val idosoId: Long,
    @ColumnInfo(name = "tipo_registro")
    val tipoRegistro: String,
    val titulo: String,
    @ColumnInfo(name = "descricao_clinica")
    val descricaoClinica: String,
    @ColumnInfo(name = "pressao_arterial")
    val pressaoArterial: String = "",
    @ColumnInfo(name = "temperatura_c")
    val temperaturaCelsius: Double? = null,
    @ColumnInfo(name = "glicemia_mg_dl")
    val glicemiaMgDl: Int? = null,
    @ColumnInfo(name = "saturacao_o2")
    val saturacaoO2: Int? = null,
    @ColumnInfo(name = "profissional_responsavel")
    val profissionalResponsavel: String,
    @ColumnInfo(name = "crm_coren")
    val crmCoren: String = "",
    @ColumnInfo(name = "alerta_critico")
    val alertaCritico: Boolean = false,
    @ColumnInfo(name = "data_registro")
    val dataRegistro: Long = System.currentTimeMillis()
) {
    companion object {
        const val TIPO_EVOLUCAO_CLINICA = "Evolução Clínica"
        const val TIPO_SINAIS_VITAIS = "Sinais Vitais"
        const val TIPO_PRESCRICAO = "Prescrição Médica"
        const val TIPO_INTERCORRENCIA = "Intercorrência"
        const val TIPO_AVALIACAO = "Avaliação Multiprofissional"
    }
}
