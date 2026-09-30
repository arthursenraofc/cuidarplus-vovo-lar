package com.example.model;

import java.io.Serializable;

/**
 * Modelo DTO para exibição de alerta e próxima dose com cálculo de pontualidade.
 */
public class DoseAlertaDTO implements Serializable {

    private Medicamento medicamento;
    private String horarioPrevisto;
    private String statusPontualidade; // "NO_HORARIO", "ATRASADO", "CONCLUIDO"
    private boolean administradoHoje;

    public DoseAlertaDTO() {
    }

    public DoseAlertaDTO(Medicamento medicamento, String horarioPrevisto, String statusPontualidade, boolean administradoHoje) {
        this.medicamento = medicamento;
        this.horarioPrevisto = horarioPrevisto;
        this.statusPontualidade = statusPontualidade;
        this.administradoHoje = administradoHoje;
    }

    public int getIdMedicamento() {
        return medicamento != null ? medicamento.getId() : -1;
    }

    public String getNomeMedicamento() {
        return medicamento != null ? medicamento.getNomeMedicamento() : "";
    }

    public String getDosagem() {
        return medicamento != null ? medicamento.getDosagem() : "";
    }

    public String getNomeIdoso() {
        return medicamento != null ? medicamento.getNomeIdoso() : "";
    }

    public Medicamento getMedicamento() {
        return medicamento;
    }

    public void setMedicamento(Medicamento medicamento) {
        this.medicamento = medicamento;
    }

    public String getHorarioPrevisto() {
        return horarioPrevisto;
    }

    public void setHorarioPrevisto(String horarioPrevisto) {
        this.horarioPrevisto = horarioPrevisto;
    }

    public String getStatusPontualidade() {
        return statusPontualidade;
    }

    public void setStatusPontualidade(String statusPontualidade) {
        this.statusPontualidade = statusPontualidade;
    }

    public boolean isAdministradoHoje() {
        return administradoHoje;
    }

    public void setAdministradoHoje(boolean administradoHoje) {
        this.administradoHoje = administradoHoje;
    }
}
