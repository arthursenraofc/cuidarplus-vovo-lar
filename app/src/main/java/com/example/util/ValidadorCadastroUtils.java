package com.example.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classe utilitária responsável pela validação semântica e higienização (sanitização)
 * dos campos cadastrais de residentes da ILPI Vovô Lar no aplicativo CuidarPlus.
 */
public final class ValidadorCadastroUtils {

    private static final Pattern PADRAO_DATA = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");

    // Faixa etária e ano razoáveis para residentes de uma ILPI
    public static final int ANO_MINIMO_NASCIMENTO = 1900;
    public static final int IDADE_MINIMA_RESIDENTE = 50;
    public static final int IDADE_MAXIMA_RESIDENTE = 125;

    private ValidadorCadastroUtils() {
        // Classe utilitária estática
    }

    /**
     * Valida a Data de Nascimento informada no padrão DD/MM/AAAA:
     * - Verifica preenchimento e formato exato (10 caracteres).
     * - Valida mês (01-12) e dia compatível com o mês/ano (incluindo anos bissextos).
     * - Impede datas futuras e verifica se o ano/idade é razoável para residentes idosos.
     * - Define mensagens de erro amigáveis diretamente no {@link TextInputLayout}.
     *
     * @param dataTexto Texto digitado no campo de data
     * @param tilData   TextInputLayout associado para exibição de erro via setError()
     * @return true se a data for válida; false caso contrário
     */
    public static boolean validarDataNascimento(@Nullable String dataTexto,
                                                @Nullable TextInputLayout tilData) {
        String valor = dataTexto != null ? dataTexto.trim() : "";

        if (valor.isEmpty()) {
            definirErro(tilData, "Informe a data de nascimento (DD/MM/AAAA).");
            return false;
        }

        if (!PADRAO_DATA.matcher(valor).matches()) {
            definirErro(tilData, "Data incompleta. Use o formato DD/MM/AAAA.");
            return false;
        }

        String[] partes = valor.split("/");
        int dia;
        int mes;
        int ano;
        try {
            dia = Integer.parseInt(partes[0]);
            mes = Integer.parseInt(partes[1]);
            ano = Integer.parseInt(partes[2]);
        } catch (NumberFormatException e) {
            definirErro(tilData, "Data de nascimento inválida.");
            return false;
        }

        if (mes < 1 || mes > 12) {
            definirErro(tilData, "Mês inválido. Informe um mês entre 01 e 12.");
            return false;
        }

        if (dia < 1 || dia > 31) {
            definirErro(tilData, "Dia inválido. Informe um dia entre 01 e 31.");
            return false;
        }

        // Validação estrita de calendário (meses de 30 dias, fevereiro e anos bissextos)
        Calendar dataInformada = Calendar.getInstance();
        dataInformada.setLenient(false);
        dataInformada.clear();
        dataInformada.set(ano, mes - 1, dia);

        try {
            dataInformada.getTimeInMillis();
        } catch (IllegalArgumentException e) {
            definirErro(tilData, "Dia inexistente para o mês e ano informados.");
            return false;
        }

        Calendar hoje = Calendar.getInstance();
        if (dataInformada.after(hoje)) {
            definirErro(tilData, "A data de nascimento não pode ser uma data futura.");
            return false;
        }

        if (ano < ANO_MINIMO_NASCIMENTO) {
            definirErro(tilData,
                    String.format(Locale.getDefault(),
                            "Ano inválido. Informe um ano a partir de %d.", ANO_MINIMO_NASCIMENTO));
            return false;
        }

        int idade = calcularIdadeExata(dataInformada, hoje);
        if (idade > IDADE_MAXIMA_RESIDENTE) {
            definirErro(tilData, "Ano de nascimento inválido para cadastro ativo.");
            return false;
        }

        if (idade < IDADE_MINIMA_RESIDENTE) {
            definirErro(tilData,
                    String.format(Locale.getDefault(),
                            "Ano incompatível para residente da ILPI (idade mínima: %d anos).",
                            IDADE_MINIMA_RESIDENTE));
            return false;
        }

        limparErro(tilData);
        return true;
    }

    /**
     * Valida o número de Telefone do Familiar / Emergência:
     * - Verifica preenchimento obrigatório.
     * - Confere se possui exatamente 10 dígitos (fixo) ou 11 dígitos (celular).
     * - Valida o código de área (DDD brasileiro entre 11 e 99).
     * - Define mensagens de erro amigáveis via {@link TextInputLayout#setError(CharSequence)}.
     *
     * @param telefoneTexto Texto com ou sem máscara digitado no campo
     * @param tilTelefone   TextInputLayout associado para exibição de erro
     * @return true se o telefone for válido; false caso contrário
     */
    public static boolean validarTelefone(@Nullable String telefoneTexto,
                                          @Nullable TextInputLayout tilTelefone) {
        String valor = telefoneTexto != null ? telefoneTexto.trim() : "";

        if (valor.isEmpty()) {
            definirErro(tilTelefone, "Informe o telefone do familiar ou emergência.");
            return false;
        }

        String digitos = MascaraTelefoneWatcher.removerMascara(valor);
        int qtdDigitos = digitos.length();

        if (qtdDigitos != MascaraTelefoneWatcher.MIN_DIGITOS_TELEFONE
                && qtdDigitos != MascaraTelefoneWatcher.MAX_DIGITOS_TELEFONE) {
            definirErro(tilTelefone, "Telefone incompleto. Informe DDD + número (10 ou 11 dígitos).");
            return false;
        }

        int ddd;
        try {
            ddd = Integer.parseInt(digitos.substring(0, 2));
        } catch (NumberFormatException e) {
            definirErro(tilTelefone, "DDD inválido.");
            return false;
        }

        if (ddd < 11 || ddd > 99) {
            definirErro(tilTelefone, "DDD inválido. Informe um código de área válido (ex: 11, 21, 51).");
            return false;
        }

        limparErro(tilTelefone);
        return true;
    }

    /**
     * Higieniza e normaliza a data de nascimento para persistência consistente (DD/MM/AAAA).
     */
    @NonNull
    public static String higienizarData(@Nullable String dataTexto) {
        return MascaraDataWatcher.formatarData(dataTexto);
    }

    /**
     * Higieniza o telefone mantendo apenas os dígitos puros (10 ou 11 números),
     * útil para integrações diretas (discador / WhatsApp) ou armazenamento limpo.
     */
    @NonNull
    public static String higienizarTelefoneApenasDigitos(@Nullable String telefoneTexto) {
        String digitos = MascaraTelefoneWatcher.removerMascara(telefoneTexto);
        if (digitos.length() > MascaraTelefoneWatcher.MAX_DIGITOS_TELEFONE) {
            return digitos.substring(0, MascaraTelefoneWatcher.MAX_DIGITOS_TELEFONE);
        }
        return digitos;
    }

    /**
     * Higieniza e padroniza o telefone no formato visual canônico ((XX) XXXXX-XXXX ou (XX) XXXX-XXXX)
     * para exibição nos prontuários, PDF e listagem do SQLite.
     */
    @NonNull
    public static String higienizarTelefoneFormatado(@Nullable String telefoneTexto) {
        return MascaraTelefoneWatcher.formatarTelefone(telefoneTexto);
    }

    /**
     * Higieniza textos livres removendo espaços excedentes nas extremidades.
     */
    @NonNull
    public static String higienizarTexto(@Nullable String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim().replaceAll("[ \\t]{2,}", " ");
    }

    private static int calcularIdadeExata(@NonNull Calendar nascimento, @NonNull Calendar referencia) {
        int idade = referencia.get(Calendar.YEAR) - nascimento.get(Calendar.YEAR);
        int mesRef = referencia.get(Calendar.MONTH);
        int mesNasc = nascimento.get(Calendar.MONTH);
        if (mesRef < mesNasc || (mesRef == mesNasc
                && referencia.get(Calendar.DAY_OF_MONTH) < nascimento.get(Calendar.DAY_OF_MONTH))) {
            idade--;
        }
        return idade;
    }

    private static void definirErro(@Nullable TextInputLayout til, @NonNull String mensagem) {
        if (til != null) {
            til.setErrorEnabled(true);
            til.setError(mensagem);
        }
    }

    private static void limparErro(@Nullable TextInputLayout til) {
        if (til != null) {
            til.setError(null);
        }
    }
}
