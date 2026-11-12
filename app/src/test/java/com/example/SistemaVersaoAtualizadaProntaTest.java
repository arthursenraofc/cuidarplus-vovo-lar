package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.model.Idoso;
import com.example.util.SessaoManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.io.File;
import java.util.List;

/**
 * Teste de prontidão de inicialização e atualização (CUJ) garantindo que o CuidarPlus
 * sempre abra diretamente na versão atualizada com menu operacional limpo, opção dedicada
 * de Dashboard, botão WhatsApp, PDF offline, captura de foto e alerta de estoque.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class SistemaVersaoAtualizadaProntaTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
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
    public void buildConfigEManifestDevemGarantirAtualizacaoEAberturaDiretaNoMenuPrincipal() throws Exception {
        assertTrue(
                "versionCode deve ser superior às versões anteriores para forçar upgrade do APK",
                BuildConfig.VERSION_CODE >= 14
        );

        PackageManager pm = context.getPackageManager();
        Intent mainLauncherQuery = new Intent(Intent.ACTION_MAIN);
        mainLauncherQuery.addCategory(Intent.CATEGORY_LAUNCHER);
        mainLauncherQuery.setPackage(context.getPackageName());
        List<android.content.pm.ResolveInfo> launchers = pm.queryIntentActivities(mainLauncherQuery, 0);
        assertEquals("Apenas MainActivity deve possuir MAIN/LAUNCHER no AndroidManifest", 1, launchers.size());
        assertEquals(MainActivity.class.getName(), launchers.get(0).activityInfo.name);

        Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Intent de LAUNCHER deve estar configurada", launchIntent);
        assertEquals(MainActivity.class.getName(), launchIntent.getComponent().getClassName());

        ActivityInfo info = pm.getActivityInfo(launchIntent.getComponent(), 0);
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode);
        assertTrue((info.flags & ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH) != 0);

        // Verifica renderização estática e direta logo no onCreate() (sem depender de callbacks assíncronos)
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity activity = controller.get();
            TextView tvTituloModulos = activity.findViewById(R.id.tv_titulo_modulos_operacao);
            TextView tvContador = activity.findViewById(R.id.tv_contador_dashboard);
            assertNotNull(tvTituloModulos);
            assertEquals("Módulos e Operação Diária", tvTituloModulos.getText().toString());
            assertEquals("4 Residentes", tvContador.getText().toString());
        }
    }

    @Test
    public void upgradeDesdeSprint1DeveAbrirMenuComOpcaoDashboardWhatsAppPdfECardAlertaEstoqueProntos() {
        // 1. Simula instalação prévia da Sprint 1 (banco v1 apenas com tabela idosos básica)
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dbFile.getParentFile().mkdirs();
        }

        try (SQLiteDatabase legacySprint1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacySprint1Db.execSQL("CREATE TABLE idosos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nome TEXT NOT NULL, "
                    + "data_nascimento TEXT, "
                    + "tipo_sanguineo TEXT, "
                    + "alergias TEXT, "
                    + "restricoes_medicas TEXT, "
                    + "contato_emergencia TEXT, "
                    + "caminho_foto TEXT);");

            legacySprint1Db.execSQL("INSERT INTO idosos (id, nome, data_nascimento, tipo_sanguineo, alergias, restricoes_medicas, contato_emergencia, caminho_foto) VALUES "
                    + "(1, 'Dona Francisca de Souza', '15/04/1945', 'O+', 'Alergia a Penicilina', 'Hipertensão', 'Filha Mariana - (51) 98765-4321', ''), "
                    + "(2, 'Seu Antônio Carlos Ribeiro', '08/11/1939', 'A+', 'Nenhuma', 'Parkinson inicial', 'Filho Roberto - (51) 99123-8877', ''), "
                    + "(3, 'Dona Tereza Cristina Moreira', '22/07/1942', 'B-', 'Intolerância à lactose', 'Osteoporose', 'Irmã Helena - (51) 98844-3322', '');");
            legacySprint1Db.setVersion(1);
        }

        // 2. Abre MainActivity e valida menu enxuto + opção dedicada de Dashboard
        Intent dashboardIntent;
        try (ActivityController<MainActivity> mainController =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity mainActivity = mainController.get();

            // Cards expandidos de Dashboard não ficam fixos na tela principal
            assertNull(mainActivity.findViewById(R.id.card_resumo_total_residentes));
            assertNull(mainActivity.findViewById(R.id.card_resumo_vagas));

            MaterialCardView cardMenuDashboard = mainActivity.findViewById(R.id.card_menu_dashboard);
            assertNotNull(cardMenuDashboard);
            assertEquals(View.VISIBLE, cardMenuDashboard.getVisibility());

            // Garante que não há rodapé antigo da Sprint 1 na tela principal
            LinearLayout containerMenu = mainActivity.findViewById(R.id.container_conteudo_dashboard);
            for (int i = 0; i < containerMenu.getChildCount(); i++) {
                View child = containerMenu.getChildAt(i);
                if (child instanceof TextView) {
                    String texto = ((TextView) child).getText().toString().toLowerCase();
                    assertFalse("Rodapé antigo da Sprint 1 não deve existir", texto.contains("sprint 1"));
                }
            }

            cardMenuDashboard.performClick();
            dashboardIntent = Shadows.shadowOf(mainActivity).getNextStartedActivity();
            assertNotNull(dashboardIntent);
            assertEquals(DashboardActivity.class.getName(), dashboardIntent.getComponent().getClassName());
        }

        // 3. Abre a opção DashboardActivity e valida indicadores migrados
        try (ActivityController<DashboardActivity> dashController =
                     Robolectric.buildActivity(DashboardActivity.class, dashboardIntent).setup()) {
            DashboardActivity dash = dashController.get();
            TextView tvTotal = dash.findViewById(R.id.tv_valor_total_residentes);
            LinearLayout containerAniv = dash.findViewById(R.id.container_lista_aniversariantes);

            assertEquals("4", tvTotal.getText().toString());
            assertEquals(View.VISIBLE, containerAniv.getVisibility());
            assertTrue(containerAniv.getChildCount() >= 1);
        }

        // 4. Verifica que os dados enriquecidos para PDF e WhatsApp estão prontos na Ficha do Idoso
        IdosoDAO idosoDAO = new IdosoDAO(context);
        List<Idoso> idosos = idosoDAO.listarTodos();
        assertEquals(4, idosos.size());

        Idoso francisca = idosoDAO.buscarPorId(1);
        assertNotNull(francisca);
        assertEquals("184.520.390-15", francisca.getCpf());
        assertEquals("Quarto 01 - Leito A", francisca.getLeitoQuarto());
        assertEquals("Mariana de Souza", francisca.getNomeFamiliar());

        Intent detalhesIntent = new Intent(context, DetalhesIdosoActivity.class);
        detalhesIntent.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, 1);

        try (ActivityController<DetalhesIdosoActivity> detalhesController =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, detalhesIntent).setup()) {
            DetalhesIdosoActivity detalhes = detalhesController.get();

            MaterialButton btnLigar = detalhes.findViewById(R.id.btn_ligar_contato);
            MaterialButton btnWhatsapp = detalhes.findViewById(R.id.btn_whatsapp_contato);
            MaterialButton btnExportarPdf = detalhes.findViewById(R.id.btn_exportar_pdf);
            MaterialCardView btnFotoBadge = detalhes.findViewById(R.id.btn_camera_badge_detalhe);

            assertNotNull(btnLigar);
            assertNotNull(btnWhatsapp);
            assertNotNull(btnExportarPdf);
            assertNotNull(btnFotoBadge);

            assertEquals(View.VISIBLE, btnWhatsapp.getVisibility());
            assertTrue(btnWhatsapp.isEnabled());
            assertEquals("Contatar Familiar via WhatsApp", btnWhatsapp.getText().toString());
            assertTrue("Botão Ligar e Botão WhatsApp devem ter cores distintas para hierarquia visual",
                    btnLigar.getBackgroundTintList().getDefaultColor()
                            != btnWhatsapp.getBackgroundTintList().getDefaultColor());

            assertEquals(View.VISIBLE, btnExportarPdf.getVisibility());
            assertEquals(View.VISIBLE, btnFotoBadge.getVisibility());
        }

        // 4b. Abre ListaIdososActivity (activity_fichas_idosos.xml) e valida folga do FAB e busca Pill MD3
        try (ActivityController<ListaIdososActivity> listaController =
                     Robolectric.buildActivity(ListaIdososActivity.class).setup()) {
            ListaIdososActivity listaActivity = listaController.get();
            RecyclerView rvIdosos = listaActivity.findViewById(R.id.rv_lista_idosos);
            View tilBusca = listaActivity.findViewById(R.id.til_busca_idosos);

            assertNotNull(rvIdosos);
            assertNotNull(tilBusca);
            assertFalse("clipToPadding deve ser false para rolar além do FAB", rvIdosos.getClipToPadding());
            float density = listaActivity.getResources().getDisplayMetrics().density;
            assertTrue("paddingBottom deve reservar pelo menos 88dp para o FAB",
                    rvIdosos.getPaddingBottom() >= Math.round(88f * density));
        }

        // 5. Abre MedicamentoActivity e valida layout ConstraintLayout + card de alerta de estoque ativo
        try (ActivityController<MedicamentoActivity> medController =
                     Robolectric.buildActivity(MedicamentoActivity.class).setup()) {
            MedicamentoActivity medActivity = medController.get();

            MaterialCardView cardAcoes = medActivity.findViewById(R.id.cardAcoes);
            MaterialCardView cardEstoque = medActivity.findViewById(R.id.cardEstoque);
            RecyclerView rvMedicamentos = medActivity.findViewById(R.id.rvMedicamentos);

            assertNotNull(cardAcoes);
            assertNotNull(cardEstoque);
            assertNotNull(rvMedicamentos);
            assertEquals(View.VISIBLE, cardEstoque.getVisibility());
            assertTrue("Alerta de estoque crítico deve estar ativo para Prolopa (4 < 10)", medActivity.isAlertaEstoqueAtivo());
        }
    }
}
