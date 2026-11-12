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
import com.example.databinding.ActivityMedicamentosBinding;
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

    protected ActivityMedicamentosBinding binding;
    private DatabaseHelper dbHelper;
    private IdosoDAO idosoDAO;
    protected com.example.database.MedicamentoDAO medicamentoDAO;
    private MedicamentoAdapter adapter;

    private int idosoId = -1;
    private String nomeIdoso = "";
    private ToneGenerator toneGenerator;

    // Launcher para retorno da Activity de Edição de Medicamento
    private final ActivityResultLauncher<Intent> editarMedicamentoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    carregarMedicamentos();
                }
            });

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
        binding = ActivityMedicamentosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = DatabaseHelper.getInstance(this);
        idosoDAO = new IdosoDAO(this);
        medicamentoDAO = new com.example.database.MedicamentoDAO(this);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Exception ignored) {
        }

        recuperarDadosIntent();
        configurarToolbar();
        configurarRecyclerView();
        configurarBotoes();
        MedicamentoActivity.configurarCardEstoque(this, binding, medicamentoDAO);
        verificarPermissoesRuntime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        configurarBotoes();
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
        adapter = new MedicamentoAdapter(this, null, this::confirmarAdministracao, this::solicitarReposicaoMedicamento);
        adapter.setOnEditarClickListener(this::abrirEdicaoMedicamento);
        adapter.setOnExcluirClickListener(this::confirmarExclusaoMedicamento);
        binding.rvMedicamentos.setAdapter(adapter);
    }

    private void abrirEdicaoMedicamento(Medicamento med) {
        if (!com.example.util.PerfilManager.podeEditarExcluir(this)) {
            com.example.util.PerfilManager.exibirDialogAcessoRestrito(
                    this,
                    getString(R.string.aviso_perfil_restrito_med_msg),
                    this::carregarMedicamentos
            );
            return;
        }

        Intent intent = new Intent(this, EditarMedicamentoActivity.class);
        intent.putExtra(EditarMedicamentoActivity.EXTRA_MEDICAMENTO_ID, med.getId());
        editarMedicamentoLauncher.launch(intent);
    }

    private void confirmarExclusaoMedicamento(Medicamento med) {
        if (!com.example.util.PerfilManager.podeEditarExcluir(this)) {
            com.example.util.PerfilManager.exibirDialogAcessoRestrito(
                    this,
                    getString(R.string.aviso_perfil_restrito_med_msg),
                    this::carregarMedicamentos
            );
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirmar_exclusao_med_titulo)
                .setMessage(getString(R.string.confirmar_exclusao_med_msg, med.getNomeMedicamento()))
                .setPositiveButton(R.string.excluir, (dialog, which) -> {
                    try {
                        int linhas = medicamentoDAO.excluirMedicamento(med.getId());
                        if (linhas > 0) {
                            Toast.makeText(this, R.string.sucesso_medicamento_excluido, Toast.LENGTH_SHORT).show();
                            carregarMedicamentos();
                        } else {
                            Toast.makeText(this, "Erro ao excluir medicamento.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (com.example.database.AcessoNaoAutorizadoException ex) {
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    private void solicitarReposicaoMedicamento(DoseAlertaDTO doseDTO) {
        if (doseDTO == null || doseDTO.getMedicamento() == null) return;
        Medicamento med = doseDTO.getMedicamento();

        Idoso idoso = idosoDAO.buscarPorId(med.getIdIdoso() > 0 ? med.getIdIdoso() : idosoId);
        if (idoso != null && idoso.getContatoEmergencia() != null && !idoso.getContatoEmergencia().trim().isEmpty()) {
            String mensagem = com.example.util.WhatsAppUtils.gerarMensagemReposicaoMedicamento(
                    idoso.getNome(),
                    med.getNomeMedicamento(),
                    med.getDosagem()
            );
            com.example.util.WhatsAppUtils.abrirConversaFamiliar(this, idoso.getContatoEmergencia(), mensagem);
        } else {
            Toast.makeText(this, "Nenhum contato cadastrado para o familiar deste residente.", Toast.LENGTH_LONG).show();
        }
    }

    private void configurarBotoes() {
        // Restrição Dinâmica de Interface (UI) conforme matriz RBAC:
        // Cuidadores não possuem permissão para cadastrar ou editar prescrições.
        // O botão de cadastro é ocultado (View.GONE) para manter a interface limpa e segura.
        boolean podeCadastrar = com.example.util.PermissaoUtils.podeCadastrarMedicamento(this);
        binding.btnNovoMedicamento.setVisibility(podeCadastrar ? View.VISIBLE : View.GONE);
        binding.btnNovoMedicamento.setEnabled(podeCadastrar);

        // Botão de relatório de estoque / PDF acessível para ADMINISTRADOR e ENFERMEIRO
        boolean podeRelatorio = com.example.util.PermissaoUtils.podeGerarRelatorio(this);
        binding.btnExportarEstoquePdf.setVisibility(podeRelatorio ? View.VISIBLE : View.GONE);
        binding.btnExportarEstoquePdf.setEnabled(podeRelatorio);

        boolean exibirBarraAcoes = podeCadastrar || podeRelatorio;
        binding.layoutBotoesAcao.setVisibility(exibirBarraAcoes ? View.VISIBLE : View.GONE);

        binding.btnExportarEstoquePdf.setOnClickListener(v -> {
            Intent intent = new Intent(this, EstoqueActivity.class);
            startActivity(intent);
        });

        binding.btnNovoMedicamento.setOnClickListener(v -> {
            if (!com.example.util.PermissaoUtils.podeCadastrarMedicamento(this)) {
                com.example.util.PerfilManager.exibirDialogAcessoRestrito(
                        this,
                        getString(R.string.aviso_perfil_restrito_med_msg),
                        this::carregarMedicamentos
                );
                return;
            }

            Intent intent = new Intent(this, CadastroMedicamentoActivity.class);
            if (idosoId > 0) {
                intent.putExtra(CadastroMedicamentoActivity.EXTRA_PRESELECTED_IDOSO_ID, idosoId);
            }
            startActivity(intent);
        });
    }

    private void carregarMedicamentos() {
        MedicamentoActivity.atualizarCardEstoque(this, binding, medicamentoDAO);

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
            // Atualiza o estoque descontando 1 unidade e reflete eventual alerta mínimo
            dbHelper.decrementarEstoqueUmaUnidade(med.getId());

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
