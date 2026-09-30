package com.example.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.alarm.AlarmScheduler;
import com.example.database.DatabaseHelper;
import com.example.model.Medicamento;

import java.util.List;

/**
 * BroadcastReceiver acionado no evento ACTION_BOOT_COMPLETED.
 * Caso o dispositivo seja desligado, descarregue ou reiniciado, este componente
 * é despertado pelo Android para reagendar todos os alarmes ativos no AlarmManager,
 * garantindo que nenhum medicamento seja esquecido.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Log.i(TAG, "Dispositivo reiniciado ou app atualizado. Reagendando alarmes do CuidarPlus...");

            try {
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
                List<Medicamento> medicamentosAtivos = dbHelper.buscarMedicamentosAgendados();

                int agendados = 0;
                for (Medicamento med : medicamentosAtivos) {
                    if (med.isAtivo()) {
                        AlarmScheduler.agendarMedicamento(context, med);
                        agendados++;
                    }
                }

                Log.i(TAG, "Sucesso: " + agendados + " alarmes de medicação foram restaurados.");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao restaurar alarmes no boot: " + e.getMessage(), e);
            }
        }
    }
}
