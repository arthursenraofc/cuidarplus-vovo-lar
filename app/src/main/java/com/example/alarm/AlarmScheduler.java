package com.example.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.model.Medicamento;
import com.example.receiver.NotificationReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Helper avançado responsável por agendar, calcular e cancelar alarmes nativos via AlarmManager.
 * Utiliza setExactAndAllowWhileIdle() para disparar mesmo durante o modo Doze do Android.
 * Oferece métodos auxiliares para checagem e redirecionamento de permissão de alarme exato (Android 12+).
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    /**
     * Agenda o primeiro disparo do alarme para o medicamento cadastrado.
     * Calcula dinamicamente a próxima dose com base no horário inicial (HH:mm) e na frequência digitada.
     */
    public static void agendarMedicamento(Context context, Medicamento med) {
        if (med == null || !med.isAtivo() || med.getHorarioInicial() == null) {
            return;
        }

        try {
            String[] partesHora = med.getHorarioInicial().split(":");
            int hora = Integer.parseInt(partesHora[0].trim());
            int minuto = Integer.parseInt(partesHora[1].trim());

            Calendar agora = Calendar.getInstance();
            Calendar disparo = Calendar.getInstance();
            disparo.set(Calendar.HOUR_OF_DAY, hora);
            disparo.set(Calendar.MINUTE, minuto);
            disparo.set(Calendar.SECOND, 0);
            disparo.set(Calendar.MILLISECOND, 0);

            // Se o horário de hoje já passou, calcula o próximo ciclo conforme a frequência
            if (disparo.before(agora)) {
                int freqHoras = med.getFrequenciaHoras() > 0 ? med.getFrequenciaHoras() : 24;
                while (disparo.before(agora)) {
                    disparo.add(Calendar.HOUR_OF_DAY, freqHoras);
                }
            }

            agendarExacto(context, med, disparo.getTimeInMillis());
            Log.d(TAG, "Alarme agendado para: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(disparo.getTime()));

        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular horário de agendamento: " + e.getMessage());
        }
    }

    /**
     * Reagenda o alarme adicionando o intervalo (frequenciaHoras) a partir do momento atual.
     */
    public static void agendarProximoDisparo(Context context, Medicamento med) {
        if (med == null || !med.isAtivo() || med.getFrequenciaHoras() <= 0) {
            return;
        }

        Calendar proximo = Calendar.getInstance();
        proximo.add(Calendar.HOUR_OF_DAY, med.getFrequenciaHoras());

        agendarExacto(context, med, proximo.getTimeInMillis());
    }

    /**
     * Registra o alarme no AlarmManager usando setExactAndAllowWhileIdle para romper o modo Doze.
     */
    private static void agendarExacto(Context context, Medicamento med, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra(NotificationReceiver.EXTRA_MED_ID, med.getId());
        intent.putExtra(NotificationReceiver.EXTRA_IDOSO_ID, med.getIdIdoso());
        intent.putExtra(NotificationReceiver.EXTRA_NOME_MED, med.getNomeMedicamento());
        intent.putExtra(NotificationReceiver.EXTRA_DOSAGEM, med.getDosagem());
        intent.putExtra(NotificationReceiver.EXTRA_NOME_IDOSO, med.getNomeIdoso());
        intent.putExtra(NotificationReceiver.EXTRA_TIPO, med.getTipo());
        intent.putExtra(NotificationReceiver.EXTRA_OBS, med.getObservacaoMedica());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                med.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Permissão de alarme exato não concedida, aplicando alarme regular: " + se.getMessage());
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Verifica se o aplicativo possui autorização para agendar alarmes exatos (Android 12+ / API 31+).
     */
    public static boolean podeAgendarAlarmesExatos(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Redireciona o usuário para as configurações nativas de permissão de alarme exato caso esteja bloqueado.
     */
    public static void abrirConfiguracaoAlarmeExato(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir tela de configuração de alarme: " + e.getMessage());
            }
        }
    }

    /**
     * Cancela o alarme de um medicamento pelo seu ID.
     */
    public static void cancelarAlarme(Context context, int medId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || medId <= 0) {
            return;
        }

        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                medId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }
}
