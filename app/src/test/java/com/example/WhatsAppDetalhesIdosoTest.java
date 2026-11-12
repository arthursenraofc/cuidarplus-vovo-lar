package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.SessaoManager;
import com.example.util.WhatsAppUtils;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowToast;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class WhatsAppDetalhesIdosoTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ShadowLog.clear();
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
        ShadowApplication shadowApp = Shadows.shadowOf((android.app.Application) context);
        shadowApp.checkActivities(false);
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void higienizacaoTelefone_deveRemoverSimbolosEAdicionarDdiBrasilAutomaticamente() {
        // Celular formatado sem DDI
        assertEquals("5551998765432",
                DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("(51) 99876-5432"));

        // Fixo formatado sem DDI
        assertEquals("551134567890",
                DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("(11) 3456-7890"));

        // Já com DDI +55 não deve duplicar o prefixo
        assertEquals("5551998765432",
                DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("+55 (51) 99876-5432"));

        // Caso especial DDD 55 (RS - 11 dígitos): deve receber o DDI 55 totalizando 13 dígitos
        assertEquals("5555991234567",
                DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("(55) 99123-4567"));

        // Contato com nome do familiar + telefone
        assertEquals("5551999998888",
                DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("Filha Ana - (51) 99999-8888"));

        // Telefone nulo ou sem dígitos suficientes
        assertEquals("", DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil(null));
        assertEquals("", DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("   "));
        assertEquals("", DetalhesIdosoActivity.higienizarTelefoneComDdiBrasil("12345"));
    }

    @Test
    public void botaoWhatsApp_devePossuirEstiloMaterial3EDispararIntentComUrlCodificada() throws Exception {
        // Cadastra residente no SQLite como Administrador e depois acessa como Cuidador
        IdosoDAO dao = new IdosoDAO(context);
        long idCriado = dao.inserir(new Idoso(
                "Dona Maria Helena",
                "12/05/1942",
                "O+",
                "Dipirona",
                "Hipertensa",
                "(51) 99876-5432",
                ""
        ));

        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(3L, "Cuidadora Joana", "joana@vovolar.org", PerfilUsuario.CUIDADOR),
                "Cuida@123"
        );

        Intent intent = new Intent(context, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, (int) idCriado);

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intent).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            MaterialButton btnWhatsApp = activity.findViewById(R.id.btn_whatsapp_contato);

            assertNotNull(btnWhatsApp);
            assertEquals(View.VISIBLE, btnWhatsApp.getVisibility());
            assertTrue(btnWhatsApp.isEnabled());
            assertEquals("Contatar Familiar via WhatsApp", btnWhatsApp.getText().toString());
            assertNotNull(btnWhatsApp.getIcon());
            assertNotNull(btnWhatsApp.getBackgroundTintList());
            assertEquals(Color.parseColor("#25D366"), btnWhatsApp.getBackgroundTintList().getDefaultColor());

            float density = activity.getResources().getDisplayMetrics().density;
            assertEquals(Math.round(12f * density), btnWhatsApp.getCornerRadius());

            // Clica no botão do WhatsApp
            btnWhatsApp.performClick();

            Intent startedIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(startedIntent);
            assertEquals(Intent.ACTION_VIEW, startedIntent.getAction());

            Uri data = startedIntent.getData();
            assertNotNull(data);
            assertTrue(data.toString().startsWith("https://api.whatsapp.com/send?phone=5551998765432&text="));

            String rawUrl = data.toString();
            String encodedText = rawUrl.substring(rawUrl.indexOf("&text=") + 6);
            String decodedMessage = URLDecoder.decode(encodedText, StandardCharsets.UTF_8.name());
            assertEquals(
                    "Olá! Entro em contato da ILPI Vovô Lar a respeito do(a) residente Dona Maria Helena. Como podemos ajudar?",
                    decodedMessage
            );
        }
    }

    @Test
    public void extrasDiretosIntent_devemFuncionarETelefoneVazioOuSemWhatsAppDeveExibirToastAmigavel() {
        // 1. Cenário com telefone vazio passado via Intent ("nomeIdoso" e "telefoneFamiliar")
        Intent intentSemTelefone = new Intent(context, DetalhesIdosoActivity.class);
        intentSemTelefone.putExtra(DetalhesIdosoActivity.EXTRA_NOME_IDOSO, "Sr. Antônio Carlos");
        intentSemTelefone.putExtra(DetalhesIdosoActivity.EXTRA_TELEFONE_FAMILIAR, "");

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intentSemTelefone).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            MaterialButton btnWhatsApp = activity.findViewById(R.id.btn_whatsapp_contato);

            ShadowToast.reset();
            btnWhatsApp.performClick();

            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertEquals(
                    activity.getString(R.string.erro_telefone_familiar_vazio),
                    ShadowToast.getTextOfLatestToast()
            );
        }

        // 2. Cenário em que o WhatsApp não está instalado no dispositivo (ActivityNotFoundException)
        ShadowApplication shadowApp = Shadows.shadowOf((android.app.Application) context);
        shadowApp.checkActivities(true);

        Intent intentComTelefone = new Intent(context, DetalhesIdosoActivity.class);
        intentComTelefone.putExtra(DetalhesIdosoActivity.EXTRA_NOME_IDOSO, "Sr. Antônio Carlos");
        intentComTelefone.putExtra(DetalhesIdosoActivity.EXTRA_TELEFONE_FAMILIAR, "(51) 98888-7777");

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intentComTelefone).setup()) {
            DetalhesIdosoActivity activity = controller.get();
            MaterialButton btnWhatsApp = activity.findViewById(R.id.btn_whatsapp_contato);

            ShadowToast.reset();
            ShadowLog.clear();
            btnWhatsApp.performClick();

            assertEquals(
                    activity.getString(R.string.erro_whatsapp_nao_instalado),
                    ShadowToast.getTextOfLatestToast()
            );

            for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
                assertFalse("Nenhum Log.ERROR deve ser emitido por ausência do WhatsApp: " + item.msg,
                        item.type == Log.ERROR);
            }
        }
    }

    @Test
    public void whatsAppUtils_semWhatsAppInstalado_naoDeveEmitirLogErrorEDeveOferecerSmsECompartilhamento() {
        ShadowApplication shadowApp = Shadows.shadowOf((android.app.Application) context);
        shadowApp.checkActivities(true);

        Intent intentActivity = new Intent(context, DetalhesIdosoActivity.class);
        intentActivity.putExtra(DetalhesIdosoActivity.EXTRA_NOME_IDOSO, "Dona Clara");
        intentActivity.putExtra(DetalhesIdosoActivity.EXTRA_TELEFONE_FAMILIAR, "(51) 99777-6655");

        try (ActivityController<DetalhesIdosoActivity> controller =
                     Robolectric.buildActivity(DetalhesIdosoActivity.class, intentActivity).setup()) {
            DetalhesIdosoActivity activity = controller.get();

            ShadowToast.reset();
            ShadowLog.clear();

            String msg = WhatsAppUtils.gerarMensagemReposicaoMedicamento("Dona Clara", "Losartana", "50mg");
            WhatsAppUtils.abrirConversaFamiliar(activity, "(51) 99777-6655", msg);

            // 1. Verifica que o Toast amigável foi exibido
            assertEquals(
                    activity.getString(R.string.erro_whatsapp_nao_instalado),
                    ShadowToast.getTextOfLatestToast()
            );

            // 2. Verifica que NENHUM Log.e foi emitido pela tag WhatsAppUtils
            List<ShadowLog.LogItem> logsWhatsApp = ShadowLog.getLogsForTag("WhatsAppUtils");
            for (ShadowLog.LogItem logItem : logsWhatsApp) {
                assertFalse("Log.ERROR não deve ser emitido quando WhatsApp não está instalado",
                        logItem.type == Log.ERROR);
            }

            // 3. Verifica que o diálogo de alternativas (SMS / Compartilhar) foi aberto
            Dialog ultimoDialogo = ShadowAlertDialog.getLatestDialog();
            assertNotNull("Diálogo de alternativas deve ser exibido quando WhatsApp não está instalado", ultimoDialogo);
            assertTrue(ultimoDialogo.isShowing());
            assertTrue(ultimoDialogo instanceof AlertDialog);

            // 4. Desativa checkActivities para validar o Intent de SMS gerado pelo botão positivo
            shadowApp.checkActivities(false);
            AlertDialog alertDialog = (AlertDialog) ultimoDialogo;
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            Intent smsIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(smsIntent);
            assertEquals(Intent.ACTION_SENDTO, smsIntent.getAction());
            assertEquals("smsto:+5551997776655", smsIntent.getDataString());
            assertEquals(msg, smsIntent.getStringExtra("sms_body"));
        }
    }
}
