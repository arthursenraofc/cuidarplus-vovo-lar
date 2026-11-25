package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Relação Room 1:N entre um residente (IdosoEntity) e todo o seu histórico de Registros Médicos.
 */
data class IdosoComRegistrosMedicos(
    @Embedded
    val idoso: IdosoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "idoso_id"
    )
    val registrosMedicos: List<RegistroMedicoEntity>
)
