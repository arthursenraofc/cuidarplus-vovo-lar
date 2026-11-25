package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.HistoricoMedicacaoEntity
import com.example.data.local.entity.MedicamentoEntity
import com.example.data.repository.IlpiRepository
import com.example.model.DoseAlertaDTO
import com.example.model.HistoricoMedicacao
import com.example.model.Idoso
import com.example.model.Medicamento
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class FiltroMedicacao {
    SOMENTE_PENDENTES,
    SOMENTE_ATRASADOS,
    POR_IDOSO,
    TODOS_DO_DIA
}

sealed class MedicamentosUiState {
    data object Carregando : MedicamentosUiState()

    data class Sucesso(
        val doses: List<DoseAlertaDTO>,
        val totalPendentes: Int,
        val totalAtrasados: Int,
        val totalConcluidos: Int,
        val filtroAtivo: FiltroMedicacao,
        val idosoIdSelecionado: Int,
        val snapshotVersion: Long = 0L
    ) : MedicamentosUiState()

    data class Erro(val mensagem: String) : MedicamentosUiState()
}

data class FiltroParametros(
    val filtro: FiltroMedicacao = FiltroMedicacao.SOMENTE_PENDENTES,
    val idosoId: Int = -1,
    val dataHoje: String = MedicamentosViewModel.dataAtualFormatada(),
    val agoraMinutos: Int = MedicamentosViewModel.minutosAtuaisDoDia()
)

/**
 * ViewModel reativo para controle de doses pendentes e atrasadas apoiado pelo Room Database.
 * Garante filtragem estrita de pendências (`!administradoHoje`, `pendente == true`, sem duplicidade por ID)
 * e remoção imediata de doses concluídas ao dar baixa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicamentosViewModel(
    private val repository: IlpiRepository
) : ViewModel() {

    fun interface JavaUiStateObserver {
        fun onChanged(state: MedicamentosUiState)
    }

    private val writeMutex = Mutex()
    private val parametrosFiltro = MutableStateFlow(FiltroParametros())
    private val versaoSnapshotSincronizada = MutableStateFlow(0L)

    val uiState: StateFlow<MedicamentosUiState> = parametrosFiltro
        .flatMapLatest { params ->
            observarEstadoPorParametros(params)
        }
        .catch { erro ->
            emit(MedicamentosUiState.Erro(erro.message ?: "Falha ao carregar medicamentos"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MedicamentosUiState.Carregando
        )

    private fun observarEstadoPorParametros(params: FiltroParametros): Flow<MedicamentosUiState> {
        val fluxoPrincipal: Flow<List<MedicamentoEntity>> = when (params.filtro) {
            FiltroMedicacao.SOMENTE_ATRASADOS ->
                repository.observarMedicamentosAtrasadosPendentes(params.dataHoje, params.agoraMinutos)
            FiltroMedicacao.POR_IDOSO ->
                if (params.idosoId > 0) {
                    repository.observarMedicamentosPendentesPorIdoso(params.idosoId.toLong(), params.dataHoje)
                } else {
                    repository.observarMedicamentosPendentes(params.dataHoje)
                }
            FiltroMedicacao.SOMENTE_PENDENTES ->
                repository.observarMedicamentosPendentes(params.dataHoje)
            FiltroMedicacao.TODOS_DO_DIA ->
                repository.observarTodosMedicamentosAtivos()
        }

        return combine(
            fluxoPrincipal,
            repository.observarTodosMedicamentosAtivos(),
            repository.observarHistoricoMedicacoesDoDia(params.dataHoje),
            versaoSnapshotSincronizada
        ) { filtradosRoom, todosAtivos, historicoHoje, versaoSnapshot ->
            val idsConcluidosHoje = extrairIdsConcluidosNoDia(todosAtivos, historicoHoje, params.dataHoje)

            // Filtra estritamente registros pendentes para os modos operacionais, removendo qualquer duplicidade por ID
            val medicamentosUnicos = filtradosRoom
                .distinctBy { it.id }
                .filter { entity ->
                    if (params.filtro == FiltroMedicacao.TODOS_DO_DIA) {
                        params.idosoId <= 0 || entity.idosoId.toInt() == params.idosoId
                    } else {
                        ehRegistroPendente(entity, idsConcluidosHoje, params.dataHoje)
                    }
                }

            val dosesMapeadas = medicamentosUnicos
                .map { entity ->
                    val concluido = idsConcluidosHoje.contains(entity.id) ||
                        !ehRegistroPendente(entity, idsConcluidosHoje, params.dataHoje)
                    converterParaDoseDto(entity, concluido, params.agoraMinutos)
                }
                .let { lista ->
                    if (params.filtro == FiltroMedicacao.SOMENTE_ATRASADOS) {
                        lista.filter {
                            !it.isAdministradoHoje && "ATRASADO".equals(it.statusPontualidade, ignoreCase = true)
                        }
                    } else if (params.filtro != FiltroMedicacao.TODOS_DO_DIA) {
                        lista.filter {
                            !it.isAdministradoHoje && !"CONCLUIDO".equals(it.statusPontualidade, ignoreCase = true)
                        }
                    } else {
                        lista
                    }
                }
                .sortedWith(COMPARATOR_DOSES)

            // Contagem global do dia (usando todos os ativos sem duplicidade)
            val todosUnicos = todosAtivos.distinctBy { it.id }
            var totalPendentes = 0
            var totalAtrasados = 0
            var totalConcluidos = 0

            for (entity in todosUnicos) {
                val concluido = !ehRegistroPendente(entity, idsConcluidosHoje, params.dataHoje)
                if (concluido) {
                    totalConcluidos++
                } else {
                    totalPendentes++
                    if (calcularStatusPontualidade(entity.horarioInicial, false, params.agoraMinutos) == "ATRASADO") {
                        totalAtrasados++
                    }
                }
            }

            MedicamentosUiState.Sucesso(
                doses = dosesMapeadas,
                totalPendentes = totalPendentes,
                totalAtrasados = totalAtrasados,
                totalConcluidos = totalConcluidos,
                filtroAtivo = params.filtro,
                idosoIdSelecionado = params.idosoId,
                snapshotVersion = versaoSnapshot
            )
        }
    }

    @JvmOverloads
    fun aplicarFiltro(
        filtro: FiltroMedicacao,
        idosoId: Int = -1,
        dataHoje: String = dataAtualFormatada(),
        agoraMinutos: Int = minutosAtuaisDoDia()
    ) {
        parametrosFiltro.value = FiltroParametros(
            filtro = filtro,
            idosoId = idosoId,
            dataHoje = dataHoje,
            agoraMinutos = agoraMinutos
        )
    }

    fun atualizarRelogioReferencia(dataHoje: String, agoraMinutos: Int) {
        val atual = parametrosFiltro.value
        parametrosFiltro.value = atual.copy(
            dataHoje = dataHoje,
            agoraMinutos = agoraMinutos
        )
    }

    @JvmOverloads
    fun sincronizarDados(
        idosos: List<Idoso>,
        medicamentos: List<Medicamento>,
        historicosHoje: List<HistoricoMedicacao>,
        dataHoje: String = dataAtualFormatada(),
        versaoSnapshot: Long = versaoSnapshotSincronizada.value + 1L
    ) {
        viewModelScope.launch {
            writeMutex.withLock {
                repository.resetarPendenciasParaNovoDia(dataHoje)
                repository.sincronizarMedicamentosComSnapshot(idosos, medicamentos, historicosHoje, dataHoje)
                if (versaoSnapshot > versaoSnapshotSincronizada.value) {
                    versaoSnapshotSincronizada.value = versaoSnapshot
                }
            }
        }
    }

    @JvmOverloads
    fun registrarBaixaDose(
        doseDTO: DoseAlertaDTO,
        nomeCuidador: String,
        observacao: String,
        novoEstoque: String = "",
        dataHoje: String = dataAtualFormatada(),
        dataHoraExecutada: String = dataHoraAtualFormatada()
    ) {
        val med = doseDTO.medicamento ?: return
        val statusExecucao = if ("ATRASADO".equals(doseDTO.statusPontualidade, ignoreCase = true)) {
            HistoricoMedicacao.STATUS_ATRASADO
        } else {
            HistoricoMedicacao.STATUS_ADMINISTRADO
        }

        viewModelScope.launch {
            writeMutex.withLock {
                repository.registrarBaixaDose(
                    medicamentoId = med.id.toLong(),
                    idosoId = med.idIdoso.toLong(),
                    horarioPrevisto = doseDTO.horarioPrevisto.orEmpty(),
                    dataHoje = dataHoje,
                    dataHoraExecutada = dataHoraExecutada,
                    statusHistorico = statusExecucao,
                    nomeCuidador = nomeCuidador,
                    observacao = observacao,
                    novoEstoque = novoEstoque
                )
            }
        }
    }

    fun observarNoLifecycle(lifecycleOwner: LifecycleOwner, observer: JavaUiStateObserver) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiState.collect { state ->
                    observer.onChanged(state)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MedicamentosViewModel::class.java)) {
                        return MedicamentosViewModel(IlpiRepository.create(application)) as T
                    }
                    throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
                }
            }
        }

        /**
         * Regra pura e reutilizável de filtragem de doses pendentes/atrasadas:
         * - Elimina qualquer duplicidade por ID de medicamento;
         * - Remove registros concluídos/administrados/recusados para os modos pendentes e atrasados;
         * - Ordena com prioridade absoluta para doses atrasadas e depois por horário previsto.
         */
        @JvmStatic
        fun filtrarDosesPendentes(
            doses: List<DoseAlertaDTO>,
            filtro: FiltroMedicacao,
            idosoId: Int
        ): List<DoseAlertaDTO> {
            val unicosPorId = LinkedHashMap<Int, DoseAlertaDTO>()
            for (dto in doses) {
                val medId = dto.idMedicamento
                if (medId <= 0) continue
                val existente = unicosPorId[medId]
                if (existente == null || (!existente.isAdministradoHoje && dto.isAdministradoHoje)) {
                    unicosPorId[medId] = dto
                }
            }

            return unicosPorId.values
                .filter { dto ->
                    val med = dto.medicamento ?: return@filter false
                    val bateIdoso = idosoId <= 0 || med.idIdoso == idosoId
                    if (!bateIdoso) return@filter false

                    val pendente = !dto.isAdministradoHoje &&
                        !"CONCLUIDO".equals(dto.statusPontualidade, ignoreCase = true)

                    when (filtro) {
                        FiltroMedicacao.SOMENTE_ATRASADOS ->
                            pendente && "ATRASADO".equals(dto.statusPontualidade, ignoreCase = true)
                        FiltroMedicacao.SOMENTE_PENDENTES,
                        FiltroMedicacao.POR_IDOSO -> pendente
                        FiltroMedicacao.TODOS_DO_DIA -> true
                    }
                }
                .sortedWith(COMPARATOR_DOSES)
        }

        private fun extrairIdsConcluidosNoDia(
            medicamentos: List<MedicamentoEntity>,
            historicoHoje: List<HistoricoMedicacaoEntity>,
            dataHoje: String
        ): Set<Long> {
            val ids = HashSet<Long>()
            for (h in historicoHoje) {
                val st = h.status.uppercase(Locale.ROOT)
                if (h.dataReferencia == dataHoje && (
                        st == HistoricoMedicacao.STATUS_ADMINISTRADO ||
                            st == HistoricoMedicacao.STATUS_ATRASADO ||
                            st == HistoricoMedicacao.STATUS_RECUSADO ||
                            st == "CONCLUIDO"
                        )
                ) {
                    ids.add(h.medicamentoId)
                }
            }
            for (m in medicamentos) {
                if (!m.pendente && m.ultimaAdministracaoData == dataHoje) {
                    ids.add(m.id)
                }
            }
            return ids
        }

        private fun ehRegistroPendente(
            entity: MedicamentoEntity,
            idsConcluidosHoje: Set<Long>,
            dataHoje: String
        ): Boolean {
            if (!entity.statusAtivo) return false
            if (!entity.pendente) return false
            if (entity.ultimaAdministracaoData == dataHoje) return false
            val st = entity.ultimoStatusHistorico.uppercase(Locale.ROOT)
            if (st == "ADMINISTRADO" || st == "CONCLUIDO" || st == "RECUSADO") return false
            return !idsConcluidosHoje.contains(entity.id)
        }

        private fun converterParaDoseDto(
            entity: MedicamentoEntity,
            concluido: Boolean,
            agoraMinutos: Int
        ): DoseAlertaDTO {
            val status = calcularStatusPontualidade(entity.horarioInicial, concluido, agoraMinutos)
            return DoseAlertaDTO(entity.toModel(), entity.horarioInicial, status, concluido)
        }

        @JvmStatic
        fun calcularStatusPontualidade(
            horarioPrevisto: String,
            concluido: Boolean,
            agoraMinutos: Int
        ): String {
            if (concluido) return "CONCLUIDO"
            val minutosPrevistos = extrairMinutos(horarioPrevisto)
            return if (agoraMinutos > minutosPrevistos + 15) "ATRASADO" else "NO_HORARIO"
        }

        @JvmStatic
        fun extrairMinutos(horario: String): Int {
            return try {
                val partes = horario.trim().split(":")
                val h = partes[0].trim().toInt()
                val m = partes[1].trim().toInt()
                (h * 60) + m
            } catch (_: Exception) {
                0
            }
        }

        @JvmStatic
        fun dataAtualFormatada(): String =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        @JvmStatic
        fun dataHoraAtualFormatada(): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        @JvmStatic
        fun minutosAtuaisDoDia(): Int {
            val cal = Calendar.getInstance()
            return (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
        }

        private val COMPARATOR_DOSES = Comparator<DoseAlertaDTO> { d1, d2 ->
            val p1 = prioridadeStatus(d1.statusPontualidade)
            val p2 = prioridadeStatus(d2.statusPontualidade)
            if (p1 != p2) return@Comparator p1.compareTo(p2)

            val m1 = extrairMinutos(d1.horarioPrevisto.orEmpty())
            val m2 = extrairMinutos(d2.horarioPrevisto.orEmpty())
            if (m1 != m2) return@Comparator m1.compareTo(m2)

            val n1 = d1.nomeIdoso.orEmpty()
            val n2 = d2.nomeIdoso.orEmpty()
            val cmpNome = n1.compareTo(n2, ignoreCase = true)
            if (cmpNome != 0) return@Comparator cmpNome

            d1.idMedicamento.compareTo(d2.idMedicamento)
        }

        private fun prioridadeStatus(status: String?): Int {
            return when {
                "ATRASADO".equals(status, ignoreCase = true) -> 0
                "NO_HORARIO".equals(status, ignoreCase = true) -> 1
                else -> 2
            }
        }
    }
}
