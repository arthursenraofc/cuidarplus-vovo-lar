package com.example.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.model.Medicamento;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Utilitário para validação, cálculo de dias restantes e classificação visual
 * da Data de Validade (DD/MM/AAAA) dos medicamentos da ILPI Vovô Lar.
 */
public final class ValidadeMedicamentoHelper {

    public static final int DIAS_ALERTA_VENCIMENTO_PROXIMO = 30;
    private static final Pattern PADRAO_DATA = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");

    private ValidadeMedicamentoHelper() {
    }

    public enum StatusValidade {
        NAO_INFORMADA,
        VALIDO,
        VENCIMENTO_PROXIMO,
        VENCIDO
    }

    public static final class AvaliacaoValidade {
        private final StatusValidade status;
        private final long diasRestantes;
        private final String dataFormatada;

        public AvaliacaoValidade(StatusValidade status, long diasRestantes, String dataFormatada) {
            this.status = status;
            this.diasRestantes = diasRestantes;
            this.dataFormatada = dataFormatada != null ? dataFormatada : "";
        }

        public StatusValidade getStatus() {
            return status;
        }

        public long getDiasRestantes() {
            return diasRestantes;
        }

        public String getDataFormatada() {
            return dataFormatada;
        }

        public boolean possuiAlerta() {
            return status == StatusValidade.VENCIDO || status == StatusValidade.VENCIMENTO_PROXIMO;
        }
    }

    /**
     * Valida uma Data de Validade opcional no formato DD/MM/AAAA.
     * Vazio é permitido; se preenchido, exige data existente no calendário.
     */
    public static boolean validarDataValidadeOpcional(@Nullable String dataTexto,
                                                      @Nullable TextInputLayout tilData) {
        String valor = dataTexto != null ? dataTexto.trim() : "";
        if (valor.isEmpty()) {
            limparErro(tilData);
            return true;
        }

        if (!PADRAO_DATA.matcher(valor).matches()) {
            definirErro(tilData, "Data incompleta. Use o formato DD/MM/AAAA.");
            return false;
        }

        Calendar parsed = parseDataCalendario(valor);
        if (parsed == null) {
            definirErro(tilData, "Data de validade inválida. Verifique dia, mês e ano.");
            return false;
        }

        int ano = parsed.get(Calendar.YEAR);
        if (ano < 2000 || ano > 2100) {
            definirErro(tilData, "Informe um ano de validade entre 2000 e 2100.");
            return false;
        }

        limparErro(tilData);
        return true;
    }

    /**
     * Avalia a data de validade frente à data atual do dispositivo.
     */
    @NonNull
    public static AvaliacaoValidade avaliarValidade(@Nullable String dataValidade) {
        return avaliarValidade(dataValidade, Calendar.getInstance());
    }

    /**
     * Avalia a data de validade frente a uma data de referência explícita.
     */
    @NonNull
    public static AvaliacaoValidade avaliarValidade(@Nullable String dataValidade,
                                                    @Nullable Calendar referencia) {
        String valor = dataValidade != null ? dataValidade.trim() : "";
        if (valor.isEmpty()) {
            return new AvaliacaoValidade(StatusValidade.NAO_INFORMADA, Long.MAX_VALUE, "");
        }

        Calendar calValidade = parseDataCalendario(valor);
        if (calValidade == null) {
            return new AvaliacaoValidade(StatusValidade.NAO_INFORMADA, Long.MAX_VALUE, valor);
        }

        Calendar calHoje = referencia != null ? (Calendar) referencia.clone() : Calendar.getInstance();
        zerarHorario(calHoje);
        zerarHorario(calValidade);

        long diffMillis = calValidade.getTimeInMillis() - calHoje.getTimeInMillis();
        long diasRestantes = TimeUnit.MILLISECONDS.toDays(diffMillis);

        StatusValidade status;
        if (diasRestantes < 0) {
            status = StatusValidade.VENCIDO;
        } else if (diasRestantes <= DIAS_ALERTA_VENCIMENTO_PROXIMO) {
            status = StatusValidade.VENCIMENTO_PROXIMO;
        } else {
            status = StatusValidade.VALIDO;
        }

        return new AvaliacaoValidade(status, diasRestantes, valor);
    }

    /**
     * Retorna o texto curto do badge de alerta de validade, ou string vazia se não houver alerta.
     */
    @NonNull
    public static String formatarTextoBadge(@NonNull AvaliacaoValidade avaliacao) {
        if (avaliacao.getStatus() == StatusValidade.VENCIDO) {
            return "Vencido";
        }
        if (avaliacao.getStatus() == StatusValidade.VENCIMENTO_PROXIMO) {
            long dias = avaliacao.getDiasRestantes();
            if (dias == 0) {
                return "Vence hoje";
            }
            if (dias == 1) {
                return "Vence em 1 dia";
            }
            return String.format(Locale.getDefault(), "Vence em %d dias", dias);
        }
        return "";
    }

    /**
     * Conta quantos medicamentos na lista estão vencidos ou com vencimento próximo (<= 30 dias).
     */
    public static int contarAlertasValidade(@Nullable List<Medicamento> medicamentos) {
        return listarComAlertaValidade(medicamentos).size();
    }

    /**
     * Retorna todos os medicamentos vencidos ou com vencimento dentro de 30 dias.
     */
    @NonNull
    public static List<Medicamento> listarComAlertaValidade(@Nullable List<Medicamento> medicamentos) {
        List<Medicamento> alertas = new ArrayList<>();
        if (medicamentos == null) {
            return alertas;
        }
        for (Medicamento med : medicamentos) {
            if (med == null) {
                continue;
            }
            if (avaliarValidade(med.getDataValidade()).possuiAlerta()) {
                alertas.add(med);
            }
        }
        return alertas;
    }

    /**
     * Gera uma data formatada em DD/MM/AAAA deslocada em dias a partir de hoje.
     */
    @NonNull
    public static String gerarDataDeslocadaFormatada(int diasOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, diasOffset);
        return String.format(
                Locale.US,
                "%02d/%02d/%04d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR)
        );
    }

    @Nullable
    private static Calendar parseDataCalendario(@NonNull String valor) {
        if (!PADRAO_DATA.matcher(valor).matches()) {
            return null;
        }
        String[] partes = valor.split("/");
        try {
            int dia = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            int ano = Integer.parseInt(partes[2]);
            if (mes < 1 || mes > 12 || dia < 1 || dia > 31) {
                return null;
            }
            Calendar cal = Calendar.getInstance();
            cal.setLenient(false);
            cal.clear();
            cal.set(ano, mes - 1, dia);
            cal.getTimeInMillis();
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private static void zerarHorario(@NonNull Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static void definirErro(@Nullable TextInputLayout til, @NonNull String msg) {
        if (til != null) {
            til.setErrorEnabled(true);
            til.setError(msg);
        }
    }

    private static void limparErro(@Nullable TextInputLayout til) {
        if (til != null) {
            til.setError(null);
        }
    }
}
