package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IlpiRoomDatabase
import com.example.data.local.entity.IdosoEntity
import com.example.data.local.entity.RegistroMedicoEntity
import com.example.data.repository.IlpiRepository
import com.example.database.DatabaseHelper
import com.example.database.IdosoDAO
import com.example.database.MedicamentoDAO
import com.example.database.PassagemTurnoDAO
import com.example.model.Idoso
import com.example.model.Medicamento
import com.example.model.PassagemTurno
import com.example.model.PerfilUsuario
import com.example.model.Usuario
import com.example.receiver.BootReceiver
import com.example.receiver.MedicamentoReceiver
import com.example.ui.viewmodel.MedicamentosViewModel
import com.example.util.DashboardManager
import com.example.util.EstoqueAlertaManager
import com.example.util.ImageUtil
import com.example.util.NotificationHelper
import com.example.util.ResumoProntuarioPdfGenerator
import com.example.util.SessaoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Auditoria completa de persistência em disco e sobrevivência ao desligamento/reinício do aparelho
 * simulando uma jornada real de uso diário da ILPI Vovô Lar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowNativePdfDocument::class])
class AuditoriaPersistenciaDispositivoTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        limparEstadoCompletoDiscoEMemoria()
    }

    @After
    fun tearDown() {
        limparEstadoCompletoDiscoEMemoria()
    }

    private fun limparEstadoCompletoDiscoEMemoria() {
        DatabaseHelper.resetInstanceForTesting()
        IlpiRoomDatabase.resetInstanceForTesting()
        SessaoManager.resetInstanceForTesting()

        context.deleteDatabase(DatabaseHelper.DATABASE_NAME)
        context.deleteDatabase(IlpiRoomDatabase.DATABASE_NAME)

        context.getSharedPreferences(SessaoManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(EstoqueAlertaManager.PREFS_ESTOQUE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("CuidarPlusDashboardPrefs", Context.MODE_PRIVATE).edit().clear().commit()

        ImageUtil.obterDiretorioFotosPrivado(context).deleteRecursively()
        ResumoProntuarioPdfGenerator.obterDiretorioResumosOffline(context).deleteRecursively()
    }

    @Test
    fun auditoriaCompleta_registrosDevemPersistirAposDesligamentoEReinicioSimulado() = runBlocking {
        // =========================================================================
        // FASE 1: USO DIÁRIO NO DISPOSITIVO (CRIAÇÃO, EDIÇÃO, BAIXA, PDF, TURNO)
        // =========================================================================

        // 1.1 Sessão segura, credenciais offline e perfil ativo
        val sessaoInicial = SessaoManager.getInstance(context)
        val usuarioPlantao = Usuario(77L, "Enf. Marina Soares", "marina@vovolar.org", PerfilUsuario.ADMINISTRADOR)
        sessaoInicial.salvarSessao(usuarioPlantao, "Pin@2026")
        assertTrue(sessaoInicial.validarCredenciaisOffline("marina@vovolar.org", "Pin@2026"))

        // 1.2 Configurações gerenciais (Capacidade de leitos e Limite de estoque)
        assertTrue(DashboardManager.salvarCapacidadeTotalVagas(context, 32))
        assertTrue(EstoqueAlertaManager.salvarLimiteMinimoEstoque(context, 12))

        // 1.3 Cadastro de novo residente + foto privada em getFilesDir()
        val idosoDAO = IdosoDAO(context)
        val bitmapFoto = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val caminhoFotoSalva = ImageUtil.salvarBitmapNoArmazenamentoPrivado(context, bitmapFoto)
        assertNotNull(caminhoFotoSalva)
        assertTrue(ImageUtil.possuiFotoValida(caminhoFotoSalva))

        val novoIdoso = Idoso(
            "Dona Beatriz Vasconcelos",
            "12/06/1940",
            "O-",
            "Dipirona",
            "Cardiopatia moderada; dieta branda",
            "Filho Lucas - (51) 99888-1122",
            caminhoFotoSalva
        ).apply {
            cpf = "555.444.333-22"
            leitoQuarto = "Quarto 08 - Leito B"
            nomeFamiliar = "Lucas Vasconcelos"
            grauParentesco = "Filho"
        }
        val novoIdosoId = idosoDAO.inserir(novoIdoso).toInt()
        assertTrue(novoIdosoId > 0)
        novoIdoso.id = novoIdosoId

        // Edição de residente existente preservando campos auxiliares (CPF / leito)
        val franciscaAntes = idosoDAO.buscarPorId(1)
        assertNotNull(franciscaAntes)
        franciscaAntes!!.restricoesMedicas = "Hipertensão controlada e fisioterapia diária"
        assertEquals(1, idosoDAO.atualizar(franciscaAntes))

        // 1.4 Prescrição de medicamento + disparo via ação de notificação (baixa rápida)
        val medDAO = MedicamentoDAO(context)
        val novoMed = Medicamento().apply {
            idIdoso = novoIdosoId
            nomeMedicamento = "Omeprazol"
            dosagem = "20mg (1 cápsula)"
            estoque = "6 cápsulas" // Abaixo do limite 12 -> gera alerta crítico
            tipo = "Comprimido"
            horarioInicial = "06:00"
            frequenciaHoras = 24
            observacaoMedica = "Em jejum antes do café"
            statusAtivo = 1
        }
        val novoMedId = medDAO.inserir(novoMed).toInt()
        assertTrue(novoMedId > 0)

        // Simula o usuário tocando "Já Administrei" na notificação enquanto o app está em segundo plano
        val intentNotifBaixa = Intent(context, MedicamentoReceiver::class.java).apply {
            action = MedicamentoReceiver.ACTION_MARCAR_ADMINISTRADO
            putExtra(MedicamentoReceiver.EXTRA_MED_ID, novoMedId)
            putExtra(MedicamentoReceiver.EXTRA_IDOSO_ID, novoIdosoId)
            putExtra(MedicamentoReceiver.EXTRA_NOME_MED, "Omeprazol")
        }
        MedicamentoReceiver().onReceive(context, intentNotifBaixa)

        // Verifica desconto imediato no estoque e histórico gravado
        val medAposBaixa = medDAO.buscarPorId(novoMedId)
        assertNotNull(medAposBaixa)
        assertEquals("5 cápsulas", medAposBaixa!!.estoque)

        // 1.5 Registro Clínico persistido diretamente no Room Database (cuidarplus_ilpi_room.db)
        val repoAntes = IlpiRepository.create(context)
        repoAntes.inserirIdoso(IdosoEntity.fromModel(novoIdoso))
        val idRegClinico = repoAntes.inserirRegistroMedico(
            RegistroMedicoEntity(
                idosoId = novoIdosoId.toLong(),
                tipoRegistro = RegistroMedicoEntity.TIPO_SINAIS_VITAIS,
                titulo = "Aferição Vespertina Persistente",
                descricaoClinica = "Residente estável, hidratada e sem queixas.",
                pressaoArterial = "125/80 mmHg",
                temperaturaCelsius = 36.4,
                glicemiaMgDl = 96,
                saturacaoO2 = 98,
                profissionalResponsavel = "Enf. Marina Soares",
                crmCoren = "COREN-RS 998877",
                alertaCritico = false
            )
        )
        assertTrue(idRegClinico > 0L)

        // 1.6 Registro e confirmação de Passagem de Turno
        val passagemDAO = PassagemTurnoDAO(context)
        val novaPassagem = PassagemTurno().apply {
            turno = PassagemTurno.TURNO_TARDE
            profissionalOrigem = "Enf. Marina Soares"
            cargoOrigem = "Administrador(a)"
            idIdoso = novoIdosoId
            nomeIdoso = "Dona Beatriz Vasconcelos"
            prioridade = PassagemTurno.PRIORIDADE_ATENCAO
            observacoes = "Aceitou bem a dieta branda no almoço."
            intercorrencias = "Nenhuma intercorrência."
            pendenciasProximoTurno = "Aferir PA às 20h."
            isConferido = false
        }
        val passagemId = passagemDAO.inserir(novaPassagem).toInt()
        assertTrue(passagemId > 0)
        assertEquals(1, passagemDAO.confirmarRecebimento(passagemId, "Enf. Lucas Plantão"))

        // 1.7 Geração e gravação do Resumo do Prontuário em PDF offline
        val pdfOffline = ResumoProntuarioPdfGenerator.gerarESalvarResumoOffline(
            context,
            novoIdoso,
            medDAO.listarPorIdoso(novoIdosoId)
        )
        assertTrue(pdfOffline.exists())
        assertTrue(pdfOffline.length() > 0L)

        // =========================================================================
        // FASE 2: SIMULAÇÃO DE DESLIGAMENTO E REINÍCIO DO APARELHO
        // =========================================================================

        // Fecha instâncias em memória (sem apagar nenhum arquivo em disco)
        DatabaseHelper.resetInstanceForTesting()
        IlpiRoomDatabase.resetInstanceForTesting()
        SessaoManager.resetInstanceForTesting()

        // Simula o comportamento real do Android ao desligar:
        // alarmes em RAM do AlarmManager e notificações visíveis são descartados até o BootReceiver rodar.
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)
        shadowAlarmManager.scheduledAlarms.toList().forEach { scheduled ->
            scheduled.operation?.let { alarmManager.cancel(it) }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)
        notificationManager.cancelAll()
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(0, shadowNotificationManager.allNotifications.size)

        // Dispara o BootReceiver simulando o religamento do aparelho (ACTION_BOOT_COMPLETED)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        // =========================================================================
        // FASE 3: VERIFICAÇÃO INTEGRAL PÓS-REINÍCIO (ZERO PERDA DE DADOS)
        // =========================================================================

        // 3.1 Sessão criptografada, usuário, perfil e credencial offline sobrevivem
        val sessaoPosBoot = SessaoManager.getInstance(context)
        assertTrue(sessaoPosBoot.isSessaoValida)
        assertEquals("Enf. Marina Soares", sessaoPosBoot.nomeUsuario)
        assertEquals("marina@vovolar.org", sessaoPosBoot.emailUsuario)
        assertEquals(PerfilUsuario.ADMINISTRADOR, sessaoPosBoot.perfilAtivo)
        assertTrue(sessaoPosBoot.validarCredenciaisOffline("marina@vovolar.org", "Pin@2026"))

        // 3.2 Preferências gerenciais sobrevivem
        assertEquals(32, DashboardManager.getCapacidadeTotalVagas(context))
        assertEquals(12, EstoqueAlertaManager.getLimiteMinimoEstoque(context))

        // 3.3 SQLite Idosos + foto interna + preservação de campos auxiliares sem duplicação
        val idosoDaoPosBoot = IdosoDAO(context)
        val residentesPosBoot = idosoDaoPosBoot.listarTodos()
        assertEquals(5, residentesPosBoot.size) // 4 iniciais + Dona Beatriz
        val beatrizPosBoot = idosoDaoPosBoot.buscarPorId(novoIdosoId)
        assertNotNull(beatrizPosBoot)
        assertEquals("Dona Beatriz Vasconcelos", beatrizPosBoot!!.nome)
        assertEquals("Quarto 08 - Leito B", beatrizPosBoot.leitoQuarto)
        assertEquals("555.444.333-22", beatrizPosBoot.cpf)
        assertTrue(ImageUtil.possuiFotoValida(beatrizPosBoot.caminhoFoto))
        assertTrue(File(beatrizPosBoot.caminhoFoto).exists())

        val franciscaPosBoot = idosoDaoPosBoot.buscarPorId(1)
        assertNotNull(franciscaPosBoot)
        assertEquals("Hipertensão controlada e fisioterapia diária", franciscaPosBoot!!.restricoesMedicas)
        assertEquals("184.520.390-15", franciscaPosBoot.cpf)

        // 3.4 Medicamentos, estoque decrementado, baixa do dia e alarmes restaurados no boot
        val dbHelperPosBoot = DatabaseHelper.getInstance(context)
        val medPosBoot = dbHelperPosBoot.buscarMedicamentoPorId(novoMedId)
        assertNotNull(medPosBoot)
        assertEquals("5 cápsulas", medPosBoot!!.estoque)

        val hojeStr = MedicamentosViewModel.dataAtualFormatada()
        val historicoHojePosBoot = dbHelperPosBoot.listarHistoricoDoDia(hojeStr)
        assertTrue(historicoHojePosBoot.any { it.idMedicamento == novoMedId && it.nomeCuidador == "Enf. Marina Soares" })

        val pendentesPosBoot = dbHelperPosBoot.buscarDosesPendentes(-1)
        assertFalse(
            "Dose já administrada antes do desligamento não deve reaparecer como pendente após boot",
            pendentesPosBoot.any { it.idMedicamento == novoMedId }
        )

        // Alarmes restaurados no AlarmManager + notificação de estoque baixo restaurada no BootReceiver
        assertTrue(
            "BootReceiver deve restaurar os alarmes dos medicamentos ativos após religar o aparelho",
            shadowAlarmManager.scheduledAlarms.size >= 4
        )
        assertNotNull(
            "BootReceiver deve reexibir a notificação de estoque crítico após religar o aparelho",
            shadowNotificationManager.getNotification(NotificationHelper.NOTIF_ID_ESTOQUE_BAIXO)
        )

        // 3.5 Room Database (cuidarplus_ilpi_room.db) preserva evoluções clínicas
        val repoPosBoot = IlpiRepository.create(context)
        val registrosPosBoot = repoPosBoot.observarRegistrosMedicosDoIdoso(novoIdosoId.toLong()).first()
        assertEquals(1, registrosPosBoot.size)
        assertEquals("Aferição Vespertina Persistente", registrosPosBoot.first().titulo)

        // 3.6 Passagem de Turno preserva registro e conferência
        val passagensPosBoot = PassagemTurnoDAO(context).listarTodas()
        val passagemEncontrada = passagensPosBoot.firstOrNull { it.id == passagemId }
        assertNotNull(passagemEncontrada)
        assertTrue(passagemEncontrada!!.isConferido)
        assertEquals("Enf. Lucas Plantão", passagemEncontrada.profissionalDestino)

        // 3.7 PDF offline continua acessível após reinício
        val pdfRecuperado = ResumoProntuarioPdfGenerator.obterResumoSalvoOffline(context, beatrizPosBoot)
        assertNotNull(pdfRecuperado)
        assertTrue(pdfRecuperado!!.exists())
        assertTrue(pdfRecuperado.length() > 0L)

        // 3.8 Reabertura das telas principais após o reinício exibe os dados reais salvos
        Robolectric.buildActivity(MainActivity::class.java).setup().use { ctrl ->
            val tvContador = ctrl.get().findViewById<TextView>(R.id.tv_contador_dashboard)
            assertTrue(tvContador.text.toString().contains("5"))
        }

        Robolectric.buildActivity(DashboardActivity::class.java).setup().use { ctrl ->
            val act = ctrl.get()
            assertEquals("5", act.findViewById<TextView>(R.id.tv_valor_total_residentes).text.toString())
            assertTrue(act.findViewById<TextView>(R.id.tv_capacidade_total_vagas).text.toString().contains("32"))
        }

        Robolectric.buildActivity(EstoqueActivity::class.java).setup().use { ctrl ->
            val tvTotal = ctrl.get().findViewById<TextView>(R.id.tv_resumo_total_itens)
            assertTrue(tvTotal.text.toString().contains("4"))
        }

        Robolectric.buildActivity(PassagemTurnoActivity::class.java).setup().use { ctrl ->
            val tvSub = ctrl.get().findViewById<TextView>(R.id.tv_resumo_passagem_subtitulo)
            assertTrue(tvSub.text.toString().contains("3"))
        }
    }
}
