package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.IdosoRoomDao
import com.example.data.local.dao.MedicamentoRoomDao
import com.example.data.local.dao.RegistroMedicoDao
import com.example.data.local.entity.HistoricoMedicacaoEntity
import com.example.data.local.entity.IdosoEntity
import com.example.data.local.entity.MedicamentoEntity
import com.example.data.local.entity.RegistroMedicoEntity

/**
 * Banco de dados local Room para persistência offline dos dados da ILPI Vovô Lar.
 * Versão 2: inclui [MedicamentoEntity] e [HistoricoMedicacaoEntity] para controle reativo
 * de doses pendentes e atrasadas sem duplicidades.
 */
@Database(
    entities = [
        IdosoEntity::class,
        RegistroMedicoEntity::class,
        MedicamentoEntity::class,
        HistoricoMedicacaoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class IlpiRoomDatabase : RoomDatabase() {

    abstract fun idosoDao(): IdosoRoomDao

    abstract fun registroMedicoDao(): RegistroMedicoDao

    abstract fun medicamentoDao(): MedicamentoRoomDao

    companion object {
        const val DATABASE_NAME = "cuidarplus_ilpi_room.db"

        @JvmField
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medicamentos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `idoso_id` INTEGER NOT NULL,
                        `nome_idoso` TEXT NOT NULL DEFAULT '',
                        `nome_medicamento` TEXT NOT NULL,
                        `dosagem` TEXT NOT NULL,
                        `estoque` TEXT NOT NULL DEFAULT '',
                        `tipo` TEXT NOT NULL DEFAULT 'Comprimido',
                        `horario_inicial` TEXT NOT NULL,
                        `frequencia_horas` INTEGER NOT NULL DEFAULT 8,
                        `observacao_medica` TEXT NOT NULL DEFAULT '',
                        `status_ativo` INTEGER NOT NULL DEFAULT 1,
                        `pendente` INTEGER NOT NULL DEFAULT 1,
                        `ultima_administracao_data` TEXT NOT NULL DEFAULT '',
                        `ultimo_status_historico` TEXT NOT NULL DEFAULT '',
                        `atualizado_em` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`idoso_id`) REFERENCES `idosos`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicamentos_idoso_id` ON `medicamentos` (`idoso_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicamentos_status_ativo_pendente` ON `medicamentos` (`status_ativo`, `pendente`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicamentos_horario_inicial` ON `medicamentos` (`horario_inicial`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `historico_medicacoes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicamento_id` INTEGER NOT NULL,
                        `idoso_id` INTEGER NOT NULL,
                        `data_referencia` TEXT NOT NULL,
                        `data_hora_prescrita` TEXT NOT NULL DEFAULT '',
                        `data_hora_executada` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `nome_cuidador` TEXT NOT NULL DEFAULT 'Cuidador de Plantão',
                        `observacao` TEXT NOT NULL DEFAULT '',
                        `registrado_em` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`medicamento_id`) REFERENCES `medicamentos`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`idoso_id`) REFERENCES `idosos`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_historico_medicacoes_medicamento_id_data_referencia` ON `historico_medicacoes` (`medicamento_id`, `data_referencia`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_historico_medicacoes_idoso_id` ON `historico_medicacoes` (`idoso_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_historico_medicacoes_status` ON `historico_medicacoes` (`status`)")
            }
        }

        @Volatile
        private var INSTANCE: IlpiRoomDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): IlpiRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IlpiRoomDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        @JvmStatic
        fun resetInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
