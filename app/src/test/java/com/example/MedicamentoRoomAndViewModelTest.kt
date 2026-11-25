package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IlpiRoomDatabase
import com.example.data.local.entity.HistoricoMedicacaoEntity
import com.example.data.local.entity.IdosoEntity
import com.example.data.local.entity.MedicamentoEntity
import com.example.data.repository.IlpiRepository
import com.example.model.DoseAlertaDTO
import com.example.model.HistoricoMedicacao
import com.example.model.Medicamento
import com.example.ui.viewmodel.FiltroMedicacao
import com.example.ui.viewmodel.MedicamentosUiState
import com.example.ui.viewmodel.MedicamentosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MedicamentoRoomAndViewModelTest {

    private lateinit var context: Context
    private lateinit var db: IlpiRoomDatabase
    private lateinit var repository: IlpiRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        IlpiRoomDatabase.resetInstanceForTesting()
        db = Room.inMemoryDatabaseBuilder(context, IlpiRoomDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        repository = IlpiRepository(
            db.idosoDao(),
            db.registroMedicoDao(),
            db.medicamentoDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
        IlpiRoomDatabase.resetInstanceForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun `room dao deve filtrar estritamente atrasados pendentes sem duplicidade e excluir apos baixa`() = runTest(testDispatcher) {
        val idoso1 = repository.inserirIdoso(
            IdosoEntity(
                nome = "Dona Francisca",
                dataNascimento = "15/04/1945",
                tipoSanguineo = "O+"
            )
        )
        val idoso2 = repository.inserirIdoso(
            IdosoEntity(
                nome = "Seu Antônio",
                dataNascimento = "08/11/1939",
                tipoSanguineo = "A+"
            )
        )

        val medDao = db.medicamentoDao()
        val dataHoje = "24/11/2026"
        val agora14h = 14 * 60

        val idAtrasado = medDao.upsert(
            MedicamentoEntity(
                idosoId = idoso2,
                nomeIdoso = "Seu Antônio",
                nomeMedicamento = "Prolopa 250mg",
                dosagem = "1 comprimido",
                estoque = "10 comprimidos",
                horarioInicial = "08:00",
                pendente = true
            )
        )
        val idNoHorario = medDao.upsert(
            MedicamentoEntity(
                idosoId = idoso1,
                nomeIdoso = "Dona Francisca",
                nomeMedicamento = "Losartana 50mg",
                dosagem = "1 comprimido",
                estoque = "20 comprimidos",
                horarioInicial = "18:00",
                pendente = true
            )
        )
        // Mesmo com histórico de um dia anterior, o registro de hoje continua pendente e único
        medDao.inserirHistoricos(
            listOf(
                HistoricoMedicacaoEntity(
                    medicamentoId = idAtrasado,
                    idosoId = idoso2,
                    dataReferencia = "23/11/2026",
                    dataHoraPrescrita = "08:00",
                    dataHoraExecutada = "23/11/2026 08:10:00",
                    status = HistoricoMedicacao.STATUS_ADMINISTRADO
                ),
                HistoricoMedicacaoEntity(
                    medicamentoId = idAtrasado,
                    idosoId = idoso2,
                    dataReferencia = "22/11/2026",
                    dataHoraPrescrita = "08:00",
                    dataHoraExecutada = "22/11/2026 08:35:00",
                    status = HistoricoMedicacao.STATUS_ATRASADO
                )
            )
        )

        val atrasadosAntes = repository.observarMedicamentosAtrasadosPendentes(dataHoje, agora14h).first()
        assertEquals(1, atrasadosAntes.size)
        assertEquals("Prolopa 250mg", atrasadosAntes.first().nomeMedicamento)

        val pendentesAntes = repository.observarMedicamentosPendentes(dataHoje).first()
        assertEquals(2, pendentesAntes.size)
        assertTrue(pendentesAntes.any { it.id == idNoHorario })

        // Registra baixa da dose atrasada
        medDao.registrarBaixaDose(
            medicamentoId = idAtrasado,
            idosoId = idoso2,
            horarioPrevisto = "08:00",
            dataHoje = dataHoje,
            dataHoraExecutada = "24/11/2026 14:00:00",
            statusHistorico = HistoricoMedicacao.STATUS_ATRASADO,
            nomeCuidador = "Enf. Camila",
            observacao = "Administrado com atraso",
            novoEstoque = "9 comprimidos"
        )

        val atrasadosDepois = repository.observarMedicamentosAtrasadosPendentes(dataHoje, agora14h).first()
        assertTrue("Dose atrasada após baixa não pode continuar na lista de atrasados", atrasadosDepois.isEmpty())

        val pendentesDepois = repository.observarMedicamentosPendentes(dataHoje).first()
        assertEquals(1, pendentesDepois.size)
        assertEquals("Losartana 50mg", pendentesDepois.first().nomeMedicamento)

        // Novo dia deve restaurar pendência automaticamente
        medDao.resetarPendenciasParaNovoDia("25/11/2026")
        val pendentesDiaSeguinte = repository.observarMedicamentosPendentes("25/11/2026").first()
        assertEquals(2, pendentesDiaSeguinte.size)
    }

    @Test
    fun `viewmodel deve atualizar stateflow removendo atrasado apos baixa e deduplicar lista`() = runTest(testDispatcher) {
        val idoso1 = repository.inserirIdoso(
            IdosoEntity(nome = "Dona Francisca", dataNascimento = "15/04/1945", tipoSanguineo = "O+")
        )
        val idoso2 = repository.inserirIdoso(
            IdosoEntity(nome = "Seu Antônio", dataNascimento = "08/11/1939", tipoSanguineo = "A+")
        )

        val dataHoje = "24/11/2026"
        val medDao = db.medicamentoDao()
        val idAtrasado = medDao.upsert(
            MedicamentoEntity(
                idosoId = idoso2,
                nomeIdoso = "Seu Antônio",
                nomeMedicamento = "Prolopa 250mg",
                dosagem = "1 comp",
                estoque = "5 comprimidos",
                horarioInicial = "07:00",
                pendente = true
            )
        )
        medDao.upsert(
            MedicamentoEntity(
                idosoId = idoso1,
                nomeIdoso = "Dona Francisca",
                nomeMedicamento = "Metformina 850mg",
                dosagem = "1 comp",
                estoque = "20 comprimidos",
                horarioInicial = "20:00",
                pendente = true
            )
        )

        val viewModel = MedicamentosViewModel(repository)
        val collectJob = launch { viewModel.uiState.collect {} }

        viewModel.aplicarFiltro(FiltroMedicacao.SOMENTE_ATRASADOS, -1, dataHoje, 14 * 60)
        advanceUntilIdle()

        val estadoAtrasados = viewModel.uiState.value as MedicamentosUiState.Sucesso
        assertEquals(1, estadoAtrasados.doses.size)
        assertEquals("Prolopa 250mg", estadoAtrasados.doses.first().nomeMedicamento)
        assertEquals(2, estadoAtrasados.totalPendentes)
        assertEquals(1, estadoAtrasados.totalAtrasados)

        // Realiza baixa do medicamento atrasado pelo ViewModel
        viewModel.registrarBaixaDose(
            doseDTO = estadoAtrasados.doses.first(),
            nomeCuidador = "Enf. Camila",
            observacao = "Confirmado",
            novoEstoque = "4 comprimidos",
            dataHoje = dataHoje,
            dataHoraExecutada = "24/11/2026 14:05:00"
        )
        advanceUntilIdle()

        val estadoAposBaixa = viewModel.uiState.value as MedicamentosUiState.Sucesso
        assertTrue("Lista de atrasados deve ficar vazia após baixa", estadoAposBaixa.doses.isEmpty())
        assertEquals(0, estadoAposBaixa.totalAtrasados)
        assertEquals(1, estadoAposBaixa.totalPendentes)
        assertEquals(1, estadoAposBaixa.totalConcluidos)

        // Alterna para pendentes gerais: exibe apenas Metformina, sem Prolopa concluído
        viewModel.aplicarFiltro(FiltroMedicacao.SOMENTE_PENDENTES, -1, dataHoje, 14 * 60)
        advanceUntilIdle()
        val estadoPendentes = viewModel.uiState.value as MedicamentosUiState.Sucesso
        assertEquals(1, estadoPendentes.doses.size)
        assertEquals("Metformina 850mg", estadoPendentes.doses.first().nomeMedicamento)

        // Valida também deduplicação pura por ID em filtrarDosesPendentes
        val medDup = Medicamento(idAtrasado.toInt(), idoso2.toInt(), "Prolopa 250mg", "1 comp", "07:00", "")
        val medFut = Medicamento(99, idoso1.toInt(), "Metformina 850mg", "1 comp", "20:00", "")
        val filtrados = MedicamentosViewModel.filtrarDosesPendentes(
            listOf(
                DoseAlertaDTO(medDup, "07:00", "ATRASADO", false),
                DoseAlertaDTO(medDup, "07:00", "CONCLUIDO", true),
                DoseAlertaDTO(medFut, "20:00", "NO_HORARIO", false),
                DoseAlertaDTO(medFut, "20:00", "NO_HORARIO", false)
            ),
            FiltroMedicacao.SOMENTE_PENDENTES,
            -1
        )
        assertEquals(1, filtrados.size)
        assertEquals("Metformina 850mg", filtrados.first().nomeMedicamento)
        assertFalse(filtrados.first().isAdministradoHoje)

        collectJob.cancel()
    }
}
