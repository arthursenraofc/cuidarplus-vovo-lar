package com.example.model;

import java.io.Serializable;

/**
 * Modelo que representa um registro de Passagem de Turno e Observações clínicas/operacionais
 * entre os plantões (Manhã, Tarde, Noite) da ILPI Vovô Lar.
 */
public class PassagemTurno implements Serializable {

    public static final String TURNO_DIURNO = "Diurno";
    public static final String TURNO_NOTURNO = "Noturno";
    public static final String TURNO_MANHA = "Manhã";
    public static final String TURNO_TARDE = "Tarde";
    public static final String TURNO_NOITE = "Noite";

    public static final String PRIORIDADE_NORMAL = "NORMAL";
    public static final String PRIORIDADE_ATENCAO = "ATENCAO";
    public static final String PRIORIDADE_URGENTE = "URGENTE";

    private int id;
    private String turno;
    private String dataHoraRegistro;
    private long timestampRegistro;
    private String profissionalOrigem;
    private String cargoOrigem;
    private int idIdoso;
    private String nomeIdoso;
    private String prioridade;
    private String observacoes;
    private String intercorrencias;
    private String pendenciasProximoTurno;
    private boolean conferido;
    private String profissionalDestino;
    private String dataHoraConferencia;

    public PassagemTurno() {
        this.idIdoso = -1;
        this.nomeIdoso = "Geral da Instituição";
        this.prioridade = PRIORIDADE_NORMAL;
        this.intercorrencias = "";
        this.pendenciasProximoTurno = "";
        this.profissionalDestino = "";
        this.dataHoraConferencia = "";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTurno() {
        return turno != null ? turno : TURNO_MANHA;
    }

    public void setTurno(String turno) {
        this.turno = turno;
    }

    public String getDataHoraRegistro() {
        return dataHoraRegistro != null ? dataHoraRegistro : "";
    }

    public void setDataHoraRegistro(String dataHoraRegistro) {
        this.dataHoraRegistro = dataHoraRegistro;
    }

    public long getTimestampRegistro() {
        return timestampRegistro;
    }

    public void setTimestampRegistro(long timestampRegistro) {
        this.timestampRegistro = timestampRegistro;
    }

    public String getProfissionalOrigem() {
        return profissionalOrigem != null ? profissionalOrigem : "";
    }

    public void setProfissionalOrigem(String profissionalOrigem) {
        this.profissionalOrigem = profissionalOrigem;
    }

    public String getCargoOrigem() {
        return cargoOrigem != null ? cargoOrigem : "";
    }

    public void setCargoOrigem(String cargoOrigem) {
        this.cargoOrigem = cargoOrigem;
    }

    public int getIdIdoso() {
        return idIdoso;
    }

    public void setIdIdoso(int idIdoso) {
        this.idIdoso = idIdoso;
    }

    public String getNomeIdoso() {
        return nomeIdoso != null && !nomeIdoso.trim().isEmpty()
                ? nomeIdoso : "Geral da Instituição";
    }

    public void setNomeIdoso(String nomeIdoso) {
        this.nomeIdoso = nomeIdoso;
    }

    public String getPrioridade() {
        return prioridade != null ? prioridade : PRIORIDADE_NORMAL;
    }

    public void setPrioridade(String prioridade) {
        this.prioridade = prioridade;
    }

    public String getObservacoes() {
        return observacoes != null ? observacoes : "";
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getIntercorrencias() {
        return intercorrencias != null ? intercorrencias : "";
    }

    public void setIntercorrencias(String intercorrencias) {
        this.intercorrencias = intercorrencias;
    }

    public String getPendenciasProximoTurno() {
        return pendenciasProximoTurno != null ? pendenciasProximoTurno : "";
    }

    public void setPendenciasProximoTurno(String pendenciasProximoTurno) {
        this.pendenciasProximoTurno = pendenciasProximoTurno;
    }

    public boolean isConferido() {
        return conferido;
    }

    public void setConferido(boolean conferido) {
        this.conferido = conferido;
    }

    public String getProfissionalDestino() {
        return profissionalDestino != null ? profissionalDestino : "";
    }

    public void setProfissionalDestino(String profissionalDestino) {
        this.profissionalDestino = profissionalDestino;
    }

    public String getDataHoraConferencia() {
        return dataHoraConferencia != null ? dataHoraConferencia : "";
    }

    public void setDataHoraConferencia(String dataHoraConferencia) {
        this.dataHoraConferencia = dataHoraConferencia;
    }
}
