package com.example.model;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Classe Modelo que representa a entidade Idoso (Residente da ILPI).
 * Implementa Serializable para permitir o envio do objeto entre Activities via Intent.
 */
public class Idoso implements Serializable {

    private int id;
    private String nome;
    private String dataNascimento;       // Formato DD/MM/AAAA
    private String tipoSanguineo;        // Ex: A+, O-, etc.
    private String alergias;             // Medicamentos, alimentos, etc.
    private String restricoesMedicas;    // Diagnósticos, cuidados, etc.
    private String contatoEmergencia;    // Nome e Telefone do responsável
    private String caminhoFoto;          // Caminho absoluto local do arquivo da foto

    // Construtor vazio (padrão)
    public Idoso() {
    }

    // Construtor completo com ID (utilizado na leitura do banco de dados)
    public Idoso(int id, String nome, String dataNascimento, String tipoSanguineo,
                 String alergias, String restricoesMedicas, String contatoEmergencia, String caminhoFoto) {
        this.id = id;
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.tipoSanguineo = tipoSanguineo;
        this.alergias = alergias;
        this.restricoesMedicas = restricoesMedicas;
        this.contatoEmergencia = contatoEmergencia;
        this.caminhoFoto = caminhoFoto;
    }

    // Construtor sem ID (utilizado para novos cadastros)
    public Idoso(String nome, String dataNascimento, String tipoSanguineo,
                 String alergias, String restricoesMedicas, String contatoEmergencia, String caminhoFoto) {
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.tipoSanguineo = tipoSanguineo;
        this.alergias = alergias;
        this.restricoesMedicas = restricoesMedicas;
        this.contatoEmergencia = contatoEmergencia;
        this.caminhoFoto = caminhoFoto;
    }

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(String dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getTipoSanguineo() {
        return tipoSanguineo;
    }

    public void setTipoSanguineo(String tipoSanguineo) {
        this.tipoSanguineo = tipoSanguineo;
    }

    public String getAlergias() {
        return alergias;
    }

    public void setAlergias(String alergias) {
        this.alergias = alergias;
    }

    public String getRestricoesMedicas() {
        return restricoesMedicas;
    }

    public void setRestricoesMedicas(String restricoesMedicas) {
        this.restricoesMedicas = restricoesMedicas;
    }

    public String getContatoEmergencia() {
        return contatoEmergencia;
    }

    public void setContatoEmergencia(String contatoEmergencia) {
        this.contatoEmergencia = contatoEmergencia;
    }

    public String getCaminhoFoto() {
        return caminhoFoto;
    }

    public void setCaminhoFoto(String caminhoFoto) {
        this.caminhoFoto = caminhoFoto;
    }

    /**
     * Calcula a idade atual do idoso com base na data de nascimento (DD/MM/AAAA).
     * @return Idade em anos, ou 0 se a data for inválida.
     */
    public int calcularIdade() {
        if (dataNascimento == null || dataNascimento.trim().isEmpty()) {
            return 0;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date dataNasc = sdf.parse(dataNascimento);
            if (dataNasc == null) return 0;

            Calendar dob = Calendar.getInstance();
            dob.setTime(dataNasc);
            Calendar hoje = Calendar.getInstance();

            int idade = hoje.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (hoje.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                idade--;
            }
            return Math.max(idade, 0);
        } catch (ParseException e) {
            return 0;
        }
    }
}
