package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.ImageUtil;
import com.example.util.SessaoManager;
import com.google.android.material.card.MaterialCardView;
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
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class CapturaFotoPerfilIdosoTest {

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

        ShadowLog.clear();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        limparDiretorio(new File(context.getFilesDir(), ImageUtil.DIRETORIO_FOTOS_PRIVADO));
        limparDiretorio(new File(context.getCacheDir(), ImageUtil.DIRETORIO_CAPTURAS_TEMP));

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Administrador Teste", "admin@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@1234"
        );
    }

    @After
    public void tearDown() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.checkActivities(false);
        limparDiretorio(new File(context.getFilesDir(), ImageUtil.DIRETORIO_FOTOS_PRIVADO));
        limparDiretorio(new File(context.getCacheDir(), ImageUtil.DIRETORIO_CAPTURAS_TEMP));
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void perfilSemFoto_deveExibirPlaceholderEAtualizarParaFotoLocalPrivadaAoCapturarComCameraX() throws Exception {
        IdosoDAO dao = new IdosoDAO(context);
        long idResidente = dao.inserir(new Idoso(
                "Dona Helena Vasconcelos",
                "18/03/1943",
                "A+",
                "Nenhuma",
                "Hipertensa",
                "(51) 99888-7766",
                ""
        ));

        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(3L, "Cuidadora Paula", "paula@vovolar.org", PerfilUsuario.CUIDADOR),
                "Cuida@123"
        );

        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        Intent intent = new Intent(context, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, (int) idResidente);

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intent).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            ImageView ivFoto = activity.findViewById(R.id.iv_detalhe_foto);
            MaterialCardView btnCameraBadge = activity.findViewById(R.id.btn_camera_badge_detalhe);

            // 1. Verifica placeholder inicial e descrição acessível da ação unificada
            assertTrue("Deve exibir placeholder inicialmente", ImageUtil.estaExibindoPlaceholder(ivFoto));
            assertEquals(
                    activity.getString(R.string.btn_capturar_foto_perfil),
                    btnCameraBadge.getContentDescription().toString()
            );

            // 2. Inicia captura com CameraX pelo botão flutuante sobreposto à foto de perfil
            btnCameraBadge.performClick();

            ShadowActivity.IntentForResult intentCameraXDisparada =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull("Activity da CameraX deve ser iniciada", intentCameraXDisparada);
            assertNotNull(intentCameraXDisparada.intent.getComponent());
            assertEquals(
                    CameraFotoIdosoActivity.class.getName(),
                    intentCameraXDisparada.intent.getComponent().getClassName()
            );
            assertEquals(
                    "Dona Helena Vasconcelos",
                    intentCameraXDisparada.intent.getStringExtra(CameraFotoIdosoActivity.EXTRA_NOME_RESIDENTE)
            );

            // 3. Simula retorno da CameraFotoIdosoActivity com foto gravada no armazenamento privado
            String primeiroCaminhoGerado = gerarArquivoFotoPrivadoSimulado(Color.rgb(33, 150, 243));
            Intent retornoCameraX = new Intent().putExtra(
                    CameraFotoIdosoActivity.EXTRA_CAMINHO_FOTO_SALVA,
                    primeiroCaminhoGerado
            );
            Shadows.shadowOf(activity).receiveResult(
                    intentCameraXDisparada.intent,
                    Activity.RESULT_OK,
                    retornoCameraX
            );
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            // 4. Verifica atualização imediata da UI e persistência nos arquivos privados (getFilesDir())
            assertFalse("Não deve mais exibir placeholder após captura", ImageUtil.estaExibindoPlaceholder(ivFoto));
            assertTrue("ImageView deve conter BitmapDrawable da foto capturada",
                    ivFoto.getDrawable() instanceof BitmapDrawable);
            assertEquals(
                    activity.getString(R.string.btn_atualizar_foto_perfil),
                    btnCameraBadge.getContentDescription().toString()
            );

            Idoso atualizadoNoBanco = dao.buscarPorId((int) idResidente);
            assertNotNull(atualizadoNoBanco);
            String primeiroCaminhoPrivado = atualizadoNoBanco.getCaminhoFoto();
            assertEquals(primeiroCaminhoGerado, primeiroCaminhoPrivado);

            File primeiroArquivo = new File(primeiroCaminhoPrivado);
            assertTrue("Arquivo da foto deve existir no disco", primeiroArquivo.exists());
            assertTrue("Arquivo da foto deve ter conteúdo válido", primeiroArquivo.length() > 0);
            assertTrue(
                    "Foto deve ser armazenada nos arquivos privados internos do app (getFilesDir())",
                    primeiroArquivo.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath())
            );

            // 5. Captura uma segunda foto via toque direto no avatar e verifica substituição limpa
            ivFoto.performClick();
            ShadowActivity.IntentForResult segundaCaptura =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(segundaCaptura);
            assertEquals(
                    CameraFotoIdosoActivity.class.getName(),
                    segundaCaptura.intent.getComponent().getClassName()
            );

            String segundoCaminhoGerado = gerarArquivoFotoPrivadoSimulado(Color.rgb(76, 175, 80));
            Shadows.shadowOf(activity).receiveResult(
                    segundaCaptura.intent,
                    Activity.RESULT_OK,
                    new Intent().putExtra(CameraFotoIdosoActivity.EXTRA_CAMINHO_FOTO_SALVA, segundoCaminhoGerado)
            );
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            String segundoCaminhoPrivado = dao.buscarPorId((int) idResidente).getCaminhoFoto();
            assertNotEquals(primeiroCaminhoPrivado, segundoCaminhoPrivado);
            assertFalse("Foto antiga substituída deve ser removida do armazenamento privado",
                    primeiroArquivo.exists());
            assertTrue("Nova foto privada deve existir no disco", new File(segundoCaminhoPrivado).exists());

            // 6. Recria a Activity e garante que a foto persiste sem voltar para o placeholder
            controller.recreate();
            ImageView ivFotoAposRecreate = controller.get().findViewById(R.id.iv_detalhe_foto);
            assertFalse("Foto deve permanecer carregada após recriação da Activity",
                    ImageUtil.estaExibindoPlaceholder(ivFotoAposRecreate));
        }
    }

    @Test
    public void cadastroNovoIdoso_deveCapturarFotoComCameraXEPersistirNosArquivosPrivados() throws Exception {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();
            ImageView ivPreview = activity.findViewById(R.id.iv_preview_foto);

            assertTrue("Preview deve iniciar com placeholder", ImageUtil.estaExibindoPlaceholder(ivPreview));

            ((TextInputEditText) activity.findViewById(R.id.et_nome)).setText("Sr. Oswaldo Montenegro");
            activity.findViewById(R.id.btn_tirar_foto_camera).performClick();

            ShadowActivity.IntentForResult intentCameraX =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(intentCameraX);
            assertNotNull(intentCameraX.intent.getComponent());
            assertEquals(
                    CameraFotoIdosoActivity.class.getName(),
                    intentCameraX.intent.getComponent().getClassName()
            );
            assertEquals(
                    "Sr. Oswaldo Montenegro",
                    intentCameraX.intent.getStringExtra(CameraFotoIdosoActivity.EXTRA_NOME_RESIDENTE)
            );

            String caminhoSalvoCameraX = gerarArquivoFotoPrivadoSimulado(Color.rgb(156, 39, 176));
            Shadows.shadowOf(activity).receiveResult(
                    intentCameraX.intent,
                    Activity.RESULT_OK,
                    new Intent().putExtra(CameraFotoIdosoActivity.EXTRA_CAMINHO_FOTO_SALVA, caminhoSalvoCameraX)
            );
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertFalse("Preview deve exibir a foto capturada", ImageUtil.estaExibindoPlaceholder(ivPreview));

            ((TextInputEditText) activity.findViewById(R.id.et_data_nascimento)).setText("22091940");
            ((TextInputEditText) activity.findViewById(R.id.et_contato_emergencia)).setText("51991112233");

            activity.findViewById(R.id.btn_salvar_cadastro).performClick();
            assertTrue(activity.isFinishing());

            IdosoDAO dao = new IdosoDAO(context);
            Idoso salvo = null;
            for (Idoso item : dao.listarTodos()) {
                if ("Sr. Oswaldo Montenegro".equals(item.getNome())) {
                    salvo = item;
                    break;
                }
            }
            assertNotNull("Residente recém-cadastrado deve estar no SQLite", salvo);
            assertEquals(caminhoSalvoCameraX, salvo.getCaminhoFoto());
            File arquivoSalvo = new File(salvo.getCaminhoFoto());
            assertTrue(arquivoSalvo.exists());
            assertTrue(arquivoSalvo.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()));
        }
    }

    @Test
    public void cancelamentoCameraX_deveManterPlaceholderSemArquivosOrfaosESemLogError() {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        Intent intent = new Intent(context, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_NOME_IDOSO, "Sr. Roberto");
        intent.putExtra(DetalhesIdosoActivity.EXTRA_TELEFONE_FAMILIAR, "(51) 99777-8899");

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intent).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            ImageView ivFoto = activity.findViewById(R.id.iv_detalhe_foto);

            activity.findViewById(R.id.btn_camera_badge_detalhe).performClick();
            ShadowActivity.IntentForResult intentCameraX =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(intentCameraX);

            Shadows.shadowOf(activity).receiveResult(intentCameraX.intent, Activity.RESULT_CANCELED, null);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertTrue("Placeholder deve permanecer ativo após cancelamento",
                    ImageUtil.estaExibindoPlaceholder(ivFoto));

            File dirTemp = new File(context.getCacheDir(), ImageUtil.DIRETORIO_CAPTURAS_TEMP);
            File[] arquivosTempRestantes = dirTemp.listFiles();
            assertEquals("Nenhum arquivo temporário deve sobrar após cancelamento",
                    0, arquivosTempRestantes != null ? arquivosTempRestantes.length : 0);

            for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
                assertFalse("Nenhum Log.ERROR deve ser emitido no fluxo cancelado: " + item.msg,
                        item.type == Log.ERROR);
            }
        }
    }

    @Test
    public void cameraFotoIdosoActivity_deveGerenciarFlashRevisaoRefazerEConfirmacaoPrivada() throws Exception {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        Intent intent = new Intent(context, CameraFotoIdosoActivity.class);
        intent.putExtra(CameraFotoIdosoActivity.EXTRA_NOME_RESIDENTE, "Dona Margarida");

        try (ActivityController<CameraFotoIdosoActivity> controller =
                     Robolectric.buildActivity(CameraFotoIdosoActivity.class, intent).setup()) {
            CameraFotoIdosoActivity activity = controller.get();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            TextView tvSubtitulo = activity.findViewById(R.id.tv_subtitulo_residente_camera);
            assertTrue(tvSubtitulo.getText().toString().contains("Dona Margarida"));

            // Testa alternância de flash (OFF -> AUTO -> ON -> OFF)
            ImageButton btnFlash = activity.findViewById(R.id.btn_alternar_flash);
            assertEquals(activity.getString(R.string.desc_flash_desligado), btnFlash.getContentDescription().toString());
            btnFlash.performClick();
            assertEquals(activity.getString(R.string.desc_flash_auto), btnFlash.getContentDescription().toString());
            btnFlash.performClick();
            assertEquals(activity.getString(R.string.desc_flash_ligado), btnFlash.getContentDescription().toString());
            btnFlash.performClick();
            assertEquals(activity.getString(R.string.desc_flash_desligado), btnFlash.getContentDescription().toString());

            // Simula foto recém-capturada pela CameraX entrando na etapa de revisão
            File capturaTemp1 = gerarArquivoTemporarioSimulado(Color.rgb(255, 152, 0));
            activity.exibirRevisaoFotoCapturada(capturaTemp1);

            View layoutConfirmacao = activity.findViewById(R.id.layout_confirmacao_foto);
            ImageView ivConfirmacao = activity.findViewById(R.id.iv_preview_captura_confirmacao);
            assertEquals(View.VISIBLE, layoutConfirmacao.getVisibility());
            assertFalse(ImageUtil.estaExibindoPlaceholder(ivConfirmacao));

            // Usuário toca em "Refazer": apaga temporário e retorna ao viewfinder
            activity.findViewById(R.id.btn_refazer_foto).performClick();
            assertEquals(View.GONE, layoutConfirmacao.getVisibility());
            assertFalse("Arquivo temporário descartado ao refazer deve ser removido", capturaTemp1.exists());

            // Segunda captura seguida de "Usar Foto": persiste em getFilesDir() e retorna RESULT_OK
            File capturaTemp2 = gerarArquivoTemporarioSimulado(Color.rgb(0, 150, 136));
            activity.exibirRevisaoFotoCapturada(capturaTemp2);
            activity.findViewById(R.id.btn_confirmar_foto).performClick();

            // Aguarda conclusão da gravação assíncrona no cameraExecutor
            long inicio = System.currentTimeMillis();
            while (!activity.isFinishing() && (System.currentTimeMillis() - inicio) < 3000L) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                Thread.sleep(25L);
            }

            assertTrue("Activity deve finalizar após confirmar foto", activity.isFinishing());
            ShadowActivity shadowCamera = Shadows.shadowOf(activity);
            assertEquals(Activity.RESULT_OK, shadowCamera.getResultCode());

            Intent resultIntent = shadowCamera.getResultIntent();
            assertNotNull(resultIntent);
            String caminhoPrivado = resultIntent.getStringExtra(CameraFotoIdosoActivity.EXTRA_CAMINHO_FOTO_SALVA);
            assertTrue(ImageUtil.possuiFotoValida(caminhoPrivado));
            File arquivoFinal = new File(caminhoPrivado);
            assertTrue(arquivoFinal.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()));
            assertFalse("Arquivo temporário de captura deve ser removido após salvar", capturaTemp2.exists());
        }
    }

    @Test
    public void cameraFotoIdosoActivity_semPermissaoCamera_deveExibirCardPermissaoSemCrash() {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.denyPermissions(Manifest.permission.CAMERA);

        try (ActivityController<CameraFotoIdosoActivity> controller =
                     Robolectric.buildActivity(CameraFotoIdosoActivity.class).setup()) {
            CameraFotoIdosoActivity activity = controller.get();
            View cardEstado = activity.findViewById(R.id.layout_estado_camera);
            View btnPermitir = activity.findViewById(R.id.btn_solicitar_permissao_camera);

            assertEquals(View.VISIBLE, cardEstado.getVisibility());
            assertEquals(View.VISIBLE, btnPermitir.getVisibility());
            assertFalse(activity.findViewById(R.id.btn_disparar_captura).isEnabled());
        }
    }

    private String gerarArquivoFotoPrivadoSimulado(int cor) {
        Bitmap bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(cor);
        String caminho = ImageUtil.salvarBitmapNoArmazenamentoPrivado(context, bmp);
        assertNotNull(caminho);
        return caminho;
    }

    private File gerarArquivoTemporarioSimulado(int cor) throws Exception {
        File temp = ImageUtil.criarArquivoTemporarioCaptura(context);
        Bitmap bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(cor);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 90, out));
            out.flush();
        }
        return temp;
    }

    private void limparDiretorio(File dir) {
        if (dir != null && dir.exists()) {
            File[] filhos = dir.listFiles();
            if (filhos != null) {
                for (File f : filhos) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }
}
