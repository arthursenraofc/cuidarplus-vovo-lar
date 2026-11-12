package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.DashboardManager;
import com.example.util.PerfilManager;
import com.example.util.SessaoManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

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

import java.util.Calendar;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class DashboardPrincipalM3Test {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        context.getSharedPreferences("CuidarPlusDashboardPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Coord. Helena", "helena@vovolar.org", PerfilUsuario.ADMINISTRADOR),
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
    public void menuPrincipalDeveExibirOpcaoDedicadaDeDashboardENavegarParaDashboardActivity() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity mainActivity = controller.get();

            // Os cards expandidos do Dashboard NÃO devem poluir a tela principal de operação diária
            assertNull(mainActivity.findViewById(R.id.card_resumo_total_residentes));
            assertNull(mainActivity.findViewById(R.id.card_resumo_vagas));
            assertNull(mainActivity.findViewById(R.id.card_alerta_aniversariantes));

            MaterialCardView cardMenuDashboard = mainActivity.findViewById(R.id.card_menu_dashboard);
            TextView tvBadgeMenu = mainActivity.findViewById(R.id.tv_badge_resumo_menu_dashboard);
            assertNotNull(cardMenuDashboard);
            assertNotNull(tvBadgeMenu);
            assertEquals(View.VISIBLE, cardMenuDashboard.getVisibility());
            assertTrue(tvBadgeMenu.getText().toString().contains("4/20 vagas"));

            cardMenuDashboard.performClick();
            Intent startedIntent = Shadows.shadowOf(mainActivity).getNextStartedActivity();
            assertNotNull(startedIntent);
            assertEquals(DashboardActivity.class.getName(), startedIntent.getComponent().getClassName());
        }
    }

    @Test
    public void dashboardDeveRenderizarMaterialCardsComTotalResidentesVagasEAniversarianteInicial() {
        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class).setup()) {
            DashboardActivity activity = controller.get();

            MaterialCardView cardTotal = activity.findViewById(R.id.card_resumo_total_residentes);
            MaterialCardView cardVagas = activity.findViewById(R.id.card_resumo_vagas);
            MaterialCardView cardOcupadas = activity.findViewById(R.id.card_vagas_ocupadas);
            MaterialCardView cardDisponiveis = activity.findViewById(R.id.card_vagas_disponiveis);
            MaterialCardView cardAniversariantes = activity.findViewById(R.id.card_alerta_aniversariantes);

            assertNotNull(cardTotal);
            assertNotNull(cardVagas);
            assertNotNull(cardOcupadas);
            assertNotNull(cardDisponiveis);
            assertNotNull(cardAniversariantes);

            assertEquals(View.VISIBLE, cardTotal.getVisibility());
            assertEquals(View.VISIBLE, cardVagas.getVisibility());
            assertEquals(View.VISIBLE, cardAniversariantes.getVisibility());

            IdosoDAO dao = new IdosoDAO(context);
            int totalBanco = dao.contarTotalIdosos();
            assertEquals(4, totalBanco);

            TextView tvTotal = activity.findViewById(R.id.tv_valor_total_residentes);
            TextView tvOcupadas = activity.findViewById(R.id.tv_vagas_ocupadas_valor);
            TextView tvDisponiveis = activity.findViewById(R.id.tv_vagas_disponiveis_valor);
            LinearProgressIndicator progress = activity.findViewById(R.id.progress_ocupacao_vagas);

            assertEquals("4", tvTotal.getText().toString());
            assertEquals("4", tvOcupadas.getText().toString());
            assertEquals("16", tvDisponiveis.getText().toString());
            assertEquals(20, progress.getProgress());

            // Verifica que há ao menos 1 aniversariante do mês atual exibido no alerta
            LinearLayout containerAniv = activity.findViewById(R.id.container_lista_aniversariantes);
            View layoutSemAniv = activity.findViewById(R.id.layout_sem_aniversariantes);
            assertEquals(View.VISIBLE, containerAniv.getVisibility());
            assertEquals(View.GONE, layoutSemAniv.getVisibility());
            assertTrue(containerAniv.getChildCount() >= 1);
        }
    }

    @Test
    public void dashboardDeveAtualizarOcupacaoELotacaoCompletaAoCadastrarIdosoEAjustarCapacidade() {
        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class).setup()) {
            DashboardActivity activity = controller.get();
            IdosoDAO dao = new IdosoDAO(context);

            // Cadastra 5º residente
            Idoso novo = new Idoso(
                    "Dona Aurora Mendes",
                    "10/08/1940",
                    "O+",
                    "Nenhuma",
                    "Hipertensão controlada",
                    "(51) 99888-7766",
                    ""
            );
            dao.inserir(novo);

            // Ajusta capacidade via diálogo M3 do botão btn_ajustar_capacidade para 5 vagas
            View btnAjustar = activity.findViewById(R.id.btn_ajustar_capacidade);
            btnAjustar.performClick();
            ShadowLooper.idleMainLooper();

            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());

            EditText campoCapacidade = dialog.findViewById(android.R.id.edit);
            assertNotNull(campoCapacidade);
            campoCapacidade.setText("5");

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            TextView tvTotal = activity.findViewById(R.id.tv_valor_total_residentes);
            TextView tvOcupadas = activity.findViewById(R.id.tv_vagas_ocupadas_valor);
            TextView tvDisponiveis = activity.findViewById(R.id.tv_vagas_disponiveis_valor);
            TextView tvPercentual = activity.findViewById(R.id.tv_percentual_ocupacao);
            TextView tvStatusLotacao = activity.findViewById(R.id.tv_status_lotacao);
            LinearProgressIndicator progress = activity.findViewById(R.id.progress_ocupacao_vagas);

            assertEquals("5", tvTotal.getText().toString());
            assertEquals("5", tvOcupadas.getText().toString());
            assertEquals("0", tvDisponiveis.getText().toString());
            assertEquals("100% de ocupação", tvPercentual.getText().toString());
            assertEquals("Lotação completa", tvStatusLotacao.getText().toString());
            assertEquals(100, progress.getProgress());
        }
    }

    @Test
    public void alertaAniversariantesDeveOrdenarPorDiaDestacarHojeENavegarParaFichaDoResidente() {
        IdosoDAO dao = new IdosoDAO(context);
        // Limpa residentes existentes para testar cenário controlado em Maio (mês 5)
        List<Idoso> iniciais = dao.listarTodos();
        for (Idoso item : iniciais) {
            dao.excluir(item.getId());
        }

        Idoso anivDia20 = new Idoso("Seu Benedito Lima", "20/05/1938", "A+", "", "", "(51) 99111-2222", "");
        anivDia20.setLeitoQuarto("Quarto 05");
        long idBenedito = dao.inserir(anivDia20);

        Idoso anivHojeDia09 = new Idoso("Dona Clara Nunes", "09/05/1946", "O-", "", "", "(51) 99333-4444", "");
        anivHojeDia09.setLeitoQuarto("Quarto 02");
        long idClara = dao.inserir(anivHojeDia09);

        Idoso outroMes = new Idoso("Seu Geraldo Alves", "14/09/1943", "B+", "", "", "(51) 99555-6666", "");
        dao.inserir(outroMes);

        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class).setup()) {
            DashboardActivity activity = controller.get();

            // Simula data de referência: 09 de Maio de 2026
            Calendar refMaio = Calendar.getInstance();
            refMaio.set(2026, Calendar.MAY, 9, 10, 0, 0);
            activity.atualizarResumoDashboard(refMaio);

            TextView tvTituloMes = activity.findViewById(R.id.tv_titulo_aniversariantes_mes);
            TextView tvBadgeQtd = activity.findViewById(R.id.tv_badge_qtd_aniversariantes);
            LinearLayout containerAniv = activity.findViewById(R.id.container_lista_aniversariantes);
            View layoutSemAniv = activity.findViewById(R.id.layout_sem_aniversariantes);

            assertEquals("Aniversariantes de Maio", tvTituloMes.getText().toString());
            assertEquals("2 celebrações", tvBadgeQtd.getText().toString());
            assertEquals(View.VISIBLE, containerAniv.getVisibility());
            assertEquals(View.GONE, layoutSemAniv.getVisibility());
            assertEquals(2, containerAniv.getChildCount());

            // 1º item ordenado deve ser Dona Clara (dia 09/05) com badge "Hoje!" visível e completando 80 anos
            View primeiroCard = containerAniv.getChildAt(0);
            TextView tvNome1 = primeiroCard.findViewById(R.id.tv_nome_aniversariante);
            TextView tvDetalhe1 = primeiroCard.findViewById(R.id.tv_detalhe_aniversariante);
            TextView tvBadgeHoje1 = primeiroCard.findViewById(R.id.tv_badge_hoje_aniversariante);
            TextView tvData1 = primeiroCard.findViewById(R.id.tv_data_aniversariante);

            assertEquals("Dona Clara Nunes", tvNome1.getText().toString());
            assertTrue(tvDetalhe1.getText().toString().contains("80 anos"));
            assertEquals(View.VISIBLE, tvBadgeHoje1.getVisibility());
            assertEquals("Dia 09/05", tvData1.getText().toString());

            // 2º item deve ser Seu Benedito (dia 20/05) sem badge "Hoje!"
            View segundoCard = containerAniv.getChildAt(1);
            TextView tvNome2 = segundoCard.findViewById(R.id.tv_nome_aniversariante);
            TextView tvBadgeHoje2 = segundoCard.findViewById(R.id.tv_badge_hoje_aniversariante);
            assertEquals("Seu Benedito Lima", tvNome2.getText().toString());
            assertEquals(View.GONE, tvBadgeHoje2.getVisibility());
            assertTrue(idBenedito > 0);

            // Clicar no card do aniversariante deve abrir DetalhesIdosoActivity para Dona Clara
            primeiroCard.performClick();
            Intent startedIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(startedIntent);
            assertEquals(DetalhesIdosoActivity.class.getName(), startedIntent.getComponent().getClassName());
            assertEquals((int) idClara, startedIntent.getIntExtra("EXTRA_IDOSO_ID", -1));

            // Simula mês sem nenhum aniversariante (ex: Fevereiro)
            Calendar refFevereiro = Calendar.getInstance();
            refFevereiro.set(2026, Calendar.FEBRUARY, 12, 10, 0, 0);
            activity.atualizarResumoDashboard(refFevereiro);

            assertEquals("Aniversariantes de Fevereiro", tvTituloMes.getText().toString());
            assertEquals("Sem alertas", tvBadgeQtd.getText().toString());
            assertEquals(View.GONE, containerAniv.getVisibility());
            assertEquals(View.VISIBLE, layoutSemAniv.getVisibility());

            TextView tvVazioTitulo = activity.findViewById(R.id.tv_sem_aniversariantes_titulo);
            assertEquals("Nenhum aniversariante em Fevereiro", tvVazioTitulo.getText().toString());
        }
    }

    @Test
    public void clicarNoCardTotalResidentesDeveAbrirListaIdososActivity() {
        try (ActivityController<DashboardActivity> controller =
                     Robolectric.buildActivity(DashboardActivity.class).setup()) {
            DashboardActivity activity = controller.get();
            View cardTotal = activity.findViewById(R.id.card_resumo_total_residentes);
            cardTotal.performClick();

            Intent nextIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertEquals(ListaIdososActivity.class.getName(), nextIntent.getComponent().getClassName());
        }
    }
}
