package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Idoso

/**
 * Entidade Room que representa um residente (Idoso) acolhido na ILPI Vovô Lar.
 */
@Entity(
    tableName = "idosos",
    indices = [
        Index(value = ["nome"]),
        Index(value = ["cpf"])
    ]
)
data class IdosoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val nome: String,
    @ColumnInfo(name = "data_nascimento")
    val dataNascimento: String,
    val cpf: String = "",
    @ColumnInfo(name = "tipo_sanguineo")
    val tipoSanguineo: String,
    val alergias: String = "",
    @ColumnInfo(name = "restricoes_medicas")
    val restricoesMedicas: String = "",
    @ColumnInfo(name = "leito_quarto")
    val leitoQuarto: String = "",
    @ColumnInfo(name = "nome_familiar")
    val nomeFamiliar: String = "",
    @ColumnInfo(name = "grau_parentesco")
    val grauParentesco: String = "",
    @ColumnInfo(name = "contato_emergencia")
    val contatoEmergencia: String = "",
    @ColumnInfo(name = "caminho_foto")
    val caminhoFoto: String? = null,
    val ativo: Boolean = true,
    @ColumnInfo(name = "criado_em")
    val criadoEm: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "atualizado_em")
    val atualizadoEm: Long = System.currentTimeMillis()
) {
    /**
     * Converte a entidade Room para o modelo de domínio Java utilizado nas telas existentes.
     */
    fun toModel(): Idoso {
        return Idoso(
            id.toInt(),
            nome,
            dataNascimento,
            tipoSanguineo,
            alergias,
            restricoesMedicas,
            contatoEmergencia,
            caminhoFoto
        ).apply {
            this.cpf = this@IdosoEntity.cpf
            this.leitoQuarto = this@IdosoEntity.leitoQuarto
            this.nomeFamiliar = this@IdosoEntity.nomeFamiliar
            this.grauParentesco = this@IdosoEntity.grauParentesco
        }
    }

    companion object {
        fun fromModel(idoso: Idoso): IdosoEntity {
            return IdosoEntity(
                id = if (idoso.id > 0) idoso.id.toLong() else 0L,
                nome = idoso.nome.orEmpty(),
                dataNascimento = idoso.dataNascimento.orEmpty(),
                cpf = idoso.cpf.orEmpty(),
                tipoSanguineo = idoso.tipoSanguineo.orEmpty(),
                alergias = idoso.alergias.orEmpty(),
                restricoesMedicas = idoso.restricoesMedicas.orEmpty(),
                leitoQuarto = idoso.leitoQuarto.orEmpty(),
                nomeFamiliar = idoso.nomeFamiliar.orEmpty(),
                grauParentesco = idoso.grauParentesco.orEmpty(),
                contatoEmergencia = idoso.contatoEmergencia.orEmpty(),
                caminhoFoto = idoso.caminhoFoto
            )
        }
    }
}
