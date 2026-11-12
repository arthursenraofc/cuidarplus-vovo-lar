package com.example.model;

import java.io.Serializable;

/**
 * Entidade de Usuário do sistema CuidarPlus para autenticação e sessão local offline (Local-First).
 */
public class Usuario implements Serializable {

    private long id;
    private String nome;
    private String email;
    private PerfilUsuario perfil;
    private long ultimoLoginMillis;

    public Usuario() {
        this.perfil = PerfilUsuario.ADMINISTRADOR;
    }

    public Usuario(long id, String nome, String email, PerfilUsuario perfil) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.perfil = perfil != null ? perfil : PerfilUsuario.ADMINISTRADOR;
        this.ultimoLoginMillis = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PerfilUsuario getPerfil() {
        return perfil;
    }

    public void setPerfil(PerfilUsuario perfil) {
        this.perfil = perfil != null ? perfil : PerfilUsuario.ADMINISTRADOR;
    }

    public long getUltimoLoginMillis() {
        return ultimoLoginMillis;
    }

    public void setUltimoLoginMillis(long ultimoLoginMillis) {
        this.ultimoLoginMillis = ultimoLoginMillis;
    }
}
