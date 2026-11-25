package com.example.data.repository

import android.content.Context
import com.example.data.local.IlpiRoomDatabase
import com.example.data.local.dao.IdosoRoomDao
import com.example.data.local.dao.MedicamentoRoomDao
import com.example.data.local.dao.RegistroMedicoDao
import com.example.data.local.entity.HistoricoMedicacaoEntity
import com.example.data.local.entity.IdosoComRegistrosMedicos
import com.example.data.local.entity.IdosoEntity
import com.example.data.local.entity.MedicamentoEntity
import com.example.data.local.entity.RegistroMedicoEntity
import com.example.model.HistoricoMedicacao
import com.example.model.Idoso
import com.example.model.Medicamento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Repositório central da camada de persistência local Room da ILPI.
 * Abstrai o acesso aos DAOs [IdosoRoomDao], [RegistroMedicoDao] e [MedicamentoRoomDao] para ViewModels e UI.
 */
class IlpiRepository @JvmOverloads constructor(
    private val idosoDao: IdosoRoomDao,
    private val registroMedicoDao: RegistroMedicoDao,
    private val medicamentoDao: MedicamentoRoomDao? = null
) {
    val idososAtivos: Flow<List<IdosoEntity>> = idosoDao.observarTodosAtivos()
    val todosIdosos: Flow<List<IdosoEntity>> = idosoDao.observarTodos()
    val totalIdososAtivos: Flow<Int> = idosoDao.observarTotalAtivos()
    val idososComRegistros: Flow<List<IdosoComRegistrosMedicos>> = idosoDao.observarTodosComRegistros()
    val todosRegistrosMedicos: Flow<List<RegistroMedicoEntity>> = registroMedicoDao.observarTodos()
    val alertasClinicosCriticos: Flow<List<RegistroMedicoEntity>> = registroMedicoDao.observarAlertasCriticos()

    fun observarIdosoPorId(id: Long): Flow<IdosoEntity?> = idosoDao.observarPorId(id)

    fun buscarIdososPorTermo(termo: String): Flow<List<IdosoEntity>> = idosoDao.buscarPorTermo(termo)

    fun observarIdosoComRegistros(idosoId: Long): Flow<IdosoComRegistrosMedicos?> =
        idosoDao.observarIdosoComRegistros(idosoId)

    fun observarRegistrosMedicosDoIdoso(idosoId: Long): Flow<List<RegistroMedicoEntity>> =
        registroMedicoDao.observarPorIdoso(idosoId)

    fun observarRegistrosMedicosPorTipo(
        idosoId: Long,
        tipoRegistro: String
    ): Flow<List<RegistroMedicoEntity>> = registroMedicoDao.observarPorIdosoETipo(idosoId, tipoRegistro)

    fun observarRegistroMedicoPorId(id: Long): Flow<RegistroMedicoEntity?> =
        registroMedicoDao.observarPorId(id)

    fun observarTodosMedicamentosAtivos(): Flow<List<MedicamentoEntity>> =
        medicamentoDao?.observarTodosAtivos() ?: flowOf(emptyList())

    fun observarMedicamentosPendentes(dataHoje: String): Flow<List<MedicamentoEntity>> =
        medicamentoDao?.observarPendentesDoDia(dataHoje) ?: flowOf(emptyList())

    fun observarMedicamentosAtrasadosPendentes(
        dataHoje: String,
        agoraMinutos: Int
    ): Flow<List<MedicamentoEntity>> =
        medicamentoDao?.observarAtrasadosPendentes(dataHoje, agoraMinutos) ?: flowOf(emptyList())

    fun observarMedicamentosPendentesPorIdoso(
        idosoId: Long,
        dataHoje: String
    ): Flow<List<MedicamentoEntity>> =
        medicamentoDao?.observarPendentesPorIdoso(idosoId, dataHoje) ?: flowOf(emptyList())

    fun observarHistoricoMedicacoesDoDia(dataHoje: String): Flow<List<HistoricoMedicacaoEntity>> =
        medicamentoDao?.observarHistoricoDoDia(dataHoje) ?: flowOf(emptyList())

    suspend fun inserirIdoso(idoso: IdosoEntity): Long {
        val agora = System.currentTimeMillis()
        return idosoDao.inserir(
            idoso.copy(
                criadoEm = if (idoso.criadoEm > 0L) idoso.criadoEm else agora,
                atualizadoEm = agora
            )
        )
    }

    suspend fun atualizarIdoso(idoso: IdosoEntity): Int =
        idosoDao.atualizar(idoso.copy(atualizadoEm = System.currentTimeMillis()))

    suspend fun excluirIdoso(idoso: IdosoEntity): Int = idosoDao.excluir(idoso)

    suspend fun excluirIdosoPorId(id: Long): Int = idosoDao.excluirPorId(id)

    suspend fun inserirRegistroMedico(registro: RegistroMedicoEntity): Long =
        registroMedicoDao.inserir(registro)

    suspend fun atualizarRegistroMedico(registro: RegistroMedicoEntity): Int =
        registroMedicoDao.atualizar(registro)

    suspend fun excluirRegistroMedico(registro: RegistroMedicoEntity): Int =
        registroMedicoDao.excluir(registro)

    suspend fun excluirRegistroMedicoPorId(id: Long): Int =
        registroMedicoDao.excluirPorId(id)

    suspend fun inserirOuAtualizarMedicamento(medicamento: MedicamentoEntity): Long =
        medicamentoDao?.upsert(medicamento.copy(atualizadoEm = System.currentTimeMillis())) ?: -1L

    suspend fun excluirMedicamentoPorId(id: Long): Int =
        medicamentoDao?.excluirPorId(id) ?: 0

    suspend fun resetarPendenciasParaNovoDia(dataHoje: String): Int =
        medicamentoDao?.resetarPendenciasParaNovoDia(dataHoje) ?: 0

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
        medicamentoDao?.registrarBaixaDose(
            medicamentoId = medicamentoId,
            idosoId = idosoId,
            horarioPrevisto = horarioPrevisto,
            dataHoje = dataHoje,
            dataHoraExecutada = dataHoraExecutada,
            statusHistorico = statusHistorico,
            nomeCuidador = nomeCuidador,
            observacao = observacao,
            novoEstoque = novoEstoque
        )
    }

    /**
     * Sincroniza o estado do SQLite legado para as entidades Room sem disparar exclusões em cascata,
     * preservando registros clínicos vinculados e marcando corretamente pendências resolvidas no dia.
     */
    suspend fun sincronizarMedicamentosComSnapshot(
        idosos: List<Idoso>,
        medicamentos: List<Medicamento>,
        historicosHoje: List<HistoricoMedicacao>,
        dataHoje: String
    ) {
        val medDao = medicamentoDao ?: return

        if (idosos.isNotEmpty()) {
            idosoDao.upsertTodos(idosos.map { IdosoEntity.fromModel(it) })
        }

        val statusResolvidoPorMedId = LinkedHashMap<Int, String>()
        for (h in historicosHoje) {
            val st = h.status.uppercase()
            if (st == HistoricoMedicacao.STATUS_ADMINISTRADO ||
                st == HistoricoMedicacao.STATUS_ATRASADO ||
                st == HistoricoMedicacao.STATUS_RECUSADO ||
                st == "CONCLUIDO"
            ) {
                statusResolvidoPorMedId[h.idMedicamento] = st
            }
        }

        val idososExistentesIds = idosos.map { it.id }.toSet()
        val medEntities = medicamentos
            .filter { it.id > 0 && (idososExistentesIds.isEmpty() || it.idIdoso in idososExistentesIds) }
            .distinctBy { it.id }
            .map { med ->
                val statusResolvido = statusResolvidoPorMedId[med.id]
                val pendente = statusResolvido == null
                MedicamentoEntity.fromModel(
                    med = med,
                    pendente = pendente,
                    ultimaAdministracaoData = if (pendente) "" else dataHoje,
                    ultimoStatusHistorico = statusResolvido ?: ""
                )
            }

        if (medEntities.isEmpty()) {
            medDao.limparTodosMedicamentos()
        } else {
            medDao.upsertTodos(medEntities)
            medDao.removerAusentes(medEntities.map { it.id })
        }

        medDao.limparHistoricoDoDia(dataHoje)
        if (historicosHoje.isNotEmpty()) {
            val medIdsValidos = medEntities.map { it.id.toInt() }.toSet()
            val histEntities = historicosHoje
                .filter { it.idMedicamento in medIdsValidos && it.idIdoso > 0 }
                .map { HistoricoMedicacaoEntity.fromModel(it, dataHoje) }
            if (histEntities.isNotEmpty()) {
                medDao.inserirHistoricos(histEntities)
            }
        }
    }

    /**
     * Popula registros iniciais da ILPI caso o banco Room local ainda esteja vazio.
     * @return true se os dados iniciais foram inseridos agora, false se já existiam residentes.
     */
    suspend fun popularDadosIniciaisSeVazio(): Boolean {
        if (idosoDao.observarTotalAtivos().first() > 0) {
            return false
        }

        val idFrancisca = idosoDao.inserir(
            IdosoEntity(
                nome = "Dona Francisca de Souza",
                dataNascimento = "15/04/1945",
                cpf = "184.520.390-15",
                tipoSanguineo = "O+",
                alergias = "Alergia a Penicilina",
                restricoesMedicas = "Hipertensão controlada; dieta hipossódica",
                leitoQuarto = "Quarto 01 - Leito A",
                nomeFamiliar = "Mariana de Souza",
                grauParentesco = "Filha",
                contatoEmergencia = "(51) 98765-4321"
            )
        )

        val idAntonio = idosoDao.inserir(
            IdosoEntity(
                nome = "Seu Antônio Carlos Ribeiro",
                dataNascimento = "08/11/1939",
                cpf = "249.811.040-72",
                tipoSanguineo = "A+",
                alergias = "Nenhuma alergia relatada",
                restricoesMedicas = "Doença de Parkinson inicial; auxílio na deambulação",
                leitoQuarto = "Quarto 03 - Leito B",
                nomeFamiliar = "Roberto Ribeiro",
                grauParentesco = "Filho",
                contatoEmergencia = "(51) 99123-8877"
            )
        )

        registroMedicoDao.inserirTodos(
            listOf(
                RegistroMedicoEntity(
                    idosoId = idFrancisca,
                    tipoRegistro = RegistroMedicoEntity.TIPO_SINAIS_VITAIS,
                    titulo = "Aferição Matinal de Sinais Vitais",
                    descricaoClinica = "Paciente lúcida, orientada e hidratada. Sem queixas álgicas.",
                    pressaoArterial = "120/80 mmHg",
                    temperaturaCelsius = 36.5,
                    glicemiaMgDl = 98,
                    saturacaoO2 = 97,
                    profissionalResponsavel = "Enf. Camila Mendes",
                    crmCoren = "COREN-RS 412890",
                    alertaCritico = false
                ),
                RegistroMedicoEntity(
                    idosoId = idAntonio,
                    tipoRegistro = RegistroMedicoEntity.TIPO_EVOLUCAO_CLINICA,
                    titulo = "Acompanhamento Motor e Tremor de Repouso",
                    descricaoClinica = "Boa resposta ao horário matinal de medicação antiparkinsoniana; deambulação supervisionada sem intercorrências.",
                    pressaoArterial = "130/80 mmHg",
                    temperaturaCelsius = 36.4,
                    saturacaoO2 = 96,
                    profissionalResponsavel = "Dr. Henrique Silveira",
                    crmCoren = "CRM-RS 38910",
                    alertaCritico = false
                )
            )
        )

        return true
    }

    companion object {
        @JvmStatic
        fun create(context: Context): IlpiRepository {
            val db = IlpiRoomDatabase.getInstance(context)
            return IlpiRepository(db.idosoDao(), db.registroMedicoDao(), db.medicamentoDao())
        }
    }
}
