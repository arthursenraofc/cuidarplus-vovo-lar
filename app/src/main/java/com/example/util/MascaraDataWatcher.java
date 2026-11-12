package com.example.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;

/**
 * TextWatcher utilitário para formatação automática em tempo real de datas no padrão DD/MM/AAAA.
 *
 * Características:
 * - Aceita apenas dígitos numéricos (0-9), removendo qualquer outro caractere.
 * - Insere as barras '/' automaticamente após o dia (2º dígito) e o mês (4º dígito).
 * - Limita estritamente a 8 dígitos numéricos (10 caracteres formatados: DD/MM/AAAA).
 * - Trata exclusão (Backspace) sobre as barras '/' sem travar o cursor.
 */
public class MascaraDataWatcher implements TextWatcher {

    public static final int MAX_DIGITOS_DATA = 8;
    public static final int MAX_CARACTERES_FORMATADOS = 10;

    private final WeakReference<EditText> editTextRef;
    private final WeakReference<TextInputLayout> textInputLayoutRef;

    private boolean isUpdating = false;
    private boolean apagandoSeparador = false;
    private int indiceDigitoParaRemover = -1;
    private int digitosAntesDoCursor = 0;

    public MascaraDataWatcher(@NonNull EditText editText) {
        this(editText, null);
    }

    public MascaraDataWatcher(@NonNull EditText editText, @Nullable TextInputLayout textInputLayout) {
        this.editTextRef = new WeakReference<>(editText);
        this.textInputLayoutRef = new WeakReference<>(textInputLayout);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (isUpdating || s == null) {
            return;
        }
        // Detecta se o usuário pressionou Backspace exatamente sobre uma barra '/'
        if (count == 1 && after == 0 && start < s.length() && s.charAt(start) == '/') {
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

        // Limpa mensagem de erro visual enquanto o usuário corrige a digitação
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

        // Se o usuário apagou uma barra '/', remove também o dígito imediatamente anterior a ela
        if (apagandoSeparador && indiceDigitoParaRemover >= 0 && indiceDigitoParaRemover < apenasDigitos.length()) {
            apenasDigitos = apenasDigitos.substring(0, indiceDigitoParaRemover)
                    + apenasDigitos.substring(indiceDigitoParaRemover + 1);
            digitosAntesDoCursor = indiceDigitoParaRemover;
        }
        apagandoSeparador = false;
        indiceDigitoParaRemover = -1;

        if (apenasDigitos.length() > MAX_DIGITOS_DATA) {
            apenasDigitos = apenasDigitos.substring(0, MAX_DIGITOS_DATA);
        }

        String textoFormatado = formatarData(apenasDigitos);

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
     * Formata uma sequência de dígitos no padrão DD/MM/AAAA de forma progressiva.
     */
    @NonNull
    public static String formatarData(@Nullable String texto) {
        String digitos = removerMascara(texto);
        if (digitos.isEmpty()) {
            return "";
        }
        if (digitos.length() > MAX_DIGITOS_DATA) {
            digitos = digitos.substring(0, MAX_DIGITOS_DATA);
        }

        StringBuilder sb = new StringBuilder(MAX_CARACTERES_FORMATADOS);
        int tamanho = digitos.length();

        for (int i = 0; i < tamanho; i++) {
            if (i == 2 || i == 4) {
                sb.append('/');
            }
            sb.append(digitos.charAt(i));
        }

        return sb.toString();
    }

    /**
     * Remove qualquer caractere não numérico da string informada.
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
