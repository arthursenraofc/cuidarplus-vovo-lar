package com.example.model;

import java.io.Serializable;

/**
 * Modelo que representa um medicamento prescrito para um residente (Sprint 2 Avançado).
 * Inclui tipo/forma farmacêutica e observações médicas específicas.
 */
public class Medicamento implements Serializable {

    private int id;
    private int idIdoso;
    private String nomeMedicamento;
    private String dosagem;            // Ex: "50mg", "2 comprimidos", "10 gotas"
    private String tipo;               // Ex: "Comprimido", "Gotas", "Injeção", "Pomada"
    private String horarioInicial;     // Formato HH:mm (Ex: "08:00")
    private int frequenciaHoras;       // Intervalo em horas (Ex: 4, 6, 8, 12, 24)
    private String observacaoMedica;   // Ex: "Administrar após café da manhã com água"
    private int statusAtivo;           // 1 para Ativo, 0 para Inativo
    
    // Campo auxiliar/transitório para exibição
    private String nomeIdoso;

    public Medicamento() {
        this.tipo = "Comprimido";
        this.statusAtivo = 1;
        this.observacaoMedica = "";
    }

    public Medicamento(int idosoId, String nome, String dosagem, String horario, String observacoes) {
        this.idIdoso = idosoId;
        this.nomeMedicamento = nome;
        this.dosagem = dosagem;
        this.tipo = "Comprimido";
        this.horarioInicial = horario;
        this.frequenciaHoras = 8;
        this.observacaoMedica = observacoes != null ? observacoes : "";
        this.statusAtivo = 1;
    }

    public Medicamento(int id, int idosoId, String nome, String dosagem, String horario, String observacoes) {
        this.id = id;
        this.idIdoso = idosoId;
        this.nomeMedicamento = nome;
        this.dosagem = dosagem;
        this.tipo = "Comprimido";
        this.horarioInicial = horario;
        this.frequenciaHoras = 8;
        this.observacaoMedica = observacoes != null ? observacoes : "";
        this.statusAtivo = 1;
    }

    public Medicamento(int id, int idIdoso, String nomeMedicamento, String dosagem,
                       String tipo, String horarioInicial, int frequenciaHoras,
                       String observacaoMedica, int statusAtivo) {
        this.id = id;
        this.idIdoso = idIdoso;
        this.nomeMedicamento = nomeMedicamento;
        this.dosagem = dosagem;
        this.tipo = (tipo != null && !tipo.isEmpty()) ? tipo : "Comprimido";
        this.horarioInicial = horarioInicial;
        this.frequenciaHoras = frequenciaHoras;
        this.observacaoMedica = observacaoMedica != null ? observacaoMedica : "";
        this.statusAtivo = statusAtivo;
    }

    public Medicamento(int idIdoso, String nomeMedicamento, String dosagem,
                       String tipo, String horarioInicial, int frequenciaHoras,
                       String observacaoMedica, int statusAtivo) {
        this.idIdoso = idIdoso;
        this.nomeMedicamento = nomeMedicamento;
        this.dosagem = dosagem;
        this.tipo = (tipo != null && !tipo.isEmpty()) ? tipo : "Comprimido";
        this.horarioInicial = horarioInicial;
        this.frequenciaHoras = frequenciaHoras;
        this.observacaoMedica = observacaoMedica != null ? observacaoMedica : "";
        this.statusAtivo = statusAtivo;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdIdoso() {
        return idIdoso;
    }

    public void setIdIdoso(int idIdoso) {
        this.idIdoso = idIdoso;
    }

    // Alias methods for idosoId
    public int getIdosoId() {
        return idIdoso;
    }

    public void setIdosoId(int idosoId) {
        this.idIdoso = idosoId;
    }

    public String getNomeMedicamento() {
        return nomeMedicamento;
    }

    public void setNomeMedicamento(String nomeMedicamento) {
        this.nomeMedicamento = nomeMedicamento;
    }

    // Alias methods for nome
    public String getNome() {
        return nomeMedicamento;
    }

    public void setNome(String nome) {
        this.nomeMedicamento = nome;
    }

    public String getDosagem() {
        return dosagem;
    }

    public void setDosagem(String dosagem) {
        this.dosagem = dosagem;
    }

    public String getTipo() {
        return tipo != null && !tipo.isEmpty() ? tipo : "Comprimido";
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getHorarioInicial() {
        return horarioInicial;
    }

    public void setHorarioInicial(String horarioInicial) {
        this.horarioInicial = horarioInicial;
    }

    // Alias methods for horario
    public String getHorario() {
        return horarioInicial;
    }

    public void setHorario(String horario) {
        this.horarioInicial = horario;
    }

    public int getFrequenciaHoras() {
        return frequenciaHoras;
    }

    public void setFrequenciaHoras(int frequenciaHoras) {
        this.frequenciaHoras = frequenciaHoras;
    }

    public String getObservacaoMedica() {
        return observacaoMedica != null ? observacaoMedica : "";
    }

    public void setObservacaoMedica(String observacaoMedica) {
        this.observacaoMedica = observacaoMedica;
    }

    // Alias methods for observacoes
    public String getObservacoes() {
        return getObservacaoMedica();
    }

    public void setObservacoes(String observacoes) {
        this.observacaoMedica = observacoes;
    }

    public int getStatusAtivo() {
        return statusAtivo;
    }

    public void setStatusAtivo(int statusAtivo) {
        this.statusAtivo = statusAtivo;
    }

    public boolean isAtivo() {
        return this.statusAtivo == 1;
    }

    public String getNomeIdoso() {
        return nomeIdoso;
    }

    public void setNomeIdoso(String nomeIdoso) {
        this.nomeIdoso = nomeIdoso;
    }
}
