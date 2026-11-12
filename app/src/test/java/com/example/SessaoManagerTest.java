package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.example.model.PerfilUsuario;
import com.example.model.Usuario;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class SessaoManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SessaoManager.resetInstanceForTesting();
        SessaoManager.getInstance(context).revogarCredenciaisESessao();
        SessaoManager.resetInstanceForTesting();
    }

    @Test
    public void deveSalvarEAutenticarCredenciaisOfflineComPerfilCorreto() {
        SessaoManager sessao = SessaoManager.getInstance(context);
        Usuario enfermeira = new Usuario(42L, "Enf. Helena", "helena@vovolar.org", PerfilUsuario.ENFERMEIRO);

        sessao.salvarCredenciaisOffline(enfermeira, "Pin#2026");
        assertTrue(sessao.possuiCredenciaisOffline());

        // Senha incorreta deve ser bloqueada
        assertFalse(sessao.autenticarOffline("helena@vovolar.org", "SenhaErrada"));

        // Credenciais corretas devem autenticar e carregar perfil ENFERMEIRO
        assertTrue(sessao.autenticarOffline("HELENA@vovolar.org", "Pin#2026"));
        assertTrue(sessao.isSessaoValida());
        assertEquals(PerfilUsuario.ENFERMEIRO, sessao.getPerfilAtivo());
        assertEquals("Enf. Helena", sessao.getNomeUsuario());

        // Matriz RBAC integrada ao SessaoManager
        assertTrue(PermissaoUtils.podeEditarMedicamento(context));
        assertFalse(PermissaoUtils.podeExcluirIdoso(context));
    }

    @Test
    public void logoutDeveEncerrarSessaoEAplicarMenorPrivilegioPreservandoCredenciaisOffline() {
        SessaoManager sessao = SessaoManager.getInstance(context);
        Usuario admin = new Usuario(1L, "Coord. Carlos", "carlos@vovolar.org", PerfilUsuario.ADMINISTRADOR);
        sessao.salvarSessao(admin, "Admin@9988");

        assertTrue(sessao.isSessaoValida());
        assertEquals(PerfilUsuario.ADMINISTRADOR, SessaoManager.getPerfil(context));

        sessao.realizarLogout();

        assertFalse(sessao.isSessaoValida());
        assertEquals(PerfilUsuario.CUIDADOR, SessaoManager.getPerfil(context));
        assertTrue(sessao.possuiCredenciaisOffline());

        // Permite novo login offline sem conexão com nuvem
        assertTrue(sessao.autenticarOffline("carlos@vovolar.org", "Admin@9988"));
        assertTrue(sessao.isSessaoValida());
        assertEquals(PerfilUsuario.ADMINISTRADOR, SessaoManager.getPerfil(context));
    }

    @Test
    public void deveMigrarPreferenciasLegadasELimparTextoPlano() {
        SharedPreferences legacy = context.getSharedPreferences("CuidarPlusPrefs", Context.MODE_PRIVATE);
        legacy.edit()
                .putString("perfil_usuario", PerfilUsuario.ENFERMEIRO.getCodigo())
                .putString("usuario_nome", "Enf. Marcos")
                .commit();

        SessaoManager.resetInstanceForTesting();
        SessaoManager sessao = SessaoManager.getInstance(context);

        assertEquals(PerfilUsuario.ENFERMEIRO, sessao.getPerfilAtivo());
        assertEquals("Enf. Marcos", sessao.getNomeUsuario());
        assertTrue(legacy.getAll().isEmpty());
    }
}
