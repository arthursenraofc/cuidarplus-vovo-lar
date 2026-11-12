package com.example.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;

/**
 * TextWatcher utilitário para formatação dinâmica em tempo real de telefones brasileiros:
 * - Fixo (10 dígitos):    (XX) XXXX-XXXX
 * - Celular (11 dígitos): (XX) XXXXX-XXXX
 *
 * Ajusta automaticamente parênteses, espaço e hífen durante a digitação ou exclusão.
 */
public class MascaraTelefoneWatcher implements TextWatcher {

    public static final int MIN_DIGITOS_TELEFONE = 10;
    public static final int MAX_DIGITOS_TELEFONE = 11;
    public static final int MAX_CARACTERES_FORMATADOS = 15;

    private final WeakReference<EditText> editTextRef;
    private final WeakReference<TextInputLayout> textInputLayoutRef;

    private boolean isUpdating = false;
    private boolean apagandoSeparador = false;
    private int indiceDigitoParaRemover = -1;
    private int digitosAntesDoCursor = 0;

    public MascaraTelefoneWatcher(@NonNull EditText editText) {
        this(editText, null);
    }

    public MascaraTelefoneWatcher(@NonNull EditText editText, @Nullable TextInputLayout textInputLayout) {
        this.editTextRef = new WeakReference<>(editText);
        this.textInputLayoutRef = new WeakReference<>(textInputLayout);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (isUpdating || s == null) {
            return;
        }
        // Detecta exclusão (Backspace) sobre caracteres de formatação: '(', ')', ' ' ou '-'
        if (count == 1 && after == 0 && start < s.length() && !Character.isDigit(s.charAt(start))) {
            apagandoSeparador = true;
            indiceDigitoParaRemover = contarDigitos(s.subSequence(0, start)) - 1;
        } else {
            apagandoSeparador = false;
            indiceDigitoParaRemover = -1;
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (isUpdating || s == null) {
            return;
        }
        int posicaoBrutaCursor = Math.min(start + count, s.length());
        digitosAntesDoCursor = contarDigitos(s.subSequence(0, posicaoBrutaCursor));

        // Limpa mensagem de erro visual enquanto o usuário digita
        TextInputLayout til = textInputLayoutRef.get();
        if (til != null && til.getError() != null) {
            til.setError(null);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (isUpdating || s == null) {
            return;
        }

        EditText editText = editTextRef.get();
        if (editText == null) {
            return;
        }

        String apenasDigitos = removerMascara(s.toString());

        // Se o usuário pressionou Backspace sobre parêntese, espaço ou hífen, remove o dígito anterior
        if (apagandoSeparador && indiceDigitoParaRemover >= 0 && indiceDigitoParaRemover < apenasDigitos.length()) {
            apenasDigitos = apenasDigitos.substring(0, indiceDigitoParaRemover)
                    + apenasDigitos.substring(indiceDigitoParaRemover + 1);
            digitosAntesDoCursor = indiceDigitoParaRemover;
        }
        apagandoSeparador = false;
        indiceDigitoParaRemover = -1;

        if (apenasDigitos.length() > MAX_DIGITOS_TELEFONE) {
            apenasDigitos = apenasDigitos.substring(0, MAX_DIGITOS_TELEFONE);
        }

        String textoFormatado = formatarTelefone(apenasDigitos);

        if (textoFormatado.equals(s.toString())) {
            return;
        }

        isUpdating = true;
        editText.setText(textoFormatado);

        int digitosAlvo = Math.min(digitosAntesDoCursor, apenasDigitos.length());
        int novaPosicaoCursor = calcularPosicaoCursor(textoFormatado, digitosAlvo);
        editText.setSelection(Math.max(0, Math.min(novaPosicaoCursor, textoFormatado.length())));
        isUpdating = false;
    }

    /**
     * Formata dinamicamente uma string numérica para o padrão brasileiro:
     * - 10 dígitos: (XX) XXXX-XXXX
     * - 11 dígitos: (XX) XXXXX-XXXX
     */
    @NonNull
    public static String formatarTelefone(@Nullable String texto) {
        String digitos = removerMascara(texto);
        if (digitos.isEmpty()) {
            return "";
        }
        if (digitos.length() > MAX_DIGITOS_TELEFONE) {
            digitos = digitos.substring(0, MAX_DIGITOS_TELEFONE);
        }

        int n = digitos.length();
        StringBuilder sb = new StringBuilder(MAX_CARACTERES_FORMATADOS);

        if (n <= 2) {
            // Ex: (51
            sb.append('(').append(digitos);
        } else if (n <= 6) {
            // Ex: (51) 9987
            sb.append('(')
              .append(digitos, 0, 2)
              .append(") ")
              .append(digitos.substring(2));
        } else if (n <= 10) {
            // Fixo ou em digitação até 10 dígitos: (XX) XXXX-XXXX
            sb.append('(')
              .append(digitos, 0, 2)
              .append(") ")
              .append(digitos, 2, 6)
              .append('-')
              .append(digitos.substring(6));
        } else {
            // Celular completo (11 dígitos): (XX) XXXXX-XXXX
            sb.append('(')
              .append(digitos, 0, 2)
              .append(") ")
              .append(digitos, 2, 7)
              .append('-')
              .append(digitos.substring(7));
        }

        return sb.toString();
    }

    /**
     * Remove parênteses, espaços, hífens e quaisquer caracteres não numéricos.
     */
    @NonNull
    public static String removerMascara(@Nullable String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return texto.replaceAll("[^0-9]", "");
    }

    private static int contarDigitos(@NonNull CharSequence sequencia) {
        int contador = 0;
        for (int i = 0; i < sequencia.length(); i++) {
            if (Character.isDigit(sequencia.charAt(i))) {
                contador++;
            }
        }
        return contador;
    }

    private static int calcularPosicaoCursor(@NonNull String textoFormatado, int qtdDigitosAntes) {
        if (qtdDigitosAntes <= 0 || textoFormatado.isEmpty()) {
            return 0;
        }
        int digitosEncontrados = 0;
        for (int i = 0; i < textoFormatado.length(); i++) {
            if (Character.isDigit(textoFormatado.charAt(i))) {
                digitosEncontrados++;
                if (digitosEncontrados == qtdDigitosAntes) {
                    return i + 1;
                }
            }
        }
        return textoFormatado.length();
    }
}
