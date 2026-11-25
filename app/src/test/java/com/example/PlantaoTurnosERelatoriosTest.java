package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.database.PassagemTurnoDAO;
import com.example.model.DoseAlertaDTO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.EstoqueAlertaManager;
import com.example.util.NotificationHelper;
import com.example.util.PdfReportGenerator;
import com.example.util.PerfilManager;
import com.example.util.SessaoManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNotificationManager;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, shadows = {ShadowNativePdfDocument.class})
public class PlantaoTurnosERelatoriosTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        context.getSharedPreferences(EstoqueAlertaManager.PREFS_ESTOQUE, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Enf. Camila Mendes", "camila@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@2026"
        );
        PerfilManager.setPerfil(context, MainActivity.PERFIL_ADMIN);
        System.setProperty("robolectric.offline", "true");
    }

    @After
    public void tearDown() {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.checkActivities(false);
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void plantaoGeralDevePriorizarAtrasadosEMarcarBaixaComoConcluido() {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        IdosoDAO idosoDAO = new IdosoDAO(context);

        // Limpa medicamentos iniciais e cria cenário controlado com 2 residentes
        for (Medicamento m : db.listarTodosMedicamentos()) {
            db.excluirMedicamento(m.getId());
        }
        List<Idoso> idosos = idosoDAO.listarTodos();
        assertTrue(idosos.size() >= 2);

        Idoso primeiroAlfabetico = idosos.get(0); // ex: Dona Francisca
        Idoso segundoResidente = idosos.get(1);   // ex: Seu Antônio

        // Primeiro residente tem dose no futuro ("23:59" -> NO_HORARIO)
        Medicamento medFuturo = new Medicamento(
                primeiroAlfabetico.getId(),
                "Losartana 50mg",
                "1 comprimido",
                "Comprimido",
                "23:59",
                24,
                "Pressão",
                1
        );
        medFuturo.setEstoque("20 comprimidos");
        db.cadastrarMedicamento(medFuturo);

        // Segundo residente tem dose bem mais cedo ("08:00" -> ATRASADO se relógio >= 08:16)
        Calendar cal = Calendar.getInstance();
        int horaAtrasada = Math.max(0, cal.get(Calendar.HOUR_OF_DAY) - 2);
        String horarioAtrasado = String.format("%02d:00", horaAtrasada);
        if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) <= 15) {
            // Garantia caso teste rode exatamente entre 00:00 e 00:15
            horarioAtrasado = "00:00";
        }

        Medicamento medAtrasado = new Medicamento(
                segundoResidente.getId(),
                "Prolopa 250mg",
                "1 comprimido",
                "Comprimido",
                horarioAtrasado,
                12,
                "Parkinson",
                1
        );
        medAtrasado.setEstoque("15 comprimidos");
        long idAtrasado = db.cadastrarMedicamento(medAtrasado);
        assertTrue(idAtrasado > 0);

        // Abre MedicamentoActivity SEM EXTRA_IDOSO_ID -> modo Plantão Geral
        try (ActivityController<MedicamentoActivity> controller =
                     Robolectric.buildActivity(MedicamentoActivity.class).setup()) {
            MedicamentoActivity activity = controller.get();
            RecyclerView rv = activity.findViewById(R.id.rvMedicamentos);
            rv.measure(
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            );
            rv.layout(0, 0, 480, 800);

            assertEquals("Deve listar doses de todos os residentes no Plantão Geral", 2, rv.getAdapter().getItemCount());

            View primeiroCard = rv.getChildAt(0);
            assertNotNull(primeiroCard);
            TextView tvNomeMed = primeiroCard.findViewById(R.id.tv_nome_medicamento);
            TextView tvResidente = primeiroCard.findViewById(R.id.tv_nome_residente_dose);
            TextView tvBadge = primeiroCard.findViewById(R.id.tv_badge_status);
            MaterialButton btnBaixa = primeiroCard.findViewById(R.id.btn_dar_baixa_agora);

            // Se o relógio do sistema passou de horarioAtrasado + 15min, o atrasado deve estar no topo
            List<DoseAlertaDTO> alertas = db.buscarAlertasDoDia();
            assertEquals("Prolopa 250mg", alertas.get(0).getNomeMedicamento());
            assertEquals("Prolopa 250mg", tvNomeMed.getText().toString());
            assertTrue(tvResidente.getText().toString().contains(segundoResidente.getNome()));

            if ("ATRASADO".equals(alertas.get(0).getStatusPontualidade())) {
                assertEquals("Atrasado", tvBadge.getText().toString());
            }

            // Dá baixa na dose atrasada
            btnBaixa.performClick();
            ShadowLooper.idleMainLooper();

            AlertDialog confirmDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(confirmDialog);
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            // Na tela operacional de pendentes, apenas a dose ainda pendente (Losartana 50mg) deve permanecer
            assertEquals("Registro já administrado deve desaparecer da lista de pendentes", 1, rv.getAdapter().getItemCount());
            rv.measure(
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            );
            rv.layout(0, 0, 480, 800);
            TextView tvRestante = rv.getChildAt(0).findViewById(R.id.tv_nome_medicamento);
            assertEquals("Losartana 50mg", tvRestante.getText().toString());
            assertTrue("Nenhuma dose atrasada pendente deve restar", db.buscarDosesAtrasadasPendentes().isEmpty());

            // Após dar baixa, a dose DEVE sair de ATRASADO para CONCLUIDO
            List<DoseAlertaDTO> aposBaixa = db.buscarAlertasDoDia();
            DoseAlertaDTO prolopaApos = null;
            for (DoseAlertaDTO d : aposBaixa) {
                if (d.getIdMedicamento() == (int) idAtrasado) {
                    prolopaApos = d;
                    break;
                }
            }
            assertNotNull(prolopaApos);
            assertTrue("Dose atrasada que recebeu baixa deve estar marcada como administrada hoje",
                    prolopaApos.isAdministradoHoje());
            assertEquals("Status de pontualidade deve transicionar para CONCLUIDO após baixa",
                    "CONCLUIDO", prolopaApos.getStatusPontualidade());

            // E o estoque deve ter decrementado de 15 para 14
            Medicamento atualizado = db.buscarMedicamentoPorId((int) idAtrasado);
            assertEquals("14 comprimidos", atualizado.getEstoque());
        }
    }

    @Test
    public void notificacaoDeSistemaParaEstoqueBaixoDeveDispararECancelarAoNormalizar() {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNm = Shadows.shadowOf(nm);

        // No banco inicial há item crítico (Prolopa com 4 unidades < 10)
        EstoqueAlertaManager.ResumoAlertaEstoque resumoCritico =
                NotificationHelper.avaliarENotificarEstoqueBaixo(context);
        assertTrue(resumoCritico.possuiAlertaCritico());

        Notification notif = shadowNm.getNotification(NotificationHelper.NOTIF_ID_ESTOQUE_BAIXO);
        assertNotNull("Notificação nativa de estoque baixo deve ser publicada no NotificationManager", notif);
        assertEquals(NotificationHelper.CANAL_ESTOQUE_ID, notif.getChannelId());

        // Ajusta limite mínimo para 2 unidades -> nenhum item fica abaixo de 2 -> notificação deve ser cancelada
        EstoqueAlertaManager.salvarLimiteMinimoEstoque(context, 2);
        EstoqueAlertaManager.ResumoAlertaEstoque resumoNormal =
                NotificationHelper.avaliarENotificarEstoqueBaixo(context);
        assertFalse(resumoNormal.possuiAlertaCritico());
        assertNull("Notificação de estoque deve ser cancelada quando todos os itens estão acima do mínimo",
                shadowNm.getNotification(NotificationHelper.NOTIF_ID_ESTOQUE_BAIXO));
    }

    @Test
    public void passagemDeTurnoDevePersistirNoBancoEConsolidarConferenciaNaActivity() {
        PassagemTurnoDAO dao = new PassagemTurnoDAO(context);
        List<PassagemTurno> iniciais = dao.listarTodas();
        assertTrue("Seeds iniciais de passagem de turno devem ser criados na v6", iniciais.size() >= 2);
        assertTrue(dao.contarPendentesConferencia() >= 1);

        try (ActivityController<PassagemTurnoActivity> controller =
                     Robolectric.buildActivity(PassagemTurnoActivity.class).setup()) {
            PassagemTurnoActivity activity = controller.get();

            // Registra nova passagem via FAB e diálogo
            activity.findViewById(R.id.fab_nova_passagem_turno).performClick();
            ShadowLooper.idleMainLooper();

            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);
            AutoCompleteTextView actvTurno = dialog.findViewById(R.id.actv_turno_plantao);
            TextInputEditText etProf = dialog.findViewById(R.id.et_profissional_passagem);
            TextInputEditText etObs = dialog.findViewById(R.id.et_observacoes_passagem);
            TextInputEditText etPend = dialog.findViewById(R.id.et_pendencias_passagem);

            actvTurno.setText(PassagemTurno.TURNO_NOITE, false);
            etProf.setText("Téc. Marcos Silva");
            etObs.setText("Residente aceitou bem a ceia e permaneceu em repouso estável.");
            etPend.setText("Aferir pressão arterial às 07:00.");

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            List<PassagemTurno> aposCriacao = dao.listarTodas();
            assertEquals(iniciais.size() + 1, aposCriacao.size());

            // Confirma recebimento no primeiro card pendente da lista
            RecyclerView rv = activity.findViewById(R.id.rv_passagem_turno);
            rv.measure(
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            );
            rv.layout(0, 0, 480, 800);

            int pendentesAntes = dao.contarPendentesConferencia();
            View primeiroCard = rv.getChildAt(0);
            assertNotNull(primeiroCard);
            MaterialButton btnConfirmar = primeiroCard.findViewById(R.id.btn_confirmar_recebimento);
            assertEquals(View.VISIBLE, btnConfirmar.getVisibility());
            btnConfirmar.performClick();
            ShadowLooper.idleMainLooper();

            assertEquals(pendentesAntes - 1, dao.contarPendentesConferencia());
        }
    }

    @Test
    public void upgradeBancoParaVersao6DeveCriarPassagemTurnoSemPerderDados() {
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.getParentFile().mkdirs();
        }
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacy.execSQL("CREATE TABLE idosos (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL);");
            legacy.execSQL("INSERT INTO idosos (nome) VALUES ('Dona Francisca');");
            legacy.setVersion(5);
        }

        DatabaseHelper.resetInstanceForTesting();
        PassagemTurnoDAO ptDao = new PassagemTurnoDAO(context);
        List<PassagemTurno> passagens = ptDao.listarTodas();
        assertTrue("Tabela passagem_turno deve ser criada automaticamente na migração para v6", passagens.size() >= 2);
        assertEquals(1, new IdosoDAO(context).contarTotalIdosos());
    }

    @Test
    public void dashboardERelatoriosPdfDevemExibirKpisEGerarArquivosValidos() throws Exception {
        // Valida geração dos 4 relatórios PDF offline
        IdosoDAO idosoDAO = new IdosoDAO(context);
        MedicamentoDAO medDAO = new MedicamentoDAO(context);
        PassagemTurnoDAO ptDAO = new PassagemTurnoDAO(context);
        DatabaseHelper db = DatabaseHelper.getInstance(context);

        List<Idoso> idosos = idosoDAO.listarTodos();
        List<Medicamento> meds = medDAO.listarTodosMedicamentos();
        List<DoseAlertaDTO> doses = db.buscarAlertasDoDia();
        List<PassagemTurno> passagens = ptDAO.listarTodas();

        File pdfEstoque = PdfReportGenerator.gerarRelatorioEstoque(context, meds);
        File pdfDoses = PdfReportGenerator.gerarRelatorioAdministracaoDoses(context, doses);
        File pdfPassagem = PdfReportGenerator.gerarRelatorioPassagemTurno(context, passagens);
        File pdfGerencial = PdfReportGenerator.gerarRelatorioGerencialIlpi(context, idosos, meds, doses, passagens);

        assertPdfValido(pdfEstoque);
        assertPdfValido(pdfDoses);
        assertPdfValido(pdfPassagem);
        assertPdfValido(pdfGerencial);

        // Valida KPIs e ações no DashboardActivity
        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class).setup()) {
            DashboardActivity dash = controller.get();

            TextView tvCriticos = dash.findViewById(R.id.tv_kpi_estoque_critico_valor);
            TextView tvTurnos = dash.findViewById(R.id.tv_kpi_passagem_turno_valor);
            TextView tvMediaIdade = dash.findViewById(R.id.tv_clinico_media_idade);
            TextView tvPrescricoes = dash.findViewById(R.id.tv_clinico_prescricoes);

            assertEquals("1", tvCriticos.getText().toString());
            assertEquals("1", tvTurnos.getText().toString());
            assertTrue(tvMediaIdade.getText().toString().contains("a"));
            assertEquals(String.valueOf(doses.size()), tvPrescricoes.getText().toString());

            dash.findViewById(R.id.card_kpi_passagem_turno).performClick();
            Intent ptIntent = Shadows.shadowOf(dash).getNextStartedActivity();
            assertNotNull(ptIntent);
            assertEquals(PassagemTurnoActivity.class.getName(), ptIntent.getComponent().getClassName());

            dash.findViewById(R.id.btn_dashboard_relatorio_gerencial_pdf).performClick();
            Intent pdfIntent = Shadows.shadowOf(dash).getNextStartedActivity();
            assertNotNull(pdfIntent);
            assertEquals(VisualizadorPdfOfflineActivity.class.getName(), pdfIntent.getComponent().getClassName());
        }
    }

    private void assertPdfValido(File file) throws Exception {
        assertNotNull(file);
        assertTrue("Arquivo PDF deve existir: " + file.getAbsolutePath(), file.exists());
        assertTrue("Arquivo PDF deve ter conteúdo", file.length() > 20);
        byte[] header = new byte[5];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(header);
            assertEquals(5, read);
        }
        assertEquals("%PDF-", new String(header, StandardCharsets.US_ASCII));
    }
}
