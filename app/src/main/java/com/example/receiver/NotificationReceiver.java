package com.example.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.ListaMedicamentosActivity;
import com.example.R;
import com.example.alarm.AlarmScheduler;
import com.example.database.DatabaseHelper;
import com.example.model.HistoricoMedicacao;
import com.example.model.Medicamento;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * BroadcastReceiver avançado acionado pelo AlarmManager.
 * - Cria NotificationChannel de Alta Prioridade (IMPORTANCE_HIGH/MAX) com som e vibração de alarme.
 * - Exibe notificação rica com botão de Ação Direta "Marcar como Administrado" direto da barra de notificações.
 * - Trata o clique da ação rápida em background registrando a baixa no SQLite e reagendando o próximo alarme.
 */
public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotificationReceiver";

    public static final String CHANNEL_ID = "cuidarplus_medicamentos_channel_v2";
    public static final String CHANNEL_NAME = "Alarmes de Medicamentos Críticos";
    public static final String CHANNEL_DESC = "Alarmes e notificações com som e vibração para administração de remédios";

    public static final String ACTION_MARCAR_ADMINISTRADO = "com.example.cuidarplus.ACTION_MARCAR_ADMINISTRADO";

    public static final String EXTRA_MED_ID = "EXTRA_MED_ID";
    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";
    public static final String EXTRA_NOME_MED = "EXTRA_NOME_MED";
    public static final String EXTRA_DOSAGEM = "EXTRA_DOSAGEM";
    public static final String EXTRA_NOME_IDOSO = "EXTRA_NOME_IDOSO";
    public static final String EXTRA_TIPO = "EXTRA_TIPO";
    public static final String EXTRA_OBS = "EXTRA_OBS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        int medId = intent.getIntExtra(EXTRA_MED_ID, -1);
        int idosoId = intent.getIntExtra(EXTRA_IDOSO_ID, -1);
        String nomeMed = intent.getStringExtra(EXTRA_NOME_MED);
        String dosagem = intent.getStringExtra(EXTRA_DOSAGEM);
        String nomeIdoso = intent.getStringExtra(EXTRA_NOME_IDOSO);
        String tipo = intent.getStringExtra(EXTRA_TIPO);
        String obs = intent.getStringExtra(EXTRA_OBS);

        if (nomeMed == null || nomeMed.trim().isEmpty()) {
            nomeMed = "Medicamento";
        }
        if (dosagem == null) {
            dosagem = "";
        }
        if (nomeIdoso == null || nomeIdoso.trim().isEmpty()) {
            nomeIdoso = "Residente";
        }
        if (tipo == null || tipo.trim().isEmpty()) {
            tipo = "Comprimido";
        }

        // Caso o usuário tenha clicado no botão "Marcar como Administrado" direto na notificação:
        if (ACTION_MARCAR_ADMINISTRADO.equals(action)) {
            tratarAcaoDiretaAdministrado(context, medId, idosoId, nomeMed);
            return;
        }

        // Caso contrário: Exibir Alarme / Notificação Rica
        exibirNotificacaoAlarme(context, medId, idosoId, nomeMed, dosagem, nomeIdoso, tipo, obs);
    }

    /**
     * Cria canal de notificação com IMPORTANCE_HIGH, som de alarme nativo e padrão de vibração.
     */
    private void criarCanalNotificacao(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

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
        }
    }

    private void exibirNotificacaoAlarme(Context context, int medId, int idosoId, String nomeMed,
                                        String dosagem, String nomeIdoso, String tipo, String obs) {
        criarCanalNotificacao(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        int notifId = medId > 0 ? medId : (int) System.currentTimeMillis();

        // Intent principal ao tocar na notificação (Abre a lista do residente)
        Intent intentPrincipal = new Intent(context, ListaMedicamentosActivity.class);
        intentPrincipal.putExtra(ListaMedicamentosActivity.EXTRA_IDOSO_ID, idosoId);
        intentPrincipal.putExtra(ListaMedicamentosActivity.EXTRA_NOME_IDOSO, nomeIdoso);
        intentPrincipal.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntentPrincipal = PendingIntent.getActivity(
                context,
                notifId,
                intentPrincipal,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // PendingIntent para AÇÃO DIRETA "Marcar como Administrado"
        Intent intentAcao = new Intent(context, NotificationReceiver.class);
        intentAcao.setAction(ACTION_MARCAR_ADMINISTRADO);
        intentAcao.putExtra(EXTRA_MED_ID, medId);
        intentAcao.putExtra(EXTRA_IDOSO_ID, idosoId);
        intentAcao.putExtra(EXTRA_NOME_MED, nomeMed);

        PendingIntent pendingIntentAcao = PendingIntent.getBroadcast(
                context,
                notifId + 100000,
                intentAcao,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String titulo = "HORA DO MEDICAMENTO: " + nomeMed;
        StringBuilder corpo = new StringBuilder();
        corpo.append("Residente: ").append(nomeIdoso).append("\n");
        corpo.append("Dose: ").append(dosagem).append(" (").append(tipo).append(")");
        if (obs != null && !obs.trim().isEmpty()) {
            corpo.append("\nObs: ").append(obs);
        }

        Uri alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmeUri == null) {
            alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle(titulo)
                .setContentText("Administrar " + nomeMed + " (" + dosagem + ") para " + nomeIdoso)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(corpo.toString()))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(alarmeUri)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
                .setAutoCancel(true)
                .setContentIntent(pendingIntentPrincipal)
                .addAction(
                        R.drawable.ic_check,
                        context.getString(R.string.notif_action_administrado),
                        pendingIntentAcao
                );

        manager.notify(notifId, builder.build());

        // Reagendamento preventivo do ciclo seguinte
        if (medId > 0) {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
            Medicamento med = dbHelper.buscarMedicamentoPorId(medId);
            if (med != null && med.isAtivo() && med.getFrequenciaHoras() > 0) {
                AlarmScheduler.agendarProximoDisparo(context, med);
            }
        }
    }

    /**
     * Processa a baixa imediata vinda do botão da notificação nativa sem abrir a tela.
     */
    private void tratarAcaoDiretaAdministrado(Context context, int medId, int idosoId, String nomeMed) {
        Log.i(TAG, "Ação Direta acionada para medicamento ID: " + medId);

        // Cancela a notificação ativa da barra de status
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && medId > 0) {
            manager.cancel(medId);
        }

        // Registra no SQLite
        try {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            String agoraStr = sdf.format(new Date());

            HistoricoMedicacao hist = new HistoricoMedicacao();
            hist.setIdMedicamento(medId);
            hist.setIdIdoso(idosoId);
            hist.setDataHoraPrescrita(agoraStr);
            hist.setDataHoraExecutada(agoraStr);
            hist.setStatus(HistoricoMedicacao.STATUS_ADMINISTRADO);
            hist.setNomeCuidador("Cuidador (Notificação)");
            hist.setObservacao("Baixa efetuada diretamente pelo botão da notificação");

            dbHelper.registrarAplicacao(hist);

            // Reagenda o próximo disparo com base no intervalo
            Medicamento med = dbHelper.buscarMedicamentoPorId(medId);
            if (med != null && med.isAtivo()) {
                AlarmScheduler.agendarProximoDisparo(context, med);
            }

            Toast.makeText(context, context.getString(R.string.notif_dose_administrada_sucesso, nomeMed), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar baixa via notificação: " + e.getMessage(), e);
        }
    }
}
