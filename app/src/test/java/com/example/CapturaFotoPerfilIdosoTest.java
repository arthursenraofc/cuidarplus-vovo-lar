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
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;

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
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.OutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class CapturaFotoPerfilIdosoTest {

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

        ShadowLog.clear();
        ShadowToast.reset();
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
    public void perfilSemFoto_deveExibirPlaceholderEAtualizarParaFotoLocalPrivadaAoCapturarNaCamera() throws Exception {
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

        // Testa também com perfil CUIDADOR para garantir que a equipe operacional pode fotografar o residente
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

            // 2. Inicia captura de foto pelo botão flutuante sobreposto à foto de perfil
            btnCameraBadge.performClick();

            ShadowActivity.IntentForResult intentCameraDisparada =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull("Intent de câmera deve ser disparada", intentCameraDisparada);
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intentCameraDisparada.intent.getAction());

            Uri outputUri = intentCameraDisparada.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            assertNotNull("EXTRA_OUTPUT via FileProvider deve estar presente", outputUri);
            assertEquals(context.getPackageName() + ".fileprovider", outputUri.getAuthority());

            // 3. Simula gravação de foto real pela câmera do dispositivo no FileProvider Uri
            gravarImagemTesteNoUri(outputUri, Color.rgb(33, 150, 243));
            Shadows.shadowOf(activity).receiveResult(
                    intentCameraDisparada.intent,
                    Activity.RESULT_OK,
                    new Intent()
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
            assertNotNull(primeiroCaminhoPrivado);
            assertFalse(primeiroCaminhoPrivado.isEmpty());

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

            Uri segundoUri = segundaCaptura.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            gravarImagemTesteNoUri(segundoUri, Color.rgb(76, 175, 80));
            Shadows.shadowOf(activity).receiveResult(
                    segundaCaptura.intent,
                    Activity.RESULT_OK,
                    new Intent()
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
    public void cadastroNovoIdoso_deveCapturarFotoPelaCameraEPersistirNosArquivosPrivados() throws Exception {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();
            ImageView ivPreview = activity.findViewById(R.id.iv_preview_foto);

            assertTrue("Preview deve iniciar com placeholder", ImageUtil.estaExibindoPlaceholder(ivPreview));

            activity.findViewById(R.id.btn_tirar_foto_camera).performClick();
            ShadowActivity.IntentForResult intentCamera =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(intentCamera);
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intentCamera.intent.getAction());

            Uri outputUri = intentCamera.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            gravarImagemTesteNoUri(outputUri, Color.rgb(156, 39, 176));
            Shadows.shadowOf(activity).receiveResult(intentCamera.intent, Activity.RESULT_OK, new Intent());
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertFalse("Preview deve exibir a foto capturada", ImageUtil.estaExibindoPlaceholder(ivPreview));

            // Preenche dados obrigatórios e salva
            ((TextInputEditText) activity.findViewById(R.id.et_nome)).setText("Sr. Oswaldo Montenegro");
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
            assertNotNull(salvo.getCaminhoFoto());
            File arquivoSalvo = new File(salvo.getCaminhoFoto());
            assertTrue(arquivoSalvo.exists());
            assertTrue(arquivoSalvo.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()));
        }
    }

    @Test
    public void cancelamentoOuAusenciaDeCamera_deveManterPlaceholderSemArquivosOrfaosESemLogError() {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
        shadowApp.grantPermissions(Manifest.permission.CAMERA);

        Intent intent = new Intent(context, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_NOME_IDOSO, "Sr. Roberto");
        intent.putExtra(DetalhesIdosoActivity.EXTRA_TELEFONE_FAMILIAR, "(51) 99777-8899");

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intent).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            ImageView ivFoto = activity.findViewById(R.id.iv_detalhe_foto);

            // 1. Usuário abre a câmera e cancela (RESULT_CANCELED)
            activity.findViewById(R.id.btn_camera_badge_detalhe).performClick();
            ShadowActivity.IntentForResult intentCamera =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(intentCamera);

            Shadows.shadowOf(activity).receiveResult(intentCamera.intent, Activity.RESULT_CANCELED, null);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertTrue("Placeholder deve permanecer ativo após cancelamento",
                    ImageUtil.estaExibindoPlaceholder(ivFoto));

            File dirTemp = new File(context.getCacheDir(), ImageUtil.DIRETORIO_CAPTURAS_TEMP);
            File[] arquivosTempRestantes = dirTemp.listFiles();
            assertEquals("Nenhum arquivo temporário deve sobrar após cancelamento",
                    0, arquivosTempRestantes != null ? arquivosTempRestantes.length : 0);

            // 2. Dispositivo sem app de câmera instalado (ActivityNotFoundException)
            shadowApp.checkActivities(true);
            ShadowLog.clear();
            ShadowToast.reset();

            activity.findViewById(R.id.btn_camera_badge_detalhe).performClick();
            assertEquals(
                    activity.getString(R.string.erro_camera_indisponivel),
                    ShadowToast.getTextOfLatestToast()
            );

            for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
                assertFalse("Nenhum Log.ERROR deve ser emitido por ausência de app de câmera: " + item.msg,
                        item.type == Log.ERROR);
            }
        }
    }

    private void gravarImagemTesteNoUri(Uri uri, int cor) throws Exception {
        Bitmap bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(cor);
        try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            assertNotNull(out);
            assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 90, out));
            out.flush();
        }
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
