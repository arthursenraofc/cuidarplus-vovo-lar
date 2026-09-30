package com.example.model;

import java.io.Serializable;

/**
 * Modelo que representa o histórico de administração de medicamentos (Sprint 2 Avançado).
 * Armazena data/hora prescrita, executada, status ("ADMINISTRADO", "ATRASADO", "RECUSADO"),
 * cuidador responsável e observação clínica.
 */
public class HistoricoMedicacao implements Serializable {

    public static final String STATUS_ADMINISTRADO = "ADMINISTRADO";
    public static final String STATUS_ATRASADO = "ATRASADO";
    public static final String STATUS_RECUSADO = "RECUSADO";

    private int id;
    private int idMedicamento;
    private int idIdoso;
    private String dataHoraPrescrita;  // Data/hora que estava prevista para aplicação
    private String dataHoraExecutada;  // Data/hora real em que foi administrada
    private String status;             // "ADMINISTRADO", "ATRASADO", "RECUSADO"
    private String nomeCuidador;
    private String observacao;

    public HistoricoMedicacao() {
        this.status = STATUS_ADMINISTRADO;
        this.observacao = "";
    }

    public HistoricoMedicacao(int medicamentoId, String dataHora, String status) {
        this.idMedicamento = medicamentoId;
        this.dataHoraPrescrita = dataHora;
        this.dataHoraExecutada = dataHora;
        this.status = status;
        this.nomeCuidador = "Cuidador";
        this.observacao = "";
    }

    public HistoricoMedicacao(int id, int medicamentoId, String dataHora, String status) {
        this.id = id;
        this.idMedicamento = medicamentoId;
        this.dataHoraPrescrita = dataHora;
        this.dataHoraExecutada = dataHora;
        this.status = status;
        this.nomeCuidador = "Cuidador";
        this.observacao = "";
    }

    public HistoricoMedicacao(int id, int idMedicamento, int idIdoso, String dataHoraPrescrita,
                              String dataHoraExecutada, String status, String nomeCuidador, String observacao) {
        this.id = id;
        this.idMedicamento = idMedicamento;
        this.idIdoso = idIdoso;
        this.dataHoraPrescrita = dataHoraPrescrita;
        this.dataHoraExecutada = dataHoraExecutada;
        this.status = status;
        this.nomeCuidador = nomeCuidador;
        this.observacao = observacao != null ? observacao : "";
    }

    public HistoricoMedicacao(int idMedicamento, int idIdoso, String dataHoraPrescrita,
                              String dataHoraExecutada, String status, String nomeCuidador, String observacao) {
        this.idMedicamento = idMedicamento;
        this.idIdoso = idIdoso;
        this.dataHoraPrescrita = dataHoraPrescrita;
        this.dataHoraExecutada = dataHoraExecutada;
        this.status = status;
        this.nomeCuidador = nomeCuidador;
        this.observacao = observacao != null ? observacao : "";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdMedicamento() {
        return idMedicamento;
    }

    public void setIdMedicamento(int idMedicamento) {
        this.idMedicamento = idMedicamento;
    }

    // Alias methods for medicamentoId
    public int getMedicamentoId() {
        return idMedicamento;
    }

    public void setMedicamentoId(int medicamentoId) {
        this.idMedicamento = medicamentoId;
    }

    public int getIdIdoso() {
        return idIdoso;
    }

    public void setIdIdoso(int idIdoso) {
        this.idIdoso = idIdoso;
    }

    public String getDataHoraPrescrita() {
        return dataHoraPrescrita != null ? dataHoraPrescrita : "";
    }

    public void setDataHoraPrescrita(String dataHoraPrescrita) {
        this.dataHoraPrescrita = dataHoraPrescrita;
    }

    public String getDataHoraExecutada() {
        return dataHoraExecutada != null ? dataHoraExecutada : "";
    }

    public void setDataHoraExecutada(String dataHoraExecutada) {
        this.dataHoraExecutada = dataHoraExecutada;
    }

    // Alias methods for dataHora
    public String getDataHora() {
        return (dataHoraExecutada != null && !dataHoraExecutada.isEmpty()) ? dataHoraExecutada : dataHoraPrescrita;
    }

    public void setDataHora(String dataHora) {
        this.dataHoraExecutada = dataHora;
        if (this.dataHoraPrescrita == null || this.dataHoraPrescrita.isEmpty()) {
            this.dataHoraPrescrita = dataHora;
        }
    }

    public String getStatus() {
        return status != null ? status : STATUS_ADMINISTRADO;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNomeCuidador() {
        return nomeCuidador != null ? nomeCuidador : "Cuidador de Plantão";
    }

    public void setNomeCuidador(String nomeCuidador) {
        this.nomeCuidador = nomeCuidador;
    }

    public String getObservacao() {
        return observacao != null ? observacao : "";
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
