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
 * BroadcastReceiver acionado pelo sistema operacional no evento ACTION_BOOT_COMPLETED.
 *
 * Responsável por restaurar e reagendar todos os alarmes de medicamentos ativos da
 * ILPI Vovô Lar diretamente no AlarmManager logo após o dispositivo ser reiniciado,
 * descarregar ou ter seu sistema atualizado.
 *
 * Garante a confiabilidade da arquitetura Local-First (100% offline).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Log.i(TAG, "Dispositivo reiniciado. Restaurando alarmes ativos do CuidarPlus...");

            try {
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
                List<Medicamento> medicamentosAtivos = dbHelper.buscarMedicamentosAgendados();

                int contadorRestaurados = 0;
                for (Medicamento med : medicamentosAtivos) {
                    if (med != null && med.isAtivo()) {
                        AlarmScheduler.agendarAlarmeMedicamento(context, med);
                        contadorRestaurados++;
                    }
                }

                Log.i(TAG, "Sucesso: " + contadorRestaurados + " alarmes de medicação foram restaurados no AlarmManager.");
            } catch (Exception e) {
                Log.e(TAG, "Falha ao restaurar alarmes no boot: " + e.getMessage(), e);
            }
        }
    }
}
