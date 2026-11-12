package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.model.Medicamento;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gerenciador de Alerta de Estoque Mínimo Configurável (Local-First).
 * Responsável por persistir o limite mínimo em SharedPreferences e avaliar se
 * algum medicamento cadastrado no banco SQLite possui quantidade inferior ao limite.
 */
public final class EstoqueAlertaManager {

    public static final int LIMITE_MINIMO_PADRAO = 10;
    public static final String PREFS_ESTOQUE = "CuidarPlusEstoquePrefs";
    public static final String KEY_LIMITE_MINIMO_ESTOQUE = "limite_minimo_estoque";

    private static final Pattern PADRAO_PRIMEIRO_INTEIRO = Pattern.compile("\\d+");

    private EstoqueAlertaManager() {
        // Utilitário estático
    }

    /**
     * Resultado consolidado da verificação de estoque no banco de dados.
     */
    public static final class ResumoAlertaEstoque {
        private final int totalCadastrados;
        private final int limiteMinimoConfigurado;
        private final List<Medicamento> medicamentosAbaixoDoLimite;

        public ResumoAlertaEstoque(
                int totalCadastrados,
                int limiteMinimoConfigurado,
                List<Medicamento> medicamentosAbaixoDoLimite
        ) {
            this.totalCadastrados = Math.max(0, totalCadastrados);
            this.limiteMinimoConfigurado = Math.max(1, limiteMinimoConfigurado);
            this.medicamentosAbaixoDoLimite = medicamentosAbaixoDoLimite != null
                    ? Collections.unmodifiableList(new ArrayList<>(medicamentosAbaixoDoLimite))
                    : Collections.emptyList();
        }

        public int getTotalCadastrados() {
            return totalCadastrados;
        }

        public int getLimiteMinimoConfigurado() {
            return limiteMinimoConfigurado;
        }

        public int getQuantidadeAbaixoDoLimite() {
            return medicamentosAbaixoDoLimite.size();
        }

        public List<Medicamento> getMedicamentosAbaixoDoLimite() {
            return medicamentosAbaixoDoLimite;
        }

        /**
         * Indica se há ao menos um medicamento cadastrado com quantidade inferior ao limite mínimo.
         */
        public boolean possuiAlertaCritico() {
            return !medicamentosAbaixoDoLimite.isEmpty();
        }
    }

    /**
     * Obtém o limite mínimo de estoque configurado localmente (padrão: 10 unidades).
     */
    public static int getLimiteMinimoEstoque(Context context) {
        if (context == null) {
            return LIMITE_MINIMO_PADRAO;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_ESTOQUE, Context.MODE_PRIVATE);
        int salvo = prefs.getInt(KEY_LIMITE_MINIMO_ESTOQUE, LIMITE_MINIMO_PADRAO);
        return salvo >= 1 ? salvo : LIMITE_MINIMO_PADRAO;
    }

    /**
     * Salva um novo limite mínimo configurável para alerta de estoque (mínimo de 1 unidade).
     */
    public static boolean salvarLimiteMinimoEstoque(Context context, int novoLimite) {
        if (context == null || novoLimite < 1) {
            return false;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_ESTOQUE, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_LIMITE_MINIMO_ESTOQUE, novoLimite).apply();
        return true;
    }

    /**
     * Extrai a quantidade numérica a partir do campo textual de estoque do medicamento
     * (ex: "28 comprimidos" -> 28, "4 frascos" -> 4, "" ou "Crítico" -> 0).
     */
    public static int extrairQuantidadeNumerica(String textoEstoque) {
        if (textoEstoque == null) {
            return 0;
        }
        String limpo = textoEstoque.trim();
        if (limpo.isEmpty()) {
            return 0;
        }

        Matcher matcher = PADRAO_PRIMEIRO_INTEIRO.matcher(limpo);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Verifica se um medicamento específico possui quantidade estritamente inferior ao limite mínimo.
     */
    public static boolean estaAbaixoDoLimiteMinimo(Medicamento med, int limiteMinimo) {
        if (med == null) {
            return false;
        }
        int limiteSeguro = Math.max(1, limiteMinimo);
        int quantidadeAtual = extrairQuantidadeNumerica(med.getEstoque());
        return quantidadeAtual < limiteSeguro;
    }

    /**
     * Avalia toda a lista de medicamentos cadastrados no SQLite frente ao limite mínimo configurado.
     */
    public static ResumoAlertaEstoque avaliarMedicamentos(List<Medicamento> todosMedicamentos, int limiteMinimo) {
        int limiteSeguro = Math.max(1, limiteMinimo);
        if (todosMedicamentos == null || todosMedicamentos.isEmpty()) {
            return new ResumoAlertaEstoque(0, limiteSeguro, Collections.emptyList());
        }

        List<Medicamento> abaixo = new ArrayList<>();
        for (Medicamento med : todosMedicamentos) {
            if (med == null) {
                continue;
            }
            if (estaAbaixoDoLimiteMinimo(med, limiteSeguro)) {
                abaixo.add(med);
            }
        }

        return new ResumoAlertaEstoque(todosMedicamentos.size(), limiteSeguro, abaixo);
    }

    /**
     * Decrementa 1 unidade na representação textual do estoque após confirmação de dose
     * (ex: "10 comprimidos" -> "9 comprimidos"), mantendo eventual sufixo de unidade.
     */
    public static String decrementarUmaUnidadeTexto(String textoEstoque) {
        if (textoEstoque == null || textoEstoque.trim().isEmpty()) {
            return textoEstoque;
        }
        Matcher matcher = PADRAO_PRIMEIRO_INTEIRO.matcher(textoEstoque);
        if (!matcher.find()) {
            return textoEstoque;
        }
        try {
            int atual = Integer.parseInt(matcher.group());
            if (atual <= 0) {
                return textoEstoque;
            }
            int novoValor = atual - 1;
            return textoEstoque.substring(0, matcher.start())
                    + novoValor
                    + textoEstoque.substring(matcher.end());
        } catch (NumberFormatException e) {
            return textoEstoque;
        }
    }
}
