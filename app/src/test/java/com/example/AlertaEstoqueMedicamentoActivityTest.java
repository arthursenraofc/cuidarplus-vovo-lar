package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.EstoqueAlertaManager;
import com.example.util.SessaoManager;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class AlertaEstoqueMedicamentoActivityTest {

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
                new Usuario(1L, "Enf. Helena", "helena@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@2026"
        );
    }

    @After
    public void tearDown() {
        DatabaseHelper.resetInstanceForTesting();
        SessaoManager.resetInstanceForTesting();
    }

    @Test
    public void testIconeEBadgeFicamVermelhosQuandoEstoqueInferiorAoLimiteMinimoEMudamAoReconfigurar() {
        int corVermelhoAlerta = ContextCompat.getColor(context, R.color.danger_red);
        int corVerdeOk = ContextCompat.getColor(context, R.color.estoque_ok_fg);

        try (ActivityController<MedicamentoActivity> controller =
                     Robolectric.buildActivity(MedicamentoActivity.class).setup()) {
            MedicamentoActivity activity = controller.get();

            MaterialCardView cardEstoque = activity.findViewById(R.id.cardEstoque);
            ImageView ivIcone = activity.findViewById(R.id.ivIconeAlertaEstoque);
            TextView tvBadge = activity.findViewById(R.id.tvBadgeAlertaEstoque);
            TextView tvTitulo = activity.findViewById(R.id.tvTituloCardEstoque);
            TextView tvSubtitulo = activity.findViewById(R.id.tvSubtituloCardEstoque);
            ImageButton btnConfigurar = activity.findViewById(R.id.btnConfigurarLimiteEstoque);

            assertNotNull(cardEstoque);
            assertNotNull(ivIcone);
            assertNotNull(tvBadge);
            assertNotNull(btnConfigurar);

            // 1. No banco inicial: Losartana (28), Metformina (45) e Prolopa (4 comprimidos).
            // Com o limite mínimo padrão = 10, Prolopa (4 < 10) dispara o alerta VERMELHO!
            assertEquals(10, activity.getLimiteMinimoEstoque());
            assertTrue("Deve estar em estado de alerta crítico", activity.isAlertaEstoqueAtivo());
            assertEquals(corVermelhoAlerta, cardEstoque.getStrokeColor());

            ColorStateList tintIconeInicial = ImageViewCompat.getImageTintList(ivIcone);
            assertNotNull(tintIconeInicial);
            assertEquals(corVermelhoAlerta, tintIconeInicial.getDefaultColor());

            ColorStateList tintBadgeInicial = ViewCompat.getBackgroundTintList(tvBadge);
            assertNotNull(tintBadgeInicial);
            assertEquals(corVermelhoAlerta, tintBadgeInicial.getDefaultColor());
            assertEquals("! 1", tvBadge.getText().toString());
            assertEquals(context.getString(R.string.card_estoque_alerta_titulo), tvTitulo.getText().toString());
            assertTrue(tvSubtitulo.getText().toString().contains("10"));

            // 2. Abre diálogo pelo botão de configuração e reduz o limite mínimo para 3 unidades.
            // Agora todos os itens (28, 45, 4) são >= 3 -> Alerta desativa e muda para VERDE (OK)!
            btnConfigurar.performClick();
            ShadowLooper.idleMainLooper();

            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull("Diálogo de configuração de limite mínimo deve abrir", dialog);
            assertTrue(dialog.isShowing());

            EditText editLimite = dialog.findViewById(android.R.id.edit);
            assertNotNull(editLimite);
            assertEquals("10", editLimite.getText().toString());

            editLimite.setText("3");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            assertFalse("Diálogo deve fechar após salvar limite válido", dialog.isShowing());
            assertEquals(3, EstoqueAlertaManager.getLimiteMinimoEstoque(context));
            assertFalse("Não deve mais haver alerta com limite mínimo = 3", activity.isAlertaEstoqueAtivo());

            ColorStateList tintIconeOk = ImageViewCompat.getImageTintList(ivIcone);
            assertNotNull(tintIconeOk);
            assertEquals(corVerdeOk, tintIconeOk.getDefaultColor());

            ColorStateList tintBadgeOk = ViewCompat.getBackgroundTintList(tvBadge);
            assertNotNull(tintBadgeOk);
            assertEquals(corVerdeOk, tintBadgeOk.getDefaultColor());
            assertEquals("OK", tvBadge.getText().toString());
            assertEquals(context.getString(R.string.card_estoque_regular_titulo), tvTitulo.getText().toString());

            // 3. Aumenta o limite mínimo para 30 unidades: Losartana (28 < 30) e Prolopa (4 < 30) -> 2 alertas vermelhos!
            btnConfigurar.performClick();
            ShadowLooper.idleMainLooper();
            AlertDialog dialog2 = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog2);

            EditText editLimite2 = dialog2.findViewById(android.R.id.edit);
            assertNotNull(editLimite2);
            editLimite2.setText("30");
            dialog2.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            assertTrue(activity.isAlertaEstoqueAtivo());
            assertEquals(2, activity.getUltimoResumoEstoque().getQuantidadeAbaixoDoLimite());
            assertEquals("! 2", tvBadge.getText().toString());
            assertEquals(corVermelhoAlerta, ViewCompat.getBackgroundTintList(tvBadge).getDefaultColor());
            assertEquals(corVermelhoAlerta, ImageViewCompat.getImageTintList(ivIcone).getDefaultColor());

            // 4. Clicar no card de estoque abre EstoqueActivity
            cardEstoque.performClick();
            ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
            Intent nextIntent = shadowApp.getNextStartedActivity();
            assertNotNull(nextIntent);
            assertEquals(EstoqueActivity.class.getName(), nextIntent.getComponent().getClassName());
        }
    }
}
