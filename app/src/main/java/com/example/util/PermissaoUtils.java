package com.example.util;

import android.content.Context;
import android.util.Log;

import com.example.database.AcessoNaoAutorizadoException;
import com.example.model.PerfilUsuario;

/**
 * Classe utilitária central de validação de acesso baseada em perfis/papéis (RBAC).
 * Concentra as regras de negócio de autorização da ILPI Vovô Lar tanto para a
 * camada de interface com o usuário (UI) quanto para as travas de segurança dos DAOs.
 */
public final class PermissaoUtils {

    private static final String TAG = "PermissaoUtils";

    private PermissaoUtils() {
        // Classe utilitária estática
    }

    // =========================================================================
    // 1. Verificações Baseadas em PerfilUsuario
    // =========================================================================

    /**
     * Apenas ADMINISTRADOR e ENFERMEIRO possuem privilégios para editar prescrições e medicamentos.
     */
    public static boolean podeEditarMedicamento(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR || perfil == PerfilUsuario.ENFERMEIRO;
    }

    /**
     * Apenas ADMINISTRADOR pode excluir o cadastro de um residente/idoso.
     */
    public static boolean podeExcluirIdoso(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR;
    }

    /**
     * ADMINISTRADOR e ENFERMEIRO podem emitir e exportar relatórios gerenciais e clínicos em PDF.
     */
    public static boolean podeGerarRelatorio(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR || perfil == PerfilUsuario.ENFERMEIRO;
    }

    /**
     * Todos os profissionais (ADMINISTRADOR, ENFERMEIRO e CUIDADOR) podem confirmar a aplicação de doses diárias.
     */
    public static boolean podeConfirmarDose(PerfilUsuario perfil) {
        return perfil != null;
    }

    /**
     * Apenas ADMINISTRADOR pode cadastrar novos idosos/residentes na instituição.
     */
    public static boolean podeCadastrarIdoso(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR;
    }

    /**
     * Apenas ADMINISTRADOR pode editar dados cadastrais e fotos do idoso.
     */
    public static boolean podeEditarIdoso(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR;
    }

    /**
     * ADMINISTRADOR e ENFERMEIRO podem criar novas prescrições e medicamentos.
     */
    public static boolean podeCadastrarMedicamento(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR || perfil == PerfilUsuario.ENFERMEIRO;
    }

    /**
     * ADMINISTRADOR e ENFERMEIRO podem excluir medicamentos da grade.
     */
    public static boolean podeExcluirMedicamento(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR || perfil == PerfilUsuario.ENFERMEIRO;
    }

    /**
     * Todos os perfis podem consultar a listagem consolidada de estoque.
     */
    public static boolean podeAcessarEstoque(PerfilUsuario perfil) {
        return perfil != null;
    }

    /**
     * Todos os perfis podem acionar familiares pelo WhatsApp em situações de rotina ou reposição.
     */
    public static boolean podeEnviarMensagemWhatsApp(PerfilUsuario perfil) {
        return perfil != null;
    }

    /**
     * Apenas ADMINISTRADOR pode modificar parâmetros e configurações mestras do sistema.
     */
    public static boolean podeAlterarConfiguracoes(PerfilUsuario perfil) {
        if (perfil == null) return false;
        return perfil == PerfilUsuario.ADMINISTRADOR;
    }

    // =========================================================================
    // 2. Sobrecargas com Contexto (consulta automática da sessão ativa)
    // =========================================================================

    public static boolean podeEditarMedicamento(Context context) {
        return podeEditarMedicamento(SessaoManager.getPerfil(context));
    }

    public static boolean podeExcluirIdoso(Context context) {
        return podeExcluirIdoso(SessaoManager.getPerfil(context));
    }

    public static boolean podeGerarRelatorio(Context context) {
        return podeGerarRelatorio(SessaoManager.getPerfil(context));
    }

    public static boolean podeConfirmarDose(Context context) {
        return podeConfirmarDose(SessaoManager.getPerfil(context));
    }

    public static boolean podeCadastrarIdoso(Context context) {
        return podeCadastrarIdoso(SessaoManager.getPerfil(context));
    }

    public static boolean podeEditarIdoso(Context context) {
        return podeEditarIdoso(SessaoManager.getPerfil(context));
    }

    /**
     * Todos os perfis autenticados na operação da ILPI podem capturar/atualizar a foto de identificação do residente.
     */
    public static boolean podeAtualizarFotoPerfil(PerfilUsuario perfil) {
        return perfil != null;
    }

    public static boolean podeAtualizarFotoPerfil(Context context) {
        return podeAtualizarFotoPerfil(SessaoManager.getPerfil(context));
    }

    public static boolean podeCadastrarMedicamento(Context context) {
        return podeCadastrarMedicamento(SessaoManager.getPerfil(context));
    }

    public static boolean podeExcluirMedicamento(Context context) {
        return podeExcluirMedicamento(SessaoManager.getPerfil(context));
    }

    public static boolean podeAlterarConfiguracoes(Context context) {
        return podeAlterarConfiguracoes(SessaoManager.getPerfil(context));
    }

    // =========================================================================
    // 3. Validação com Disparo de Exceção (Trava da Camada de Negócio / DAO)
    // =========================================================================

    /**
     * Valida se uma operação é permitida. Caso contrário, dispara AcessoNaoAutorizadoException.
     */
    public static void validarOuLancar(boolean permitido, PerfilUsuario perfil, String acao) {
        if (!permitido) {
            Log.e(TAG, "Violação de segurança RBAC: " + acao + " negado para o perfil: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, acao);
        }
    }
}
