package com.example.database;

import com.example.model.PerfilUsuario;

/**
 * Exceção lançada pela camada de persistência (DAOs) ou utilitários de segurança
 * quando uma operação de inserção, alteração ou exclusão é requisitada por um perfil
 * sem os privilégios necessários na matriz RBAC.
 */
public class AcessoNaoAutorizadoException extends SecurityException {

    private final PerfilUsuario perfil;
    private final String acao;

    public AcessoNaoAutorizadoException(String mensagem) {
        super(mensagem);
        this.perfil = null;
        this.acao = null;
    }

    public AcessoNaoAutorizadoException(PerfilUsuario perfil, String acao) {
        super("Acesso negado: o perfil [" + (perfil != null ? perfil.getTitulo() : "Desconhecido")
                + "] não possui permissão para executar a ação: " + acao);
        this.perfil = perfil;
        this.acao = acao;
    }

    public PerfilUsuario getPerfil() {
        return perfil;
    }

    public String getAcao() {
        return acao;
    }
}
