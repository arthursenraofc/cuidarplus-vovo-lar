package com.example.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.EstoqueActivity;
import com.example.R;
import com.example.database.DatabaseHelper;
import com.example.model.Medicamento;

import java.util.List;

/**
 * Utilitário responsável pelo gerenciamento de Canais de Notificação (NotificationChannel)
 * e disparo de notificações locais nativas de doses e estoque crítico no CuidarPlus.
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    public static final String CANAL_ID = "cuidarplus_medicamentos_channel_v2";
    public static final String CANAL_NOME = "Alarmes de Medicamentos Críticos";
    public static final String CANAL_DESCRICAO = "Alarmes com som e vibração para administração pontual de medicamentos dos idosos";

    public static final String CANAL_ESTOQUE_ID = "cuidarplus_estoque_baixo_channel_v1";
    public static final String CANAL_ESTOQUE_NOME = "Alertas de Estoque Baixo";
    public static final String CANAL_ESTOQUE_DESCRICAO = "Notificações automáticas quando medicamentos atingem nível abaixo do mínimo configurado";
    public static final int NOTIF_ID_ESTOQUE_BAIXO = 90500;

    private static final String KEY_ULTIMA_ASSINATURA_ESTOQUE = "ultima_assinatura_notif_estoque";

    /**
     * Registra o canal de notificação de medicamentos no NotificationManager do Android.
     */
    public static void criarCanalNotificacao(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                Log.w(TAG, "NotificationManager não disponível para registrar o canal.");
                return;
            }

            if (manager.getNotificationChannel(CANAL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CANAL_ID,
                        CANAL_NOME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(CANAL_DESCRICAO);
                channel.enableLights(true);
                channel.setLightColor(Color.RED);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                Uri alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmeUri == null) {
                    alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();

                channel.setSound(alarmeUri, audioAttributes);
                manager.createNotificationChannel(channel);
                Log.d(TAG, "NotificationChannel criado com sucesso: " + CANAL_ID);
            }
        }
        criarCanalEstoqueNotificacao(context);
    }

    /**
     * Registra o canal dedicado para notificações de alerta de estoque baixo.
     */
    public static void criarCanalEstoqueNotificacao(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CANAL_ESTOQUE_ID) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CANAL_ESTOQUE_ID,
                    CANAL_ESTOQUE_NOME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CANAL_ESTOQUE_DESCRICAO);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Consulta todos os medicamentos no SQLite e dispara/atualiza notificação nativa caso
     * algum item esteja abaixo do limite mínimo configurado.
     */
    public static EstoqueAlertaManager.ResumoAlertaEstoque avaliarENotificarEstoqueBaixo(Context context) {
        if (context == null) {
            return new EstoqueAlertaManager.ResumoAlertaEstoque(0, EstoqueAlertaManager.LIMITE_MINIMO_PADRAO, null);
        }
        List<Medicamento> meds = DatabaseHelper.getInstance(context).listarTodosMedicamentos();
        return avaliarENotificarEstoqueBaixo(context, meds);
    }

    /**
     * Avalia a lista informada frente ao limite mínimo configurado e dispara notificação no sistema
     * quando houver medicamentos em estado crítico, cancelando-a automaticamente se normalizado.
     */
    public static EstoqueAlertaManager.ResumoAlertaEstoque avaliarENotificarEstoqueBaixo(
            Context context,
            List<Medicamento> medicamentos
    ) {
        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(context);
        EstoqueAlertaManager.ResumoAlertaEstoque resumo =
                EstoqueAlertaManager.avaliarMedicamentos(medicamentos, limiteMinimo);

        if (context == null) {
            return resumo;
        }

        criarCanalEstoqueNotificacao(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return resumo;
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(EstoqueAlertaManager.PREFS_ESTOQUE, Context.MODE_PRIVATE);

        if (!resumo.possuiAlertaCritico()) {
            manager.cancel(NOTIF_ID_ESTOQUE_BAIXO);
            prefs.edit().remove(KEY_ULTIMA_ASSINATURA_ESTOQUE).commit();
            return resumo;
        }

        List<Medicamento> criticos = resumo.getMedicamentosAbaixoDoLimite();
        int qtd = criticos.size();

        StringBuilder assinaturaBuilder = new StringBuilder();
        assinaturaBuilder.append(limiteMinimo).append(':').append(qtd);
        StringBuilder detalhes = new StringBuilder();
        int exibidos = Math.min(qtd, 5);
        for (int i = 0; i < criticos.size(); i++) {
            Medicamento m = criticos.get(i);
            assinaturaBuilder.append('|').append(m.getId()).append('=').append(m.getEstoque());
            if (i < exibidos) {
                if (detalhes.length() > 0) {
                    detalhes.append("\n");
                }
                String nomeRes = (m.getNomeIdoso() != null && !m.getNomeIdoso().trim().isEmpty())
                        ? " (" + m.getNomeIdoso() + ")" : "";
                String qtdTxt = (m.getEstoque() != null && !m.getEstoque().trim().isEmpty())
                        ? m.getEstoque() : "0 un.";
                detalhes.append("• ").append(m.getNomeMedicamento()).append(": ").append(qtdTxt).append(nomeRes);
            }
        }
        if (qtd > exibidos) {
            detalhes.append("\n+ ").append(qtd - exibidos).append(" outro(s) item(ns) crítico(s)");
        }

        String novaAssinatura = assinaturaBuilder.toString();
        String anterior = prefs.getString(KEY_ULTIMA_ASSINATURA_ESTOQUE, "");
        boolean mesmoEstado = novaAssinatura.equals(anterior);
        if (!mesmoEstado) {
            prefs.edit().putString(KEY_ULTIMA_ASSINATURA_ESTOQUE, novaAssinatura).apply();
        }

        String titulo = qtd == 1
                ? "Alerta de Estoque Baixo (1 item)"
                : "Alerta de Estoque Baixo (" + qtd + " itens)";
        String textoCurto = criticos.get(0).getNomeMedicamento()
                + " abaixo do mínimo (" + limiteMinimo + " un.). Toque para repor.";

        Intent intentEstoque = new Intent(context, EstoqueActivity.class);
        intentEstoque.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_ESTOQUE_BAIXO,
                intentEstoque,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CANAL_ESTOQUE_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(titulo)
                .setContentText(textoCurto)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(titulo + " • Mínimo: " + limiteMinimo + " un.")
                        .bigText(detalhes.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(mesmoEstado)
                .setContentIntent(pendingIntent);

        try {
            manager.notify(NOTIF_ID_ESTOQUE_BAIXO, builder.build());
        } catch (SecurityException se) {
            Log.w(TAG, "Sem permissão POST_NOTIFICATIONS para notificar estoque baixo: " + se.getMessage());
        }

        return resumo;
    }
}
