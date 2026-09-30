package com.example;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.MedicamentoAdapter;
import com.example.alarm.AlarmScheduler;
import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.databinding.ActivityListaMedicamentosBinding;
import com.example.model.DoseAlertaDTO;
import com.example.model.HistoricoMedicacao;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity para Listagem de Medicamentos e Alertas do Dia (Sprint 2 Avançado).
 * - Exibe status por badges visuais (Verde = No Horário, Vermelho = Atrasado, Cinza = Concluído).
 * - Botão rápido "Dar Baixa Agora": grava no SQLite, emite som de confirmação com ToneGenerator
 *   e agenda automaticamente o próximo ciclo.
 * - Checagem e solicitação amigável de POST_NOTIFICATIONS (Android 13+) e SCHEDULE_EXACT_ALARM (Android 12+).
 */
public class ListaMedicamentosActivity extends AppCompatActivity {

    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";
    public static final String EXTRA_NOME_IDOSO = "EXTRA_NOME_IDOSO";

    private ActivityListaMedicamentosBinding binding;
    private DatabaseHelper dbHelper;
    private IdosoDAO idosoDAO;
    private MedicamentoAdapter adapter;

    private int idosoId = -1;
    private String nomeIdoso = "";
    private ToneGenerator toneGenerator;

    // Launcher nativo para permissão de notificações (Android 13+ / API 33+)
    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notificações ativadas para os alarmes.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Aviso: Sem notificações, os alarmes não poderão alertar a equipe.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListaMedicamentosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = DatabaseHelper.getInstance(this);
        idosoDAO = new IdosoDAO(this);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Exception ignored) {
        }

        recuperarDadosIntent();
        configurarToolbar();
        configurarRecyclerView();
        configurarBotoes();
        verificarPermissoesRuntime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarMedicamentos();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void recuperarDadosIntent() {
        if (getIntent().hasExtra(EXTRA_IDOSO_ID)) {
            idosoId = getIntent().getIntExtra(EXTRA_IDOSO_ID, -1);
        }
        if (getIntent().hasExtra(EXTRA_NOME_IDOSO)) {
            nomeIdoso = getIntent().getStringExtra(EXTRA_NOME_IDOSO);
        }

        if ((nomeIdoso == null || nomeIdoso.isEmpty()) && idosoId > 0) {
            Idoso idoso = idosoDAO.buscarPorId(idosoId);
            if (idoso != null) {
                nomeIdoso = idoso.getNome();
            }
        }

        if (idosoId <= 0) {
            List<Idoso> todos = idosoDAO.listarTodos();
            if (!todos.isEmpty()) {
                idosoId = todos.get(0).getId();
                nomeIdoso = todos.get(0).getNome();
            }
        }

        binding.tvNomeResidente.setText(nomeIdoso != null && !nomeIdoso.isEmpty() ? nomeIdoso : "Residente");
    }

    private void configurarToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void configurarRecyclerView() {
        binding.rvMedicamentos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicamentoAdapter(this, null, this::confirmarAdministracao);
        binding.rvMedicamentos.setAdapter(adapter);
    }

    private void configurarBotoes() {
        binding.btnNovoMedicamento.setOnClickListener(v -> {
            Intent intent = new Intent(this, CadastroMedicamentoActivity.class);
            if (idosoId > 0) {
                intent.putExtra(CadastroMedicamentoActivity.EXTRA_PRESELECTED_IDOSO_ID, idosoId);
            }
            startActivity(intent);
        });
    }

    private void carregarMedicamentos() {
        if (idosoId <= 0) {
            binding.layoutVazio.setVisibility(View.VISIBLE);
            binding.rvMedicamentos.setVisibility(View.GONE);
            binding.tvTotalMedicamentos.setText(R.string.total_medicamentos_zero);
            return;
        }

        // Utiliza o método DAO avançado buscarProximasDoses(id_idoso)
        List<DoseAlertaDTO> doses = dbHelper.buscarProximasDoses(idosoId);
        adapter.atualizarLista(doses);

        int total = doses.size();
        String totalTexto = total == 1
                ? getString(R.string.total_medicamentos_singular, total)
                : getString(R.string.total_medicamentos_plural, total);
        binding.tvTotalMedicamentos.setText(totalTexto);

        if (doses.isEmpty()) {
            binding.layoutVazio.setVisibility(View.VISIBLE);
            binding.rvMedicamentos.setVisibility(View.GONE);
        } else {
            binding.layoutVazio.setVisibility(View.GONE);
            binding.rvMedicamentos.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Confirmação antes de dar baixa na dose.
     */
    private void confirmarAdministracao(DoseAlertaDTO doseDTO) {
        Medicamento med = doseDTO.getMedicamento();
        String mensagem = getString(
                R.string.confirmar_administracao_msg,
                med.getNomeMedicamento(),
                med.getDosagem(),
                nomeIdoso
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirmar_administracao_titulo)
                .setMessage(mensagem)
                .setPositiveButton(R.string.btn_confirmar_administrado, (dialog, which) -> {
                    darBaixaMedicacao(doseDTO);
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    /**
     * Executa a baixa: grava no SQLite, emite som nativo com ToneGenerator
     * e reagenda o próximo disparo.
     */
    private void darBaixaMedicacao(DoseAlertaDTO doseDTO) {
        Medicamento med = doseDTO.getMedicamento();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        String agoraFormatado = sdf.format(new Date());

        HistoricoMedicacao historico = new HistoricoMedicacao();
        historico.setIdMedicamento(med.getId());
        historico.setIdIdoso(med.getIdIdoso());
        historico.setDataHoraPrescrita(doseDTO.getHorarioPrevisto());
        historico.setDataHoraExecutada(agoraFormatado);
        
        String statusExecucao = "ATRASADO".equalsIgnoreCase(doseDTO.getStatusPontualidade())
                ? HistoricoMedicacao.STATUS_ATRASADO : HistoricoMedicacao.STATUS_ADMINISTRADO;
        historico.setStatus(statusExecucao);
        historico.setNomeCuidador("Cuidador de Plantão");
        historico.setObservacao("Baixa efetuada via aplicativo na lista diária");

        long idHist = dbHelper.registrarAplicacao(historico);
        if (idHist != -1) {
            // Emite som discreto de confirmação nativo
            emitirSomConfirmacao();

            // Reagenda o próximo alarme com base no intervalo de horas
            AlarmScheduler.agendarProximoDisparo(this, med);

            String sucesso = getString(R.string.sucesso_dose_administrada, med.getNomeMedicamento());
            Toast.makeText(this, sucesso, Toast.LENGTH_LONG).show();

            // Atualiza a visualização
            carregarMedicamentos();
        } else {
            Toast.makeText(this, "Erro ao registrar baixa no histórico.", Toast.LENGTH_SHORT).show();
        }
    }

    private void emitirSomConfirmacao() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Checa e solicita permissões em tempo de execução:
     * - POST_NOTIFICATIONS (Android 13+ / API 33+)
     * - SCHEDULE_EXACT_ALARM (Android 12+ / API 31+)
     */
    private void verificarPermissoesRuntime() {
        // 1. Notificações no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permissao_notificacao_titulo)
                        .setMessage(R.string.dialog_permissao_notificacao_msg)
                        .setPositiveButton(R.string.btn_autorizar, (d, w) -> {
                            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                        })
                        .setNegativeButton(R.string.btn_cancelar, null)
                        .show();
            }
        }

        // 2. Alarme Exato no Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!AlarmScheduler.podeAgendarAlarmesExatos(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permissao_alarme_titulo)
                        .setMessage(R.string.dialog_permissao_alarme_msg)
                        .setPositiveButton(R.string.btn_abrir_configuracoes, (d, w) -> {
                            AlarmScheduler.abrirConfiguracaoAlarmeExato(this);
                        })
                        .setNegativeButton(R.string.btn_cancelar, null)
                        .show();
            }
        }
    }
}
