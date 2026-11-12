package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, qualifiers = "w360dp-h800dp-mdpi")
public class MedicamentosLayoutConstraintTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).salvarSessao(
                new Usuario(1L, "Enf.Helena", "helena@vovolar.org", PerfilUsuario.ADMINISTRADOR),
                "Admin@2026"
        );
    }

    @After
    public void tearDown() {
        DatabaseHelper.resetInstanceForTesting();
        SessaoManager.resetInstanceForTesting();
    }

    @Test
    public void testActivityMedicamentosXmlUsaConstraintLayoutECardWrapContentERecyclerView0dp() {
        context.setTheme(R.style.Theme_LarIdosos);
        View root = LayoutInflater.from(context).inflate(R.layout.activity_medicamentos, null, false);
        assertTrue("Container raiz deve ser ConstraintLayout", root instanceof ConstraintLayout);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        MaterialCardView cardAcoes = root.findViewById(R.id.cardAcoes);
        RecyclerView rvMedicamentos = root.findViewById(R.id.rvMedicamentos);
        MaterialButton btnEstoque = root.findViewById(R.id.btnExportarEstoquePdf);
        MaterialButton btnNovo = root.findViewById(R.id.btnNovoMedicamento);

        assertNotNull("Toolbar fixa deve existir", toolbar);
        assertNotNull("Card superior @id/cardAcoes deve existir", cardAcoes);
        assertNotNull("RecyclerView @id/rvMedicamentos deve existir", rvMedicamentos);
        assertNotNull("Botão Estoque deve existir", btnEstoque);
        assertNotNull("Botão Novo Medicamento deve existir", btnNovo);

        assertEquals("Medicamentos Prescritos", toolbar.getTitle().toString());
        assertNotNull("Toolbar deve manter o ícone de fechar (ic_close)", toolbar.getNavigationIcon());

        ConstraintLayout.LayoutParams cardParams =
                (ConstraintLayout.LayoutParams) cardAcoes.getLayoutParams();
        assertEquals(
                "cardAcoes deve ter layout_height=wrap_content",
                ViewGroup.LayoutParams.WRAP_CONTENT,
                cardParams.height
        );
        assertEquals(
                "cardAcoes deve ancorar o topo abaixo da AppBar",
                R.id.appBarLayout,
                cardParams.topToBottom
        );
        assertEquals(
                "cardAcoes não deve ser ancorado na base do pai",
                ConstraintLayout.LayoutParams.UNSET,
                cardParams.bottomToBottom
        );

        ConstraintLayout.LayoutParams rvParams =
                (ConstraintLayout.LayoutParams) rvMedicamentos.getLayoutParams();
        assertEquals(
                "rvMedicamentos deve ter layout_height=0dp (match constraint)",
                0,
                rvParams.height
        );
        assertEquals(
                "rvMedicamentos deve ancorar seu topo na base de @id/cardAcoes",
                R.id.cardAcoes,
                rvParams.topToBottom
        );
        assertEquals(
                "rvMedicamentos deve ancorar seu fundo na base do layout pai (parent)",
                ConstraintLayout.LayoutParams.PARENT_ID,
                rvParams.bottomToBottom
        );
    }

    @Test
    public void testMedicaoRealEmTelaCompactaGaranteEspacoAmploParaRecyclerView() {
        IdosoDAO idosoDAO = new IdosoDAO(context);
        long idosoId = idosoDAO.inserir(new Idoso(
                0,
                "Dona Francisca de Souza",
                "12/05/1942",
                "O+",
                "Dipirona",
                "Hipertensa",
                "(11) 99876-5432",
                null
        ));

        MedicamentoDAO medicamentoDAO = new MedicamentoDAO(context);
        Medicamento med = new Medicamento();
        med.setIdIdoso((int) idosoId);
        med.setNomeMedicamento("Losartana Potássica");
        med.setDosagem("50mg");
        med.setTipo("Comprimido");
        med.setHorarioInicial("08:00");
        med.setFrequenciaHoras(12);
        med.setEstoque("30 comprimidos");
        med.setStatusAtivo(1);
        medicamentoDAO.inserir(med);

        Intent intent = new Intent(context, ListaMedicamentosActivity.class);
        intent.putExtra(ListaMedicamentosActivity.EXTRA_IDOSO_ID, (int) idosoId);
        intent.putExtra(ListaMedicamentosActivity.EXTRA_NOME_IDOSO, "Dona Francisca de Souza");

        try (ActivityController<ListaMedicamentosActivity> controller =
                     Robolectric.buildActivity(ListaMedicamentosActivity.class, intent).setup()) {
            ListaMedicamentosActivity activity = controller.get();

            View root = activity.findViewById(R.id.rootMedicamentos);
            MaterialCardView cardAcoes = activity.findViewById(R.id.cardAcoes);
            RecyclerView rvMedicamentos = activity.findViewById(R.id.rvMedicamentos);
            MaterialButton btnEstoque = activity.findViewById(R.id.btnExportarEstoquePdf);
            MaterialButton btnNovo = activity.findViewById(R.id.btnNovoMedicamento);

            int widthSpec = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
            root.measure(widthSpec, heightSpec);
            root.layout(0, 0, 360, 800);

            assertTrue(
                    "cardAcoes deve permanecer compacto (< 220px em tela mdpi de 800px), atual: " + cardAcoes.getMeasuredHeight(),
                    cardAcoes.getMeasuredHeight() > 0 && cardAcoes.getMeasuredHeight() < 220
            );
            assertTrue(
                    "rvMedicamentos deve ocupar a maior parte da altura livre (> 500px em tela de 800px), atual: " + rvMedicamentos.getMeasuredHeight(),
                    rvMedicamentos.getMeasuredHeight() > 500
            );
            assertEquals(View.VISIBLE, rvMedicamentos.getVisibility());

            // Navegação dos botões de ação dentro do card superior
            btnEstoque.performClick();
            ShadowApplication shadowApp = Shadows.shadowOf((Application) context);
            Intent nextEstoque = shadowApp.getNextStartedActivity();
            assertNotNull(nextEstoque);
            assertEquals(EstoqueActivity.class.getName(), nextEstoque.getComponent().getClassName());

            btnNovo.performClick();
            Intent nextCadastro = shadowApp.getNextStartedActivity();
            assertNotNull(nextCadastro);
            assertEquals(CadastroMedicamentoActivity.class.getName(), nextCadastro.getComponent().getClassName());
        }
    }
}
