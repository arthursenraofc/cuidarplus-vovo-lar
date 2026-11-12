package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.InputType;
import android.widget.Button;

import androidx.test.core.app.ApplicationProvider;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.MascaraDataWatcher;
import com.example.util.MascaraTelefoneWatcher;
import com.example.util.SessaoManager;
import com.example.util.ValidadorCadastroUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class MascaraEValidacaoCadastroIdosoTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
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
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
        DatabaseHelper.resetInstanceForTesting();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void mascaraDataWatcher_deveFormatarEmTempoRealELimitarDezCaracteres() {
        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();
            TextInputEditText etData = activity.findViewById(R.id.et_data_nascimento);

            // Verifica inputType="number" configurado no XML
            assertEquals(InputType.TYPE_CLASS_NUMBER,
                    etData.getInputType() & InputType.TYPE_MASK_CLASS);

            // Digitação progressiva
            etData.setText("15");
            assertEquals("15", etData.getText().toString());

            etData.setText("150");
            assertEquals("15/0", etData.getText().toString());

            etData.setText("15081");
            assertEquals("15/08/1", etData.getText().toString());

            etData.setText("15081945");
            assertEquals("15/08/1945", etData.getText().toString());
            assertEquals(10, etData.getText().length());

            // Ignora letras e limita a 8 dígitos (10 caracteres com barras)
            assertEquals("15/08/1945", MascaraDataWatcher.formatarData("15a08b1945999"));
        }
    }

    @Test
    public void mascaraTelefoneWatcher_deveAlternarDinamicamenteEntreFixoECelular() {
        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();
            TextInputEditText etTelefone = activity.findViewById(R.id.et_contato_emergencia);

            // Verifica inputType="phone" configurado no XML
            assertEquals(InputType.TYPE_CLASS_PHONE,
                    etTelefone.getInputType() & InputType.TYPE_MASK_CLASS);

            // Telefone fixo (10 dígitos): (XX) XXXX-XXXX
            etTelefone.setText("5133221122");
            assertEquals("(51) 3322-1122", etTelefone.getText().toString());

            // Transição dinâmica para celular (11 dígitos): (XX) XXXXX-XXXX
            etTelefone.setText("51998765432");
            assertEquals("(51) 99876-5432", etTelefone.getText().toString());

            // Exclusão do último dígito retorna ao formato de 10 dígitos: (XX) XXXX-XXXX
            etTelefone.getText().delete(etTelefone.getText().length() - 1, etTelefone.getText().length());
            assertEquals("(51) 9987-6543", etTelefone.getText().toString());
        }
    }

    @Test
    public void validacaoDataETelefone_deveRejeitarEntradasInvalidasEAceitarValidas() {
        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();
            TextInputLayout tilData = activity.findViewById(R.id.til_data_nascimento);
            TextInputLayout tilTelefone = activity.findViewById(R.id.til_contato_emergencia);

            // Datas inválidas
            assertFalse(ValidadorCadastroUtils.validarDataNascimento("31/02/1945", tilData));
            assertNotNull(tilData.getError());

            assertFalse(ValidadorCadastroUtils.validarDataNascimento("29/02/1945", tilData)); // 1945 não é bissexto
            assertNotNull(tilData.getError());

            assertFalse(ValidadorCadastroUtils.validarDataNascimento("15/13/1945", tilData)); // mês 13
            assertNotNull(tilData.getError());

            assertFalse(ValidadorCadastroUtils.validarDataNascimento("10/05/2018", tilData)); // não é idoso
            assertNotNull(tilData.getError());

            // Ano bissexto válido (1944)
            assertTrue(ValidadorCadastroUtils.validarDataNascimento("29/02/1944", tilData));
            assertNull(tilData.getError());

            // Telefones inválidos
            assertFalse(ValidadorCadastroUtils.validarTelefone("(51) 9987-654", tilTelefone)); // 9 dígitos
            assertNotNull(tilTelefone.getError());

            assertFalse(ValidadorCadastroUtils.validarTelefone("(00) 99876-5432", tilTelefone)); // DDD 00
            assertNotNull(tilTelefone.getError());

            // Telefones válidos (10 e 11 dígitos)
            assertTrue(ValidadorCadastroUtils.validarTelefone("(51) 3322-4455", tilTelefone));
            assertNull(tilTelefone.getError());

            assertTrue(ValidadorCadastroUtils.validarTelefone("(51) 99876-5432", tilTelefone));
            assertNull(tilTelefone.getError());
        }
    }

    @Test
    public void salvarCadastro_deveBloquearDadosInvalidosEPersistirDadosHigienizadosNoSQLite() {
        try (ActivityController<CadastroIdosoActivity> controller =
                     Robolectric.buildActivity(CadastroIdosoActivity.class).setup()) {
            CadastroIdosoActivity activity = controller.get();

            TextInputEditText etNome = activity.findViewById(R.id.et_nome);
            TextInputEditText etData = activity.findViewById(R.id.et_data_nascimento);
            TextInputEditText etTelefone = activity.findViewById(R.id.et_contato_emergencia);
            TextInputLayout tilData = activity.findViewById(R.id.til_data_nascimento);
            TextInputLayout tilTelefone = activity.findViewById(R.id.til_contato_emergencia);
            Button btnSalvar = activity.findViewById(R.id.btn_salvar_cadastro);

            IdosoDAO dao = new IdosoDAO(activity);
            int totalInicial = dao.listarTodos().size();

            // 1. Tenta salvar com data inexistente e telefone incompleto
            etNome.setText("  Sr. Joaquim   Almeida  ");
            etData.setText("31041942"); // 31/04 não existe
            etTelefone.setText("519999"); // apenas 6 dígitos
            btnSalvar.performClick();

            assertFalse(activity.isFinishing());
            assertNotNull(tilData.getError());
            assertNotNull(tilTelefone.getError());
            assertEquals(totalInicial, dao.listarTodos().size());

            // 2. Corrige para data e celular válidos e salva
            etData.setText("14071942");
            etTelefone.setText("51988776655");
            assertNull(tilData.getError());
            assertNull(tilTelefone.getError());

            btnSalvar.performClick();
            assertTrue(activity.isFinishing());

            List<Idoso> listaPosSalvar = dao.listarTodos();
            assertEquals(totalInicial + 1, listaPosSalvar.size());

            Idoso salvo = null;
            for (Idoso item : listaPosSalvar) {
                if ("Sr. Joaquim Almeida".equals(item.getNome())) {
                    salvo = item;
                    break;
                }
            }
            assertNotNull(salvo);
            assertEquals("14/07/1942", salvo.getDataNascimento());
            assertEquals("(51) 98877-6655", salvo.getContatoEmergencia());
        }
    }
}
