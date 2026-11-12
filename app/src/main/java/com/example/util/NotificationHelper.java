package com.example.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Utilitário responsável pelo gerenciamento de Canais de Notificação (NotificationChannel)
 * no aplicativo CuidarPlus.
 *
 * Configura um canal exclusivo com prioridade máxima (IMPORTANCE_HIGH), som de alarme nativo
 * e vibração destacada para garantir que as notificações de horários de medicação dos residentes
 * da ILPI Vovô Lar sejam exibidas como banners de alerta (heads-up) no Android 8.0+ (API 26+).
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    public static final String CANAL_ID = "cuidarplus_medicamentos_channel_v2";
    public static final String CANAL_NOME = "Alarmes de Medicamentos Críticos";
    public static final String CANAL_DESCRICAO = "Alarmes com som e vibração para administração pontual de medicamentos dos idosos";

    /**
     * Registra o canal de notificação no NotificationManager do Android.
     * Obrigatório para exibição de notificações a partir do Android 8.0 (Oreo / API 26+).
     *
     * @param context Contexto da aplicação
     */
    public static void criarCanalNotificacao(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                Log.w(TAG, "NotificationManager não disponível para registrar o canal.");
                return;
            }

            // Verifica se o canal já foi criado com as propriedades corretas
            NotificationChannel canalExistente = manager.getNotificationChannel(CANAL_ID);
            if (canalExistente != null) {
                return;
            }

            // Cria o canal com IMPORTANCE_HIGH para banner pop-up sonoro
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

            // Som nativo de alarme para alertar o cuidador
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
}
