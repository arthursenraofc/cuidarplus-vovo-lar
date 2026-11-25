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
import androidx.lifecycle.ViewModelProvider;
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
import com.example.ui.viewmodel.FiltroMedicacao;
import com.example.ui.viewmodel.MedicamentosUiState;
import com.example.ui.viewmodel.MedicamentosViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private FiltroMedicacao filtroAtual = FiltroMedicacao.SOMENTE_PENDENTES;
    private MedicamentosViewModel medicamentosViewModel;
    private long versaoSnapshotAtual = 0L;
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
        medicamentosViewModel = new ViewModelProvider(
                this,
                MedicamentosViewModel.factory(getApplication())
        ).get(MedicamentosViewModel.class);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Exception ignored) {
        }

        recuperarDadosIntent();
        configurarToolbar();
        configurarRecyclerView();
        configurarBotoes();
        observarViewModel();
        MedicamentoActivity.configurarCardEstoque(this, binding, medicamentoDAO);
        verificarPermissoesRuntime();
    }

    private void observarViewModel() {
        medicamentosViewModel.observarNoLifecycle(this, state -> {
            if (state instanceof MedicamentosUiState.Sucesso) {
                MedicamentosUiState.Sucesso sucesso = (MedicamentosUiState.Sucesso) state;
                // Ignora emissões anteriores à conclusão da sincronização do snapshot corrente
                if (versaoSnapshotAtual > 0L && sucesso.getSnapshotVersion() >= versaoSnapshotAtual) {
                    renderizarListaDoses(
                            sucesso.getDoses(),
                            sucesso.getTotalAtrasados(),
                            sucesso.getTotalPendentes()
                    );
                }
            }
        });
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
        } else {
            idosoId = -1;
        }
        if (getIntent().hasExtra(EXTRA_NOME_IDOSO)) {
            nomeIdoso = getIntent().getStringExtra(EXTRA_NOME_IDOSO);
        } else {
            nomeIdoso = "";
        }

        if ((nomeIdoso == null || nomeIdoso.isEmpty()) && idosoId > 0) {
            Idoso idoso = idosoDAO.buscarPorId(idosoId);
            if (idoso != null) {
                nomeIdoso = idoso.getNome();
            }
        }

        filtroAtual = idosoId > 0 ? FiltroMedicacao.POR_IDOSO : FiltroMedicacao.SOMENTE_PENDENTES;
        atualizarTituloCabecalho(0);
        View.OnClickListener abrirFiltro = v -> exibirDialogFiltroResidente();
        binding.tvNomeResidente.setOnClickListener(abrirFiltro);
        binding.containerIconeMed.setOnClickListener(abrirFiltro);
    }

    private void atualizarTituloCabecalho(int qtdAtrasados) {
        if (idosoId > 0) {
            binding.tvNomeResidente.setText(nomeIdoso != null && !nomeIdoso.isEmpty() ? nomeIdoso : "Residente");
        } else if (filtroAtual == FiltroMedicacao.SOMENTE_ATRASADOS) {
            binding.tvNomeResidente.setText("Somente Doses Atrasadas (" + qtdAtrasados + ")");
        } else if (filtroAtual == FiltroMedicacao.TODOS_DO_DIA) {
            binding.tvNomeResidente.setText("Todas do Dia (Inclui Concluídas)");
        } else if (qtdAtrasados > 0) {
            binding.tvNomeResidente.setText("Plantão Geral • " + qtdAtrasados + " atrasada(s) no topo");
        } else {
            binding.tvNomeResidente.setText("Plantão Geral • Doses Pendentes");
        }
    }

    private void exibirDialogFiltroResidente() {
        List<Idoso> todos = idosoDAO.listarTodos();
        List<String> opcoes = new ArrayList<>();
        opcoes.add("Pendentes do Plantão (Atrasados no Topo)");
        opcoes.add("Somente Doses Atrasadas (Pendentes)");
        opcoes.add("Todas do Dia (Incluir Concluídas)");
        for (Idoso idoso : todos) {
            opcoes.add(idoso.getNome());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filtrar Medicamentos do Dia")
                .setItems(opcoes.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        idosoId = -1;
                        nomeIdoso = "";
                        filtroAtual = FiltroMedicacao.SOMENTE_PENDENTES;
                    } else if (which == 1) {
                        idosoId = -1;
                        nomeIdoso = "";
                        filtroAtual = FiltroMedicacao.SOMENTE_ATRASADOS;
                    } else if (which == 2) {
                        idosoId = -1;
                        nomeIdoso = "";
                        filtroAtual = FiltroMedicacao.TODOS_DO_DIA;
                    } else {
                        Idoso selecionado = todos.get(which - 3);
                        idosoId = selecionado.getId();
                        nomeIdoso = selecionado.getNome();
                        filtroAtual = FiltroMedicacao.POR_IDOSO;
                    }
                    carregarMedicamentos();
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
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

        List<DoseAlertaDTO> dosesBase = idosoId > 0
                ? dbHelper.buscarProximasDoses(idosoId)
                : dbHelper.buscarAlertasDoDia();

        int qtdAtrasados = 0;
        int qtdPendentes = 0;
        for (DoseAlertaDTO d : dosesBase) {
            boolean pendente = !d.isAdministradoHoje() && !"CONCLUIDO".equalsIgnoreCase(d.getStatusPontualidade());
            if (pendente) {
                qtdPendentes++;
                if ("ATRASADO".equalsIgnoreCase(d.getStatusPontualidade())) {
                    qtdAtrasados++;
                }
            }
        }

        // Sincroniza o snapshot local para o Room Database e atualiza os parâmetros reativos do ViewModel
        String hojeStr = MedicamentosViewModel.dataAtualFormatada();
        versaoSnapshotAtual++;
        medicamentosViewModel.aplicarFiltro(filtroAtual, idosoId);
        medicamentosViewModel.sincronizarDados(
                idosoDAO.listarTodos(),
                dbHelper.listarTodosMedicamentos(),
                dbHelper.listarHistoricoDoDia(hojeStr),
                hojeStr,
                versaoSnapshotAtual
        );

        // Filtragem determinística imediata garantindo exibição exclusiva de registros pendentes (sem duplicidade)
        List<DoseAlertaDTO> dosesFiltradas = MedicamentosViewModel.filtrarDosesPendentes(
                dosesBase,
                filtroAtual,
                idosoId
        );
        renderizarListaDoses(dosesFiltradas, qtdAtrasados, qtdPendentes);
    }

    private void renderizarListaDoses(List<DoseAlertaDTO> doses, int qtdAtrasados, int qtdPendentes) {
        atualizarTituloCabecalho(qtdAtrasados);
        adapter.atualizarLista(doses);

        int total = doses.size();
        String totalTexto = total == 1
                ? getString(R.string.total_medicamentos_singular, total)
                : getString(R.string.total_medicamentos_plural, total);
        if (qtdAtrasados > 0 && idosoId <= 0) {
            totalTexto = totalTexto + " • Toque acima p/ filtrar";
        } else if (idosoId <= 0) {
            totalTexto = totalTexto + (filtroAtual == FiltroMedicacao.TODOS_DO_DIA
                    ? " • Todas do dia"
                    : " • Somente pendentes (" + qtdPendentes + ")");
        }
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
        String nomeParaDialog = med.getNomeIdoso();
        if (nomeParaDialog == null || nomeParaDialog.trim().isEmpty()) {
            nomeParaDialog = nomeIdoso;
        }
        if ((nomeParaDialog == null || nomeParaDialog.trim().isEmpty()) && med.getIdIdoso() > 0) {
            Idoso idoso = idosoDAO.buscarPorId(med.getIdIdoso());
            if (idoso != null) {
                nomeParaDialog = idoso.getNome();
            }
        }
        if (nomeParaDialog == null || nomeParaDialog.trim().isEmpty()) {
            nomeParaDialog = "Residente";
        }

        String mensagem = getString(
                R.string.confirmar_administracao_msg,
                med.getNomeMedicamento(),
                med.getDosagem(),
                nomeParaDialog
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
     * Executa a baixa: grava no SQLite, emite som nativo com ToneGenerator,
     * desconta estoque, avalia notificação de estoque baixo e reagenda o próximo disparo.
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
            // Atualiza o estoque descontando 1 unidade e dispara alerta de estoque se necessário
            dbHelper.decrementarEstoqueUmaUnidade(med.getId());
            Medicamento atualizado = dbHelper.buscarMedicamentoPorId(med.getId());
            String novoEstoque = atualizado != null ? atualizado.getEstoque() : "";

            // Atualiza também a camada Room/ViewModel para remover imediatamente a dose das pendências
            medicamentosViewModel.registrarBaixaDose(
                    doseDTO,
                    "Cuidador de Plantão",
                    "Baixa efetuada via aplicativo na lista diária",
                    novoEstoque,
                    MedicamentosViewModel.dataAtualFormatada(),
                    agoraFormatado
            );

            com.example.util.NotificationHelper.avaliarENotificarEstoqueBaixo(
                    this, medicamentoDAO.listarTodosMedicamentos()
            );

            // Emite som discreto de confirmação nativo
            emitirSomConfirmacao();

            // Reagenda o próximo alarme com base no intervalo de horas
            AlarmScheduler.agendarProximoDisparo(this, med);

            String sucesso = getString(R.string.sucesso_dose_administrada, med.getNomeMedicamento());
            Toast.makeText(this, sucesso, Toast.LENGTH_LONG).show();

            // Atualiza a visualização removendo o registro concluído da lista de pendentes
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
