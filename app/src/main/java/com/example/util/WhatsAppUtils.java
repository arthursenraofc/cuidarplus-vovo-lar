package com.example.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.example.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utilitário responsável pela integração com o WhatsApp no CuidarPlus (Local-First).
 * Permite disparo de conversas diretas com familiares dos idosos sem intermediários ou APIs na nuvem,
 * com fallback seguro (SMS ou compartilhamento nativo) quando o WhatsApp não estiver instalado.
 */
public class WhatsAppUtils {

    private static final String TAG = "WhatsAppUtils";
    public static final String PACKAGE_WHATSAPP = "com.whatsapp";
    public static final String PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final String WHATSAPP_API_BASE_URL = "https://api.whatsapp.com/send?phone=";

    private WhatsAppUtils() {
        // Classe utilitária estática
    }

    /**
     * Higieniza um número de telefone removendo caracteres especiais via Regex
     * e garantindo o prefixo DDI do Brasil ("55").
     *
     * @param telefoneBruto Telefone informado (ex: "(51) 99876-5432" ou "+55 51 99876-5432")
     * @return Número contendo apenas dígitos com DDI 55, ou string vazia se inválido
     */
    public static String higienizarTelefoneBrasil(String telefoneBruto) {
        if (telefoneBruto == null || telefoneBruto.trim().isEmpty()) {
            return "";
        }

        // Remove parênteses, traços, espaços, '+' e quaisquer caracteres não numéricos
        String apenasDigitos = telefoneBruto.replaceAll("[^0-9]", "");

        // Remove zero inicial de discagem nacional caso informado (ex: 051998765432)
        if (apenasDigitos.startsWith("0") && (apenasDigitos.length() == 11 || apenasDigitos.length() == 12)) {
            apenasDigitos = apenasDigitos.substring(1);
        }

        if (apenasDigitos.length() < 10) {
            return "";
        }

        // Verifica se já possui o DDI 55 (12 dígitos para fixo com DDI ou 13 dígitos para celular com DDI)
        if (apenasDigitos.startsWith("55") && (apenasDigitos.length() == 12 || apenasDigitos.length() == 13)) {
            return apenasDigitos;
        }

        return "55" + apenasDigitos;
    }

    /**
     * Abre uma conversa direta no WhatsApp (padrão ou Business) com o familiar do idoso.
     * Caso nenhum aplicativo WhatsApp esteja instalado no dispositivo/emulador, exibe um aviso
     * amigável e oferece alternativas imediatas (envio via SMS ou compartilhamento do texto).
     *
     * @param context  Contexto Android
     * @param telefone Número de telefone (pode conter parênteses, traços, espaços ou texto)
     * @param mensagem Mensagem predefinida para ser enviada
     */
    public static void abrirConversaFamiliar(Context context, String telefone, String mensagem) {
        if (context == null) {
            return;
        }

        if (telefone == null || telefone.trim().isEmpty()) {
            Toast.makeText(context, R.string.erro_telefone_familiar_vazio, Toast.LENGTH_LONG).show();
            return;
        }

        String numeroLimpoComDdi = higienizarTelefoneBrasil(telefone);
        if (numeroLimpoComDdi.isEmpty()) {
            Toast.makeText(context, R.string.erro_telefone_familiar_invalido, Toast.LENGTH_LONG).show();
            return;
        }

        String textoMensagem = (mensagem != null) ? mensagem.trim() : "";

        try {
            String mensagemCodificada = "";
            if (!textoMensagem.isEmpty()) {
                mensagemCodificada = URLEncoder.encode(textoMensagem, StandardCharsets.UTF_8.name());
            }

            String url = WHATSAPP_API_BASE_URL + numeroLimpoComDdi
                    + (!mensagemCodificada.isEmpty() ? "&text=" + mensagemCodificada : "");
            Uri uriWhatsApp = Uri.parse(url);

            Intent intent = criarIntentWhatsApp(context, uriWhatsApp, PACKAGE_WHATSAPP);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException exPadrao) {
                Intent businessIntent = criarIntentWhatsApp(context, uriWhatsApp, PACKAGE_WHATSAPP_BUSINESS);
                context.startActivity(businessIntent);
            }
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "Aplicativo WhatsApp não instalado no dispositivo; exibindo alternativas de contato.");
            Toast.makeText(context, R.string.erro_whatsapp_nao_instalado, Toast.LENGTH_LONG).show();
            exibirAlternativasSemWhatsApp(context, numeroLimpoComDdi, textoMensagem);
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível iniciar o contato via WhatsApp: " + e.getMessage());
            Toast.makeText(context, R.string.erro_abrir_whatsapp_generico, Toast.LENGTH_SHORT).show();
        }
    }

    private static Intent criarIntentWhatsApp(Context context, Uri uri, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(packageName);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    /**
     * Exibe um diálogo Material 3 com opções alternativas (SMS ou compartilhamento de texto)
     * quando o WhatsApp não está instalado no tablet/dispositivo da instituição.
     */
    public static void exibirAlternativasSemWhatsApp(Context context, String numeroComDdi, String mensagem) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String textoExibicao = (mensagem != null && !mensagem.trim().isEmpty())
                ? mensagem.trim()
                : generarTextoFallbackPadrao();

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_whatsapp_indisponivel_titulo)
                .setMessage(activity.getString(
                        R.string.dialog_whatsapp_indisponivel_msg,
                        numeroComDdi,
                        textoExibicao
                ))
                .setPositiveButton(R.string.btn_enviar_sms, (dialog, which) ->
                        enviarPorSms(activity, numeroComDdi, textoExibicao))
                .setNeutralButton(R.string.btn_compartilhar_mensagem, (dialog, which) ->
                        compartilharTextoMensagem(activity, textoExibicao))
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    /**
     * Abre o aplicativo nativo de SMS com o número do familiar e a mensagem preenchidos.
     */
    public static void enviarPorSms(Context context, String numeroComDdi, String mensagem) {
        if (context == null || numeroComDdi == null || numeroComDdi.trim().isEmpty()) {
            return;
        }
        try {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+" + numeroComDdi.trim()));
            if (mensagem != null && !mensagem.trim().isEmpty()) {
                smsIntent.putExtra("sms_body", mensagem.trim());
            }
            if (!(context instanceof Activity)) {
                smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(smsIntent);
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "Nenhum aplicativo de SMS disponível no dispositivo.");
            Toast.makeText(context, R.string.erro_sms_indisponivel, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Abre a folha de compartilhamento nativa do Android para enviar o texto por outro canal (ex: E-mail).
     */
    public static void compartilharTextoMensagem(Context context, String mensagem) {
        if (context == null || mensagem == null || mensagem.trim().isEmpty()) {
            return;
        }
        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TEXT, mensagem.trim());

            Intent chooser = Intent.createChooser(
                    sendIntent,
                    context.getString(R.string.chooser_compartilhar_mensagem_familiar)
            );
            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "Nenhum aplicativo disponível para compartilhamento de texto.");
        }
    }

    private static String generarTextoFallbackPadrao() {
        return "Olá! Entramos em contato da ILPI Vovô Lar. Como podemos ajudar?";
    }

    /**
     * Gera mensagem padrão personalizada da ILPI Vovô Lar contendo o nome do residente.
     */
    public static String gerarMensagemRotina(String nomeIdoso) {
        String nome = (nomeIdoso != null && !nomeIdoso.trim().isEmpty()) ? nomeIdoso.trim() : "residente";
        return "Olá! Entro em contato da ILPI Vovô Lar a respeito do(a) residente " + nome + ". Como podemos ajudar?";
    }

    /**
     * Gera mensagem padrão de contato importante/urgente do lar.
     */
    public static String gerarMensagemUrgente(String nomeIdoso) {
        String nome = (nomeIdoso != null && !nomeIdoso.trim().isEmpty()) ? nomeIdoso : "residente";
        return "Olá! Entramos em contato da equipe de atendimento da ILPI CuidarPlus a respeito do residente "
                + nome + ". Por favor, nos responda assim que visualizar esta mensagem.";
    }

    /**
     * Gera mensagem padrão de alerta para reposição de medicamentos no estoque.
     */
    public static String gerarMensagemReposicaoMedicamento(String nomeIdoso, String nomeMedicamento, String dosagem) {
        String nome = (nomeIdoso != null && !nomeIdoso.trim().isEmpty()) ? nomeIdoso : "residente";
        String remedio = (nomeMedicamento != null && !nomeMedicamento.trim().isEmpty()) ? nomeMedicamento : "medicamento prescrito";
        String dose = (dosagem != null && !dosagem.trim().isEmpty()) ? " (" + dosagem + ")" : "";

        return "Olá! Aqui é da equipe de enfermagem da ILPI CuidarPlus. "
                + "Informamos que o medicamento *" + remedio + dose + "* do residente *" + nome
                + "* está próximo do término em nosso estoque. "
                + "Solicitamos a gentileza de providenciar a reposição na instituição. Agradecemos a colaboração!";
    }
}
