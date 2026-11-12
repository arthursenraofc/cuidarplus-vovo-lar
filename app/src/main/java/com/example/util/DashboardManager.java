package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.model.Idoso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gerenciador de métricas e indicadores visuais do Dashboard Principal (Material Design 3).
 * Centraliza o cálculo de ocupação de vagas (ocupadas/disponíveis), persistência local da
 * capacidade total da ILPI e filtragem de aniversariantes do mês.
 */
public final class DashboardManager {

    public static final int CAPACIDADE_TOTAL_PADRAO = 20;
    private static final String PREFS_DASHBOARD = "CuidarPlusDashboardPrefs";
    private static final String KEY_CAPACIDADE_TOTAL_VAGAS = "capacidade_total_vagas";

    private static final String[] MESES_PT_BR = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    private DashboardManager() {
        // Utilitário estático
    }

    /**
     * Representa o resumo consolidado de residentes e vagas da ILPI.
     */
    public static final class ResumoVagas {
        private final int totalResidentes;
        private final int capacidadeTotal;
        private final int vagasOcupadas;
        private final int vagasDisponiveis;
        private final int vagasExcedentes;
        private final int percentualOcupacao;

        public ResumoVagas(int totalResidentes, int capacidadeTotal, int vagasOcupadas,
                           int vagasDisponiveis, int vagasExcedentes, int percentualOcupacao) {
            this.totalResidentes = totalResidentes;
            this.capacidadeTotal = capacidadeTotal;
            this.vagasOcupadas = vagasOcupadas;
            this.vagasDisponiveis = vagasDisponiveis;
            this.vagasExcedentes = vagasExcedentes;
            this.percentualOcupacao = percentualOcupacao;
        }

        public int getTotalResidentes() {
            return totalResidentes;
        }

        public int getCapacidadeTotal() {
            return capacidadeTotal;
        }

        public int getVagasOcupadas() {
            return vagasOcupadas;
        }

        public int getVagasDisponiveis() {
            return vagasDisponiveis;
        }

        public int getVagasExcedentes() {
            return vagasExcedentes;
        }

        public int getPercentualOcupacao() {
            return percentualOcupacao;
        }

        public boolean isLotacaoCompleta() {
            return vagasDisponiveis == 0 && capacidadeTotal > 0;
        }
    }

    /**
     * Representa um residente aniversariante no mês consultado.
     */
    public static final class AniversarianteMes {
        private final Idoso idoso;
        private final int diaNascimento;
        private final int mesNascimento;
        private final int anoNascimento;
        private final int idadeNoAno;
        private final boolean aniversarioHoje;

        public AniversarianteMes(Idoso idoso, int diaNascimento, int mesNascimento,
                                 int anoNascimento, int idadeNoAno, boolean aniversarioHoje) {
            this.idoso = idoso;
            this.diaNascimento = diaNascimento;
            this.mesNascimento = mesNascimento;
            this.anoNascimento = anoNascimento;
            this.idadeNoAno = idadeNoAno;
            this.aniversarioHoje = aniversarioHoje;
        }

        public Idoso getIdoso() {
            return idoso;
        }

        public int getDiaNascimento() {
            return diaNascimento;
        }

        public int getMesNascimento() {
            return mesNascimento;
        }

        public int getAnoNascimento() {
            return anoNascimento;
        }

        public int getIdadeNoAno() {
            return idadeNoAno;
        }

        public boolean isAniversarioHoje() {
            return aniversarioHoje;
        }
    }

    /**
     * Obtém a capacidade total de vagas configurada na instituição (padrão: 20 vagas).
     */
    public static int getCapacidadeTotalVagas(Context context) {
        if (context == null) {
            return CAPACIDADE_TOTAL_PADRAO;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_DASHBOARD, Context.MODE_PRIVATE);
        int configurada = prefs.getInt(KEY_CAPACIDADE_TOTAL_VAGAS, CAPACIDADE_TOTAL_PADRAO);
        return configurada > 0 ? configurada : CAPACIDADE_TOTAL_PADRAO;
    }

    /**
     * Salva localmente uma nova capacidade total de vagas (mínimo de 1 vaga).
     */
    public static boolean salvarCapacidadeTotalVagas(Context context, int novaCapacidade) {
        if (context == null || novaCapacidade < 1) {
            return false;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_DASHBOARD, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CAPACIDADE_TOTAL_VAGAS, novaCapacidade).apply();
        return true;
    }

    /**
     * Calcula os indicadores de ocupação e disponibilidade de vagas a partir do total de residentes.
     */
    public static ResumoVagas calcularResumoVagas(int totalResidentes, int capacidadeTotal) {
        int residentesSeguros = Math.max(0, totalResidentes);
        int capacidadeSegura = Math.max(1, capacidadeTotal);

        int vagasOcupadas = residentesSeguros;
        int vagasDisponiveis = Math.max(0, capacidadeSegura - vagasOcupadas);
        int vagasExcedentes = Math.max(0, vagasOcupadas - capacidadeSegura);
        int percentual = Math.min(100, Math.round((vagasOcupadas * 100f) / capacidadeSegura));

        return new ResumoVagas(
                residentesSeguros,
                capacidadeSegura,
                vagasOcupadas,
                vagasDisponiveis,
                vagasExcedentes,
                percentual
        );
    }

    /**
     * Filtra e ordena os aniversariantes do mês atual com base no relógio do dispositivo.
     */
    public static List<AniversarianteMes> filtrarAniversariantesDoMesAtual(List<Idoso> todosIdosos) {
        return filtrarAniversariantesDoMes(todosIdosos, Calendar.getInstance());
    }

    /**
     * Filtra e ordena os aniversariantes usando uma data de referência explícita.
     */
    public static List<AniversarianteMes> filtrarAniversariantesDoMes(List<Idoso> todosIdosos, Calendar referencia) {
        Calendar cal = referencia != null ? referencia : Calendar.getInstance();
        int diaHoje = cal.get(Calendar.DAY_OF_MONTH);
        int mesAtual1a12 = cal.get(Calendar.MONTH) + 1;
        int anoAtual = cal.get(Calendar.YEAR);
        return filtrarAniversariantesDoMes(todosIdosos, diaHoje, mesAtual1a12, anoAtual);
    }

    /**
     * Filtra todos os residentes cujo mês de nascimento coincide com {@code mesAlvo1a12} (1 a 12),
     * ordenando-os cronologicamente pelo dia do aniversário e alfabeticamente pelo nome.
     */
    public static List<AniversarianteMes> filtrarAniversariantesDoMes(
            List<Idoso> todosIdosos,
            int diaReferencia,
            int mesAlvo1a12,
            int anoReferencia
    ) {
        if (todosIdosos == null || todosIdosos.isEmpty() || mesAlvo1a12 < 1 || mesAlvo1a12 > 12) {
            return Collections.emptyList();
        }

        List<AniversarianteMes> aniversariantes = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        sdf.setLenient(false);

        for (Idoso idoso : todosIdosos) {
            if (idoso == null || idoso.getDataNascimento() == null) {
                continue;
            }
            String dataTexto = idoso.getDataNascimento().trim();
            if (dataTexto.isEmpty()) {
                continue;
            }

            try {
                Date dataNasc = sdf.parse(dataTexto);
                if (dataNasc == null) {
                    continue;
                }
                Calendar calNasc = Calendar.getInstance();
                calNasc.setTime(dataNasc);

                int dia = calNasc.get(Calendar.DAY_OF_MONTH);
                int mes = calNasc.get(Calendar.MONTH) + 1;
                int ano = calNasc.get(Calendar.YEAR);

                if (mes == mesAlvo1a12) {
                    int idadeNoAno = Math.max(0, anoReferencia - ano);
                    boolean ehHoje = (dia == diaReferencia);
                    aniversariantes.add(new AniversarianteMes(
                            idoso,
                            dia,
                            mes,
                            ano,
                            idadeNoAno,
                            ehHoje
                    ));
                }
            } catch (ParseException ignored) {
                // Ignora datas ausentes ou com formato inválido sem interromper o dashboard
            }
        }

        aniversariantes.sort(Comparator
                .comparingInt(AniversarianteMes::getDiaNascimento)
                .thenComparing(a -> a.getIdoso().getNome() != null
                        ? a.getIdoso().getNome().toLowerCase(Locale.ROOT)
                        : ""));

        return aniversariantes;
    }

    /**
     * Retorna o nome do mês em português (1 = Janeiro ... 12 = Dezembro).
     */
    public static String obterNomeMesPortugues(int mes1a12) {
        if (mes1a12 < 1 || mes1a12 > 12) {
            int mesSistema = Calendar.getInstance().get(Calendar.MONTH);
            return MESES_PT_BR[mesSistema];
        }
        return MESES_PT_BR[mes1a12 - 1];
    }
}
