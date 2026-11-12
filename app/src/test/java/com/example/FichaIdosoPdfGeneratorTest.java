package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.FichaIdosoPdfGenerator;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, shadows = {ShadowNativePdfDocument.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FichaIdosoPdfGeneratorTest {

    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        // Limpa o cache estático de diretórios do FileProvider entre testes isolados do Robolectric
        java.lang.reflect.Field sCacheField = androidx.core.content.FileProvider.class.getDeclaredField("sCache");
        sCacheField.setAccessible(true);
        java.util.Map<?, ?> cache = (java.util.Map<?, ?>) sCacheField.get(null);
        if (cache != null) {
            cache.clear();
        }

        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Administrador Teste", "admin@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@1234"
        );
    }

    @After
    public void tearDown() {
        File docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docsDir != null && docsDir.exists()) {
            File[] files = docsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void gerarProntuarioPdf_deveSalvarArquivoPdfValidoEmDirectoryDocumentsECalcularHorarios() throws Exception {
        assertEquals(595, FichaIdosoPdfGenerator.A4_WIDTH);
        assertEquals(842, FichaIdosoPdfGenerator.A4_HEIGHT);
        assertEquals("08:00, 16:00, 00:00", FichaIdosoPdfGenerator.calcularGradeHorarios("08:00", 8));
        assertEquals("08:00, 20:00", FichaIdosoPdfGenerator.calcularGradeHorarios("08:00", 12));
        assertEquals(1, FichaIdosoPdfGenerator.calcularTotalPaginas(4));
        assertEquals(2, FichaIdosoPdfGenerator.calcularTotalPaginas(15));

        IdosoDAO idosoDAO = new IdosoDAO(context);
        MedicamentoDAO medicamentoDAO = new MedicamentoDAO(context);

        Idoso idoso = idosoDAO.buscarPorId(1); // Dona Francisca de Souza (seed inicial)
        assertNotNull(idoso);
        List<Medicamento> prescricoes = medicamentoDAO.listarPorIdoso(idoso.getId());
        assertFalse(prescricoes.isEmpty());

        File arquivoPdf = FichaIdosoPdfGenerator.gerarProntuarioPdf(context, idoso, prescricoes);
        assertNotNull(arquivoPdf);
        assertTrue(arquivoPdf.exists());
        assertTrue(arquivoPdf.length() > 0);

        File expectedDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        assertEquals(expectedDir.getAbsolutePath(), arquivoPdf.getParentFile().getAbsolutePath());

        // Valida assinatura binária %PDF- no cabeçalho do arquivo gerado
        byte[] headerBytes = new byte[5];
        try (FileInputStream fis = new FileInputStream(arquivoPdf)) {
            int lidos = fis.read(headerBytes);
            assertEquals(5, lidos);
        }
        assertEquals("%PDF-", new String(headerBytes, StandardCharsets.US_ASCII));
    }

    @Test
    public void abrirECompartilharPdf_devemUsarFileProviderComFlagGrantReadUriPermission() throws Exception {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        MedicamentoDAO medicamentoDAO = new MedicamentoDAO(context);
        Idoso idoso = idosoDAO.buscarPorId(1);
        List<Medicamento> prescricoes = medicamentoDAO.listarPorIdoso(1);

        File arquivoPdf = FichaIdosoPdfGenerator.gerarProntuarioPdf(context, idoso, prescricoes);

        Intent intentTela = new Intent(context, DetalhesIdosoActivity.class);
        intentTela.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, 1);

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intentTela).setup()) {
            DetalhesIdosoActivity activity = controller.get();

            // 1. Abertura via ACTION_VIEW + FileProvider
            FichaIdosoPdfGenerator.abrirPdf(activity, arquivoPdf);
            Intent viewIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(viewIntent);
            assertEquals(Intent.ACTION_VIEW, viewIntent.getAction());
            assertEquals("application/pdf", viewIntent.getType());
            Uri uriView = viewIntent.getData();
            assertNotNull(uriView);
            assertEquals("content", uriView.getScheme());
            assertTrue((viewIntent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);

            // 2. Compartilhamento via ACTION_SEND (Chooser) + FileProvider
            FichaIdosoPdfGenerator.compartilharPdf(activity, arquivoPdf, idoso.getNome());
            Intent chooserIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(chooserIntent);
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());

            Intent sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            assertNotNull(sendIntent);
            assertEquals(Intent.ACTION_SEND, sendIntent.getAction());
            assertEquals("application/pdf", sendIntent.getType());
            assertTrue((sendIntent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Uri streamUri = sendIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            assertNotNull(streamUri);
            assertEquals("content", streamUri.getScheme());
        }
    }

    @Test
    public void detalhesIdosoActivity_deveExportarPdfEmBackgroundParaEnfermeiroEOcultarParaCuidador() throws Exception {
        org.robolectric.shadows.ShadowLog.stream = System.out;

        // 1. Perfil ENFERMEIRO: deve visualizar "Exportar Ficha em PDF" e gerar via ExecutorService
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(2L, "Enf. Helena", "helena@vovolar.org", PerfilUsuario.ENFERMEIRO),
                "Enf@1234"
        );

        Intent intentEnf = new Intent(context, DetalhesIdosoActivity.class);
        intentEnf.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, 1);

        ActivityController<DetalhesIdosoActivity> controller =
                Robolectric.buildActivity(DetalhesIdosoActivity.class, intentEnf).setup();
        try {
            DetalhesIdosoActivity activity = controller.get();
            MaterialButton btnExportar = activity.findViewById(R.id.btn_exportar_pdf);

            assertEquals(View.VISIBLE, btnExportar.getVisibility());
            assertTrue(btnExportar.isEnabled());
            assertEquals("Exportar Ficha em PDF", btnExportar.getText().toString());

            btnExportar.performClick();
            assertNotNull(activity.ultimaTarefaPdf);

            // Aguarda conclusão determinística da thread secundária do ExecutorService
            activity.ultimaTarefaPdf.get(10, java.util.concurrent.TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("A geração assíncrona deve disparar a Intent de abertura do PDF", started);
            assertEquals(Intent.ACTION_VIEW, started.getAction());
            assertEquals("application/pdf", started.getType());
            assertTrue(btnExportar.isEnabled());
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            controller.pause().stop().destroy();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }

        // 2. Perfil CUIDADOR: não deve ter acesso à exportação de prontuário em PDF
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(3L, "Cuidador Paulo", "paulo@vovolar.org", PerfilUsuario.CUIDADOR),
                "Cuida@123"
        );

        ActivityController<DetalhesIdosoActivity> controllerCuidador =
                Robolectric.buildActivity(DetalhesIdosoActivity.class, intentEnf).setup();
        try {
            DetalhesIdosoActivity activity = controllerCuidador.get();
            View layoutPdf = activity.findViewById(R.id.layout_acoes_pdf);
            assertEquals(View.GONE, layoutPdf.getVisibility());
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            controllerCuidador.pause().stop().destroy();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }
}
