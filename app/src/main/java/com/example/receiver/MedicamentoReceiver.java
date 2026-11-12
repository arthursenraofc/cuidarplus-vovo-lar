package com.example.receiver;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
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
import com.example.util.NotificationHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * BroadcastReceiver responsável pelo recebimento dos alarmes de medicamentos
 * disparados pelo AlarmManager no CuidarPlus.
 *
 * Ao ser disparado:
 * 1. Extrai os dados do medicamento (nome do idoso, medicamento, dosagem, tipo, observações).
 * 2. Garante o registro do NotificationChannel de alta prioridade via NotificationHelper.
 * 3. Monta e exibe a notificação com som de alarme, padrão de vibração e botão de ação direta.
 * 4. Configura PendingIntent para abrir os detalhes/lista de medicamentos do idoso ao tocar.
 * 5. Permite baixa rápida ("Marcar como Administrado") diretamente da barra de notificações.
 * 6. Reagenda o próximo ciclo no AlarmManager caso seja medicação recorrente.
 */
public class MedicamentoReceiver extends BroadcastReceiver {

    private static final String TAG = "MedicamentoReceiver";

    public static final String ACTION_DISPARAR_ALARME = "com.example.cuidarplus.ACTION_DISPARAR_ALARME";
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
        if (context == null || intent == null) {
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
            tipo = "Dose";
        }

        // Ação rápida vinda do botão da notificação nativa
        if (ACTION_MARCAR_ADMINISTRADO.equals(action)) {
            tratarAcaoDiretaAdministrado(context, medId, idosoId, nomeMed);
            return;
        }

        // Exibe a notificação de alerta sonoro e visual
        exibirNotificacao(context, medId, idosoId, nomeMed, dosagem, nomeIdoso, tipo, obs);
    }

    /**
     * Constrói e exibe a notificação de alarme com som, vibração e PendingIntent.
     */
    private void exibirNotificacao(Context context, int medId, int idosoId, String nomeMed,
                                   String dosagem, String nomeIdoso, String tipo, String obs) {
        // Garante a existência do canal de notificação com alta prioridade
        NotificationHelper.criarCanalNotificacao(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            Log.e(TAG, "NotificationManager inacessível.");
            return;
        }

        int notifId = medId > 0 ? medId : (int) (System.currentTimeMillis() % 100000);

        // Intent para abrir a tela de medicamentos do idoso ao tocar no corpo da notificação
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

        // Intent para ação rápida "Marcar como Administrado" direto na notificação
        Intent intentAcao = new Intent(context, MedicamentoReceiver.class);
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
        corpo.append("Dose prescrita: ").append(dosagem).append(" (").append(tipo).append(")");
        if (obs != null && !obs.trim().isEmpty()) {
            corpo.append("\nObs: ").append(obs);
        }

        Uri alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmeUri == null) {
            alarmeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CANAL_ID)
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

        try {
            manager.notify(notifId, builder.build());
            Log.d(TAG, "Notificação de alerta disparada com sucesso para medicamento ID: " + medId);
        } catch (SecurityException se) {
            Log.w(TAG, "Sem permissão POST_NOTIFICATIONS para disparar notificação: " + se.getMessage());
        }

        // Reagenda o próximo ciclo no banco caso haja frequência de repetição definida
        if (medId > 0) {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
            Medicamento med = dbHelper.buscarMedicamentoPorId(medId);
            if (med != null && med.isAtivo() && med.getFrequenciaHoras() > 0) {
                AlarmScheduler.agendarProximoDisparo(context, med);
            }
        }
    }

    /**
     * Processa a confirmação imediata da dose efetuada pelo botão na notificação.
     */
    private void tratarAcaoDiretaAdministrado(Context context, int medId, int idosoId, String nomeMed) {
        Log.i(TAG, "Ação rápida 'Administrado' executada para medicamento ID: " + medId);

        // Remove a notificação da barra de status
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && medId > 0) {
            manager.cancel(medId);
        }

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

            // Reagenda o próximo ciclo conforme intervalo
            Medicamento med = dbHelper.buscarMedicamentoPorId(medId);
            if (med != null && med.isAtivo()) {
                AlarmScheduler.agendarProximoDisparo(context, med);
            }

            Toast.makeText(
                    context,
                    context.getString(R.string.notif_dose_administrada_sucesso, nomeMed),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar aplicação via notificação: " + e.getMessage(), e);
        }
    }
}
