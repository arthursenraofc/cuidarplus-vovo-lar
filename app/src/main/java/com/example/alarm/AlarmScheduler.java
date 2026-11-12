package com.example.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.model.Medicamento;
import com.example.receiver.MedicamentoReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Gerenciador e Agendador de Alarmes Nativos do CuidarPlus.
 * Utiliza o AlarmManager do Android com alarmes exatos (setExactAndAllowWhileIdle ou setAlarmClock)
 * para contornar o modo Doze e as otimizações de bateria do Android, garantindo que
 * o cuidador seja notificado pontualmente no horário prescrito para cada residente.
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    /**
     * Agenda um alarme exato para o medicamento cadastrado utilizando AlarmManager e PendingIntent.getBroadcast.
     * Calcula o próximo horário válido com base no horário inicial (HH:mm) e no intervalo de repetição.
     *
     * @param context Contexto da aplicação
     * @param med Objeto Medicamento com dados da prescrição
     */
    public static void agendarAlarmeMedicamento(Context context, Medicamento med) {
        if (context == null || med == null || !med.isAtivo() || med.getHorarioInicial() == null) {
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

            // Caso o horário inicial de hoje já tenha passado, avança para o próximo ciclo
            if (disparo.before(agora)) {
                int freqHoras = med.getFrequenciaHoras() > 0 ? med.getFrequenciaHoras() : 24;
                while (disparo.before(agora)) {
                    disparo.add(Calendar.HOUR_OF_DAY, freqHoras);
                }
            }

            agendarExacto(context, med, disparo.getTimeInMillis());
            Log.d(TAG, "Alarme programado com sucesso para: "
                    + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(disparo.getTime()));

        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular horário de agendamento do medicamento: " + e.getMessage(), e);
        }
    }

    /**
     * Alias para agendarAlarmeMedicamento, mantendo compatibilidade com classes existentes.
     */
    public static void agendarMedicamento(Context context, Medicamento med) {
        agendarAlarmeMedicamento(context, med);
    }

    /**
     * Reagenda o próximo disparo de um medicamento recorrente adicionando seu intervalo em horas.
     */
    public static void agendarProximoDisparo(Context context, Medicamento med) {
        if (context == null || med == null || !med.isAtivo() || med.getFrequenciaHoras() <= 0) {
            return;
        }

        Calendar proximo = Calendar.getInstance();
        proximo.add(Calendar.HOUR_OF_DAY, med.getFrequenciaHoras());

        agendarExacto(context, med, proximo.getTimeInMillis());
    }

    /**
     * Cancela o alarme exato de um medicamento no AlarmManager.
     *
     * @param context Contexto da aplicação
     * @param medId ID único do medicamento
     */
    public static void cancelarAlarmeMedicamento(Context context, int medId) {
        if (context == null || medId <= 0) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, MedicamentoReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                medId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Alarme cancelado com sucesso no AlarmManager para medicamento ID: " + medId);
        }
    }

    /**
     * Alias para cancelarAlarmeMedicamento, garantindo compatibilidade contínua no projeto.
     */
    public static void cancelarAlarme(Context context, int medId) {
        cancelarAlarmeMedicamento(context, medId);
    }

    /**
     * Registra o alarme nativo no AlarmManager.
     * Utiliza setExactAndAllowWhileIdle() ou setAlarmClock() para contornar o Doze Mode.
     */
    private static void agendarExacto(Context context, Medicamento med, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager indisponível.");
            return;
        }

        Intent intent = new Intent(context, MedicamentoReceiver.class);
        intent.setAction(MedicamentoReceiver.ACTION_DISPARAR_ALARME);
        intent.putExtra(MedicamentoReceiver.EXTRA_MED_ID, med.getId());
        intent.putExtra(MedicamentoReceiver.EXTRA_IDOSO_ID, med.getIdIdoso());
        intent.putExtra(MedicamentoReceiver.EXTRA_NOME_MED, med.getNomeMedicamento());
        intent.putExtra(MedicamentoReceiver.EXTRA_DOSAGEM, med.getDosagem());
        intent.putExtra(MedicamentoReceiver.EXTRA_NOME_IDOSO, med.getNomeIdoso());
        intent.putExtra(MedicamentoReceiver.EXTRA_TIPO, med.getTipo());
        intent.putExtra(MedicamentoReceiver.EXTRA_OBS, med.getObservacaoMedica());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                med.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // Android 12+ com permissão para alarme exato:
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    // Fallback para alarme que acorda enquanto idle sem exigir exceção
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                // Android 6.0 a 11:
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Disparo registrado para medicamento " + med.getNomeMedicamento() + " (ID: " + med.getId() + ")");
        } catch (SecurityException se) {
            Log.w(TAG, "Permissão de alarme exato indisponível. Utilizando agendamento padrão: " + se.getMessage());
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * Checa se o aplicativo tem permissão para agendar alarmes exatos (Android 12+ / API 31+).
     */
    public static boolean podeAgendarAlarmesExatos(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Redireciona o usuário para as configurações do sistema para liberar alarmes exatos se necessário.
     */
    public static void abrirConfiguracaoAlarmeExato(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir configurações de alarme exato: " + e.getMessage());
            }
        }
    }
}
