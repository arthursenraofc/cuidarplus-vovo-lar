package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
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
import com.example.model.HistoricoMedicacao;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.EstoqueAlertaManager;
import com.example.util.PerfilManager;
import com.example.util.SessaoManager;
import com.example.util.ValidadeMedicamentoHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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

import java.io.File;
import java.util.List;

/**
 * Testes da Sprint Final para:
 * 1. Controle de Validade de Medicamentos (migração de banco, formulário opcional, badges laranja/vermelho).
 * 2. Passagem de Turno / Observações Diárias (Resumo Automático do Plantão 12h, turnos Diurno/Noturno,
 *    chips de intercorrências e compartilhamento para o WhatsApp da Equipe).
 * 3. Inicialização imediata da MainActivity já exibindo todo o Dashboard pronto.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, shadows = {ShadowNativePdfDocument.class})
public class ValidadeEPassagemTurnoFinalTest {

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
                new Usuario(1L, "Enf. Marina Soares", "marina@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@2026"
        );
        PerfilManager.setPerfil(context, MainActivity.PERFIL_ADMIN);
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
    public void migracaoDeBancoParaVersaoAtualDeveAdicionarDataValidadeSemPerderRegistros() {
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.getParentFile().mkdirs();
        }

        try (SQLiteDatabase legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacyDb.execSQL("CREATE TABLE idosos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nome TEXT NOT NULL);");
            legacyDb.execSQL("INSERT INTO idosos (id, nome) VALUES (1, 'Dona Francisca');");

            legacyDb.execSQL("CREATE TABLE medicamento ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "id_idoso INTEGER NOT NULL, "
                    + "nome_medicamento TEXT NOT NULL, "
                    + "dosagem TEXT NOT NULL, "
                    + "tipo TEXT NOT NULL DEFAULT 'Comprimido', "
                    + "horario_inicial TEXT NOT NULL, "
                    + "frequencia_horas INTEGER NOT NULL, "
                    + "observacao_medica TEXT, "
                    + "estoque TEXT DEFAULT '', "
                    + "status_ativo INTEGER DEFAULT 1);");
            legacyDb.execSQL("INSERT INTO medicamento (id_idoso, nome_medicamento, dosagem, tipo, horario_inicial, frequencia_horas, estoque, status_ativo) "
                    + "VALUES (1, 'Captopril 25mg', '1 comprimido', 'Comprimido', '08:00', 12, '18 un.', 1);");
            legacyDb.setVersion(4);
        }

        DatabaseHelper.resetInstanceForTesting();
        MedicamentoDAO medDao = new MedicamentoDAO(context);
        List<Medicamento> meds = medDao.listarTodosMedicamentos();
        assertEquals(1, meds.size());
        assertEquals("Captopril 25mg", meds.get(0).getNomeMedicamento());
        assertEquals("", meds.get(0).getDataValidade());

        String novaValidade = ValidadeMedicamentoHelper.gerarDataDeslocadaFormatada(12);
        meds.get(0).setDataValidade(novaValidade);
        assertEquals(1, medDao.atualizarMedicamento(meds.get(0)));

        Medicamento recarregado = DatabaseHelper.getInstance(context).buscarMedicamentoPorId(meds.get(0).getId());
        assertNotNull(recarregado);
        assertEquals(novaValidade, recarregado.getDataValidade());
    }

    @Test
    public void cadastroEEdicaoMedicamentoDevemValidarEPersistirDataValidadeOpcional() {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        List<Idoso> idosos = idosoDAO.listarTodos();
        assertFalse(idosos.isEmpty());
        Idoso residente = idosos.get(0);

        String validadeFutura = ValidadeMedicamentoHelper.gerarDataDeslocadaFormatada(20);

        Intent cadastroIntent = new Intent(context, CadastroMedicamentoActivity.class);
        cadastroIntent.putExtra(CadastroMedicamentoActivity.EXTRA_PRESELECTED_IDOSO_ID, residente.getId());

        try (ActivityController<CadastroMedicamentoActivity> controller =
                     Robolectric.buildActivity(CadastroMedicamentoActivity.class, cadastroIntent).setup()) {
            CadastroMedicamentoActivity activity = controller.get();

            TextInputEditText etNome = activity.findViewById(R.id.et_nome_medicamento);
            TextInputEditText etDosagem = activity.findViewById(R.id.et_dosagem);
            TextInputEditText etEstoque = activity.findViewById(R.id.et_estoque);
            TextInputEditText etHorario = activity.findViewById(R.id.et_horario_inicial);
            TextInputEditText etValidade = activity.findViewById(R.id.et_data_validade);
            TextInputLayout tilValidade = activity.findViewById(R.id.til_data_validade);

            etNome.setText("AAS 100mg");
            etDosagem.setText("1 comprimido");
            etEstoque.setText("25 comprimidos");
            etHorario.setText("09:00");

            // Data inválida deve exibir erro e impedir salvamento
            etValidade.setText("99/99/2026");
            activity.findViewById(R.id.btn_salvar_medicamento).performClick();
            assertNotNull(tilValidade.getError());

            // Data válida DD/MM/AAAA deve persistir normalmente
            etValidade.setText(validadeFutura);
            activity.findViewById(R.id.btn_salvar_medicamento).performClick();
            assertTrue(activity.isFinishing());
        }

        Medicamento aas = null;
        for (Medicamento m : new MedicamentoDAO(context).listarTodosMedicamentos()) {
            if ("AAS 100mg".equals(m.getNomeMedicamento())) {
                aas = m;
                break;
            }
        }
        assertNotNull(aas);
        assertEquals(validadeFutura, aas.getDataValidade());

        // Edição removendo data de validade (campo opcional vazio) deve funcionar sem erro
        Intent editIntent = new Intent(context, EditarMedicamentoActivity.class);
        editIntent.putExtra(EditarMedicamentoActivity.EXTRA_MEDICAMENTO_ID, aas.getId());

        try (ActivityController<EditarMedicamentoActivity> editController =
                     Robolectric.buildActivity(EditarMedicamentoActivity.class, editIntent).setup()) {
            EditarMedicamentoActivity editActivity = editController.get();
            TextInputEditText etValidadeEdit = editActivity.findViewById(R.id.et_data_validade);
            assertEquals(validadeFutura, etValidadeEdit.getText().toString());

            etValidadeEdit.setText("");
            editActivity.findViewById(R.id.btn_salvar_alteracoes).performClick();
            assertTrue(editActivity.isFinishing());
        }

        Medicamento aasAtualizado = DatabaseHelper.getInstance(context).buscarMedicamentoPorId(aas.getId());
        assertNotNull(aasAtualizado);
        assertEquals("", aasAtualizado.getDataValidade());
    }

    @Test
    public void estoqueEListaMedicamentosDevemExibirBadgesDeVencidoEVencimentoProximo() {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        for (Medicamento m : db.listarTodosMedicamentos()) {
            db.excluirMedicamento(m.getId());
        }

        Idoso residente = new IdosoDAO(context).listarTodos().get(0);

        String dataVencida = ValidadeMedicamentoHelper.gerarDataDeslocadaFormatada(-5);
        String dataProximo = ValidadeMedicamentoHelper.gerarDataDeslocadaFormatada(15);

        Medicamento medVencido = new Medicamento(
                residente.getId(), "Dipirona Vencida", "1 comp.", "Comprimido", "23:55", 12, "", 1
        );
        medVencido.setEstoque("30 comprimidos");
        medVencido.setDataValidade(dataVencida);
        db.cadastrarMedicamento(medVencido);

        Medicamento medProximo = new Medicamento(
                residente.getId(), "Sinvastatina Próxima", "1 comp.", "Comprimido", "23:50", 24, "", 1
        );
        medProximo.setEstoque("28 comprimidos");
        medProximo.setDataValidade(dataProximo);
        db.cadastrarMedicamento(medProximo);

        // 1. Valida badges no Estoque Geral
        try (ActivityController<EstoqueActivity> estoqueCtrl =
                     Robolectric.buildActivity(EstoqueActivity.class).setup()) {
            EstoqueActivity estoqueActivity = estoqueCtrl.get();
            RecyclerView rvEstoque = estoqueActivity.findViewById(R.id.rv_estoque_medicamentos);
            rvEstoque.measure(
                    View.MeasureSpec.makeMeasureSpec(520, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
            );
            rvEstoque.layout(0, 0, 520, 900);

            assertEquals(2, rvEstoque.getAdapter().getItemCount());

            View itemVencido = rvEstoque.getChildAt(0);
            TextView tvBadge0 = itemVencido.findViewById(R.id.tv_item_badge_validade);
            assertEquals(View.VISIBLE, tvBadge0.getVisibility());
            assertEquals("Vencido", tvBadge0.getText().toString());

            View itemProximo = rvEstoque.getChildAt(1);
            TextView tvBadge1 = itemProximo.findViewById(R.id.tv_item_badge_validade);
            assertEquals(View.VISIBLE, tvBadge1.getVisibility());
            assertTrue(tvBadge1.getText().toString().contains("Vence em 15 dias"));
        }

        // 2. Valida badges na lista de Medicamentos
        try (ActivityController<MedicamentoActivity> medCtrl =
                     Robolectric.buildActivity(MedicamentoActivity.class).setup()) {
            MedicamentoActivity medActivity = medCtrl.get();
            RecyclerView rvMeds = medActivity.findViewById(R.id.rvMedicamentos);
            rvMeds.measure(
                    View.MeasureSpec.makeMeasureSpec(520, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
            );
            rvMeds.layout(0, 0, 520, 900);

            boolean encontrouBadgeVencido = false;
            boolean encontrouBadgeProximo = false;
            for (int i = 0; i < rvMeds.getChildCount(); i++) {
                View card = rvMeds.getChildAt(i);
                TextView tvBadgeVal = card.findViewById(R.id.tv_badge_validade);
                if (tvBadgeVal != null && tvBadgeVal.getVisibility() == View.VISIBLE) {
                    String txt = tvBadgeVal.getText().toString();
                    if ("Vencido".equals(txt)) {
                        encontrouBadgeVencido = true;
                    } else if (txt.contains("Vence em 15 dias")) {
                        encontrouBadgeProximo = true;
                    }
                }
            }
            assertTrue("Lista de medicamentos deve exibir badge vermelho para item vencido", encontrouBadgeVencido);
            assertTrue("Lista de medicamentos deve exibir badge laranja para vencimento em <= 30 dias", encontrouBadgeProximo);
        }
    }

    @Test
    public void passagemDeTurnoDeveExibirResumoPlantaoChipsOcorrenciaECompartilharWhatsapp() {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        List<Medicamento> meds = db.listarTodosMedicamentos();
        assertFalse(meds.isEmpty());
        // Registra uma dose nas últimas 12 horas
        String agoraFormatado = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        db.registrarAplicacao(new HistoricoMedicacao(
                0,
                meds.get(0).getId(),
                meds.get(0).getIdIdoso(),
                agoraFormatado,
                agoraFormatado,
                HistoricoMedicacao.STATUS_ADMINISTRADO,
                "Enf. Marina",
                ""
        ));

        try (ActivityController<PassagemTurnoActivity> controller =
                     Robolectric.buildActivity(PassagemTurnoActivity.class).setup()) {
            PassagemTurnoActivity activity = controller.get();

            TextView tvDoses12h = activity.findViewById(R.id.tv_resumo_plantao_doses_12h);
            TextView tvPendentes = activity.findViewById(R.id.tv_resumo_plantao_pendentes);
            TextView tvAlertasEstoque = activity.findViewById(R.id.tv_resumo_plantao_estoque);

            assertTrue(Integer.parseInt(tvDoses12h.getText().toString()) >= 1);
            assertNotNull(tvPendentes.getText().toString());
            assertTrue(Integer.parseInt(tvAlertasEstoque.getText().toString()) >= 1);

            // Abre modal e usa chip de intercorrência rápida + turno Noturno
            activity.findViewById(R.id.fab_nova_passagem_turno).performClick();
            ShadowLooper.idleMainLooper();

            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);

            AutoCompleteTextView actvTurno = dialog.findViewById(R.id.actv_turno_plantao);
            TextInputEditText etObs = dialog.findViewById(R.id.et_observacoes_passagem);
            TextInputEditText etIntercorrencias = dialog.findViewById(R.id.et_intercorrencias_passagem);
            Chip chipFebre = dialog.findViewById(R.id.chip_ocorrencia_febre);

            actvTurno.setText(PassagemTurno.TURNO_NOTURNO, false);
            etObs.setText("Ronda noturna realizada com atenção redobrada.");
            chipFebre.performClick();
            assertTrue(etIntercorrencias.getText().toString().contains("Febre"));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            // Filtra por Noturno
            Chip chipNoturno = activity.findViewById(R.id.chip_filtro_noturno);
            chipNoturno.performClick();
            ShadowLooper.idleMainLooper();

            RecyclerView rv = activity.findViewById(R.id.rv_passagem_turno);
            assertTrue(rv.getAdapter().getItemCount() >= 1);

            // Compartilhar no WhatsApp da Equipe
            activity.findViewById(R.id.btn_compartilhar_whatsapp_turno).performClick();
            Intent shareIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(shareIntent);
            Intent payloadIntent = Intent.ACTION_CHOOSER.equals(shareIntent.getAction())
                    ? shareIntent.getParcelableExtra(Intent.EXTRA_INTENT)
                    : shareIntent;
            assertNotNull(payloadIntent);
            assertEquals(Intent.ACTION_SEND, payloadIntent.getAction());
            String textoCompartilhado = payloadIntent.getStringExtra(Intent.EXTRA_TEXT);
            assertNotNull(textoCompartilhado);
            assertTrue(textoCompartilhado.contains("Passagem de Turno"));
            assertTrue(textoCompartilhado.contains("Doses administradas (12h)"));
        }
    }

    @Test
    public void mainActivityAoInicializarJaExibeDashboardCompletoProntoSemEspera() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity main = controller.get();

            // Antes mesmo de onStart/onResume, onCreate já entrega o Dashboard completo e pronto
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_resumo_total_residentes).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_resumo_vagas).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_alerta_aniversariantes).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_kpi_doses_atrasadas).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_kpi_estoque_critico).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_kpi_passagem_turno).getVisibility());
            assertEquals(View.VISIBLE, main.findViewById(R.id.card_panorama_clinico_dashboard).getVisibility());

            TextView tvTotal = main.findViewById(R.id.tv_valor_total_residentes);
            TextView tvOcupadas = main.findViewById(R.id.tv_vagas_ocupadas_valor);
            TextView tvLivres = main.findViewById(R.id.tv_vagas_disponiveis_valor);
            TextView tvCriticos = main.findViewById(R.id.tv_kpi_estoque_critico_valor);
            TextView tvStatusEstoque = main.findViewById(R.id.tv_kpi_estoque_critico_status);

            assertEquals("4", tvTotal.getText().toString());
            assertEquals("4", tvOcupadas.getText().toString());
            assertEquals("16", tvLivres.getText().toString());
            assertEquals("1", tvCriticos.getText().toString());
            assertTrue(tvStatusEstoque.getText().toString().contains("venc./próx."));

            // Clicar no botão do relatório gerencial PDF diretamente na tela inicial abre o visualizador PDF
            main.findViewById(R.id.btn_dashboard_relatorio_gerencial_pdf).performClick();
            Intent pdfIntent = Shadows.shadowOf(main).getNextStartedActivity();
            assertNotNull(pdfIntent);
            assertEquals(VisualizadorPdfOfflineActivity.class.getName(), pdfIntent.getComponent().getClassName());
        }
    }

    @Test
    public void dashboardActivityDeveSerLauncherPrincipalEExibirIndicadoresENavegarParaModulos() {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launchIntent);
        assertEquals(DashboardActivity.class.getName(), launchIntent.getComponent().getClassName());

        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class, launchIntent).setup()) {
            DashboardActivity dashboard = controller.get();
            assertEquals("4", ((TextView) dashboard.findViewById(R.id.tv_valor_total_residentes)).getText().toString());
            assertEquals("16", ((TextView) dashboard.findViewById(R.id.tv_vagas_disponiveis_valor)).getText().toString());
            assertEquals("1", ((TextView) dashboard.findViewById(R.id.tv_kpi_estoque_critico_valor)).getText().toString());

            View btnModulos = dashboard.findViewById(R.id.btn_abrir_modulos_operacao);
            assertNotNull(btnModulos);
            assertEquals(View.VISIBLE, btnModulos.getVisibility());
            btnModulos.performClick();

            Intent next = Shadows.shadowOf(dashboard).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        }
    }
}
