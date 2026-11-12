package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.ResumoProntuarioPdfGenerator;
import com.example.util.SessaoManager;
import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, shadows = {ShadowNativePdfDocument.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResumoProntuarioPdfOfflineTest {

    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        java.lang.reflect.Field sCacheField = androidx.core.content.FileProvider.class.getDeclaredField("sCache");
        sCacheField.setAccessible(true);
        java.util.Map<?, ?> cache = (java.util.Map<?, ?>) sCacheField.get(null);
        if (cache != null) {
            cache.clear();
        }

        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        limparArquivosTeste();

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Administrador Teste", "admin@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@1234"
        );
    }

    @After
    public void tearDown() {
        limparArquivosTeste();
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    private void limparArquivosTeste() {
        File dirOffline = ResumoProntuarioPdfGenerator.obterDiretorioResumosOffline(context);
        removerConteudoPasta(dirOffline);
        File docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        removerConteudoPasta(docsDir);
        File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        removerConteudoPasta(downloadsDir);
    }

    private void removerConteudoPasta(File pasta) {
        if (pasta != null && pasta.exists()) {
            File[] files = pasta.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    @Test
    public void gerarESalvarResumoOffline_deveGerarPdfValidoESalvarNoArmazenamentoLocalECopiarParaPasta() throws Exception {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        MedicamentoDAO medicamentoDAO = new MedicamentoDAO(context);

        Idoso residente = new Idoso(
                0,
                "Helena Vasconcelos",
                "12/04/1942",
                "O+",
                "Dipirona e Penicilina",
                "Dieta hipossódica; acompanhamento pressórico diário",
                "(11) 98765-4321",
                null
        );
        residente.setCpf("123.456.789-00");
        residente.setLeitoQuarto("Quarto 204 - Leito A");
        residente.setNomeFamiliar("Marcos Vasconcelos");
        residente.setGrauParentesco("Filho");
        long idInserido = idosoDAO.inserir(residente, PerfilUsuario.ADMINISTRADOR);
        assertTrue(idInserido > 0);
        residente.setId((int) idInserido);

        assertNull(ResumoProntuarioPdfGenerator.obterResumoSalvoOffline(context, residente));

        Medicamento medAtivo = new Medicamento(
                (int) idInserido,
                "Losartana Potássica",
                "50mg",
                "Comprimido",
                "08:00",
                12,
                "Administrar após o café",
                1
        );
        medAtivo.setEstoque("28 comprimidos");

        Medicamento medSuspenso = new Medicamento(
                (int) idInserido,
                "Sinvastatina",
                "20mg",
                "Comprimido",
                "21:00",
                24,
                "Uso noturno",
                0
        );
        medSuspenso.setEstoque("15 comprimidos");
        medicamentoDAO.inserir(medAtivo);
        medicamentoDAO.inserir(medSuspenso);

        List<Medicamento> prescricoes = medicamentoDAO.listarPorIdoso((int) idInserido);
        File arquivoOffline = ResumoProntuarioPdfGenerator.gerarESalvarResumoOffline(context, residente, prescricoes);

        assertNotNull(arquivoOffline);
        assertTrue(arquivoOffline.exists());
        assertTrue(arquivoOffline.length() > 100);
        assertTrue(arquivoOffline.getAbsolutePath().contains(ResumoProntuarioPdfGenerator.DIRETORIO_RESUMOS_OFFLINE));

        byte[] cabecalho = new byte[5];
        try (FileInputStream fis = new FileInputStream(arquivoOffline)) {
            int lidos = fis.read(cabecalho);
            assertEquals(5, lidos);
        }
        assertEquals("%PDF-", new String(cabecalho, StandardCharsets.US_ASCII));

        File recuperadoOffline = ResumoProntuarioPdfGenerator.obterResumoSalvoOffline(context, residente);
        assertNotNull(recuperadoOffline);
        assertEquals(arquivoOffline.getAbsolutePath(), recuperadoOffline.getAbsolutePath());

        File destinoPastaLocal = new File(context.getCacheDir(), "Copia_Local_Escolhida.pdf");
        try {
            ResumoProntuarioPdfGenerator.copiarParaUriLocal(
                    context,
                    arquivoOffline,
                    Uri.fromFile(destinoPastaLocal)
            );
            assertTrue(destinoPastaLocal.exists());
            assertEquals(arquivoOffline.length(), destinoPastaLocal.length());
        } finally {
            destinoPastaLocal.delete();
        }

        String nomeSalvoDownloads = ResumoProntuarioPdfGenerator.salvarCopiaEmDownloads(context, arquivoOffline);
        assertNotNull(nomeSalvoDownloads);
        assertTrue(nomeSalvoDownloads.endsWith(".pdf"));
    }

    @Test
    public void detalhesIdosoActivity_gerarResumoPdfOffline_deveAtualizarStatusEAbrirVisualizadorOffline() throws Exception {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        Idoso residente = new Idoso(
                0,
                "Antônio Carlos Prado",
                "20/08/1938",
                "A-",
                "Nenhuma alergia relatada",
                "Uso de andador em deslocamentos externos",
                "(21) 99888-7766",
                null
        );
        residente.setCpf("321.654.987-11");
        residente.setLeitoQuarto("Quarto 102");
        residente.setNomeFamiliar("Beatriz Prado");
        residente.setGrauParentesco("Filha");
        long idIdoso = idosoDAO.inserir(residente, PerfilUsuario.ADMINISTRADOR);

        // Mesmo no perfil Cuidador, o resumo offline deve estar disponível para consulta no plantão
        SessaoManager.getInstance(context).salvarPerfilAtivo(PerfilUsuario.CUIDADOR);

        Intent intent = new Intent(context, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, (int) idIdoso);

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intent).setup()) {
            DetalhesIdosoActivity activity = controller.get();

            MaterialButton btnGerarResumo = activity.findViewById(R.id.btn_gerar_resumo_pdf);
            MaterialButton btnAbrirOffline = activity.findViewById(R.id.btn_abrir_resumo_offline);
            TextView tvStatus = activity.findViewById(R.id.tv_status_resumo_offline);

            assertEquals(View.VISIBLE, btnGerarResumo.getVisibility());
            assertEquals(View.GONE, btnAbrirOffline.getVisibility());

            btnGerarResumo.performClick();
            assertNotNull(activity.ultimaTarefaResumoOffline);
            activity.ultimaTarefaResumoOffline.get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertEquals(View.VISIBLE, btnAbrirOffline.getVisibility());
            assertTrue(tvStatus.getText().toString().contains("Disponível offline"));

            Intent intentVisualizador = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(intentVisualizador);
            assertEquals(
                    VisualizadorPdfOfflineActivity.class.getName(),
                    intentVisualizador.getComponent().getClassName()
            );

            // Valida a abertura da tela nativa de consulta offline
            try (ActivityController<VisualizadorPdfOfflineActivity> viewerController =
                         Robolectric.buildActivity(VisualizadorPdfOfflineActivity.class, intentVisualizador).setup()) {
                VisualizadorPdfOfflineActivity viewer = viewerController.get();
                TextView tvNome = viewer.findViewById(R.id.tv_nome_residente_pdf);
                assertEquals("Antônio Carlos Prado", tvNome.getText().toString());
            }
        }
    }

    @Test
    public void listaIdososActivity_selecionarResidenteEGerarResumo_deveSalvarPdfOfflineEAbrirVisualizador() throws Exception {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        Idoso residente = new Idoso(
                0,
                "Benedita Oliveira",
                "05/11/1945",
                "B+",
                "Sulfa",
                "Controle glicêmico capilar",
                "(31) 99111-2233",
                null
        );
        long idIdoso = idosoDAO.inserir(residente, PerfilUsuario.ADMINISTRADOR);
        residente.setId((int) idIdoso);

        Intent intent = new Intent(context, ListaIdososActivity.class);
        try (ActivityController<ListaIdososActivity> controller =
                     Robolectric.buildActivity(ListaIdososActivity.class, intent).setup()) {
            ListaIdososActivity activity = controller.get();

            MaterialButton btnAtalhoResumo = activity.findViewById(R.id.btn_gerar_resumo_selecionado);
            assertNotNull(btnAtalhoResumo);
            assertEquals(View.VISIBLE, btnAtalhoResumo.getVisibility());

            activity.gerarResumoPdfParaIdosoSelecionado(residente);
            assertNotNull(activity.ultimaTarefaResumoLista);
            activity.ultimaTarefaResumoLista.get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            File resumoSalvo = ResumoProntuarioPdfGenerator.obterResumoSalvoOfflinePorId(context, (int) idIdoso);
            assertNotNull(resumoSalvo);
            assertTrue(resumoSalvo.exists());

            Intent nextIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertEquals(
                    VisualizadorPdfOfflineActivity.class.getName(),
                    nextIntent.getComponent().getClassName()
            );
        }
    }
}
