package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.PassagemTurnoAdapter;
import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.database.PassagemTurnoDAO;
import com.example.databinding.ActivityPassagemTurnoBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;
import com.example.model.PerfilUsuario;
import com.example.util.EstoqueAlertaManager;
import com.example.util.PdfReportGenerator;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;
import com.example.util.ValidadeMedicamentoHelper;
import com.example.util.WhatsAppUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Tela dedicada ao Módulo de Passagem de Turno e Observações da Equipe (Manhã, Tarde, Noite).
 * Permite registrar intercorrências clínicas, pendências para o próximo plantão, confirmar
 * recebimento de turno e exportar o relatório consolidado em PDF offline.
 */
public class PassagemTurnoActivity extends AppCompatActivity {

    private static final String TAG = "PassagemTurnoActivity";

    private ActivityPassagemTurnoBinding binding;
    private DatabaseHelper dbHelper;
    private MedicamentoDAO medicamentoDAO;
    private PassagemTurnoDAO passagemTurnoDAO;
    private IdosoDAO idosoDAO;
    private PassagemTurnoAdapter adapter;

    private List<PassagemTurno> todasPassagens = new ArrayList<>();
    private String filtroAtual = "TODOS";
    private int ultimoTotalDoses12h = 0;
    private int ultimoTotalPendentes = 0;
    private int ultimoAlertasEstoque = 0;
    private int ultimoAlertasValidade = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityPassagemTurnoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarEdgeToEdgeInsets();

        dbHelper = DatabaseHelper.getInstance(this);
        medicamentoDAO = new MedicamentoDAO(this);
        passagemTurnoDAO = new PassagemTurnoDAO(this);
        idosoDAO = new IdosoDAO(this);

        configurarToolbar();
        configurarRecyclerView();
        configurarFiltros();
        configurarBotoes();
        carregarPassagens();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarPassagens();
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingRvBase = binding.rvPassagemTurno.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPassagemTurno, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarPassagemTurno.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            binding.rvPassagemTurno.setPadding(
                    binding.rvPassagemTurno.getPaddingLeft(),
                    binding.rvPassagemTurno.getPaddingTop(),
                    binding.rvPassagemTurno.getPaddingRight(),
                    paddingRvBase + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void configurarToolbar() {
        binding.toolbarPassagemTurno.setNavigationOnClickListener(v -> finish());
    }

    private void configurarRecyclerView() {
        binding.rvPassagemTurno.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PassagemTurnoAdapter(this, new PassagemTurnoAdapter.OnPassagemActionListener() {
            @Override
            public void onConfirmarRecebimento(PassagemTurno passagem) {
                confirmarRecebimentoPassagem(passagem);
            }

            @Override
            public void onExcluir(PassagemTurno passagem) {
                confirmarExclusaoPassagem(passagem);
            }
        });
        binding.rvPassagemTurno.setAdapter(adapter);
    }

    private void configurarFiltros() {
        binding.chipGroupFiltroTurno.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filtroAtual = "TODOS";
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_filtro_pendentes) {
                    filtroAtual = "PENDENTES";
                } else if (id == R.id.chip_filtro_diurno) {
                    filtroAtual = PassagemTurno.TURNO_DIURNO;
                } else if (id == R.id.chip_filtro_noturno) {
                    filtroAtual = PassagemTurno.TURNO_NOTURNO;
                } else if (id == R.id.chip_filtro_manha) {
                    filtroAtual = PassagemTurno.TURNO_MANHA;
                } else if (id == R.id.chip_filtro_tarde) {
                    filtroAtual = PassagemTurno.TURNO_TARDE;
                } else if (id == R.id.chip_filtro_noite) {
                    filtroAtual = PassagemTurno.TURNO_NOITE;
                } else {
                    filtroAtual = "TODOS";
                }
            }
            aplicarFiltroNaLista();
        });
    }

    private void configurarBotoes() {
        binding.fabNovaPassagemTurno.setOnClickListener(v -> abrirDialogNovaPassagem());
        binding.btnExportarPassagemPdf.setOnClickListener(v -> exportarPassagemPdf());
        binding.btnCompartilharWhatsappTurno.setOnClickListener(v -> compartilharResumoWhatsApp());
    }

    public void carregarPassagens() {
        todasPassagens = passagemTurnoDAO.listarTodas();

        PerfilUsuario perfil = SessaoManager.getPerfil(this);
        adapter.setPodeExcluir(perfil == PerfilUsuario.ADMINISTRADOR || perfil == PerfilUsuario.ENFERMEIRO);

        boolean podeExportar = PermissaoUtils.podeGerarRelatorio(this);
        binding.btnExportarPassagemPdf.setVisibility(podeExportar ? View.VISIBLE : View.GONE);

        // Resumo automático do plantão consultando o banco local
        ultimoTotalDoses12h = dbHelper.contarDosesAdministradasUltimas12Horas();
        ultimoTotalPendentes = dbHelper.buscarDosesPendentes(-1).size();
        List<Medicamento> medicamentos = medicamentoDAO.listarTodosMedicamentos();
        ultimoAlertasEstoque = EstoqueAlertaManager
                .avaliarMedicamentos(medicamentos, EstoqueAlertaManager.getLimiteMinimoEstoque(this))
                .getQuantidadeAbaixoDoLimite();
        ultimoAlertasValidade = ValidadeMedicamentoHelper.contarAlertasValidade(medicamentos);

        binding.tvResumoPlantaoDoses12h.setText(String.valueOf(ultimoTotalDoses12h));
        binding.tvResumoPlantaoPendentes.setText(String.valueOf(ultimoTotalPendentes));
        binding.tvResumoPlantaoEstoque.setText(String.valueOf(ultimoAlertasEstoque + ultimoAlertasValidade));

        int pendentes = passagemTurnoDAO.contarPendentesConferencia();
        int urgentes = passagemTurnoDAO.contarUrgentesPendentes();

        String tituloResumo = pendentes == 0
                ? getString(R.string.resumo_passagem_em_dia)
                : (pendentes == 1
                ? getString(R.string.resumo_passagem_pendente_singular, pendentes)
                : getString(R.string.resumo_passagem_pendente_plural, pendentes));
        binding.tvResumoPassagemTitulo.setText(tituloResumo);

        String subResumo = urgentes == 0
                ? getString(R.string.resumo_passagem_sem_urgencia, todasPassagens.size())
                : getString(R.string.resumo_passagem_com_urgencia, urgentes, todasPassagens.size());
        binding.tvResumoPassagemSubtitulo.setText(subResumo);

        aplicarFiltroNaLista();
    }

    private void aplicarFiltroNaLista() {
        List<PassagemTurno> filtradas = new ArrayList<>();
        for (PassagemTurno pt : todasPassagens) {
            if ("TODOS".equals(filtroAtual)) {
                filtradas.add(pt);
            } else if ("PENDENTES".equals(filtroAtual)) {
                if (!pt.isConferido()) {
                    filtradas.add(pt);
                }
            } else if (PassagemTurno.TURNO_DIURNO.equalsIgnoreCase(filtroAtual)) {
                String t = pt.getTurno();
                if (PassagemTurno.TURNO_DIURNO.equalsIgnoreCase(t)
                        || PassagemTurno.TURNO_MANHA.equalsIgnoreCase(t)
                        || PassagemTurno.TURNO_TARDE.equalsIgnoreCase(t)) {
                    filtradas.add(pt);
                }
            } else if (PassagemTurno.TURNO_NOTURNO.equalsIgnoreCase(filtroAtual)) {
                String t = pt.getTurno();
                if (PassagemTurno.TURNO_NOTURNO.equalsIgnoreCase(t)
                        || PassagemTurno.TURNO_NOITE.equalsIgnoreCase(t)) {
                    filtradas.add(pt);
                }
            } else if (filtroAtual.equalsIgnoreCase(pt.getTurno())) {
                filtradas.add(pt);
            }
        }

        adapter.atualizarLista(filtradas);
        if (filtradas.isEmpty()) {
            binding.layoutVazioPassagem.setVisibility(View.VISIBLE);
            binding.rvPassagemTurno.setVisibility(View.GONE);
        } else {
            binding.layoutVazioPassagem.setVisibility(View.GONE);
            binding.rvPassagemTurno.setVisibility(View.VISIBLE);
        }
    }

    private void abrirDialogNovaPassagem() {
        View view = getLayoutInflater().inflate(R.layout.dialog_nova_passagem_turno, null);

        AutoCompleteTextView actvTurno = view.findViewById(R.id.actv_turno_plantao);
        AutoCompleteTextView actvResidente = view.findViewById(R.id.actv_residente_passagem);
        AutoCompleteTextView actvPrioridade = view.findViewById(R.id.actv_prioridade_passagem);
        TextInputLayout tilProfissional = view.findViewById(R.id.til_profissional_passagem);
        TextInputEditText etProfissional = view.findViewById(R.id.et_profissional_passagem);
        TextInputLayout tilObservacoes = view.findViewById(R.id.til_observacoes_passagem);
        TextInputEditText etObservacoes = view.findViewById(R.id.et_observacoes_passagem);
        TextInputEditText etIntercorrencias = view.findViewById(R.id.et_intercorrencias_passagem);
        TextInputEditText etPendencias = view.findViewById(R.id.et_pendencias_passagem);

        String[] turnos = {
                PassagemTurno.TURNO_DIURNO,
                PassagemTurno.TURNO_NOTURNO,
                PassagemTurno.TURNO_MANHA,
                PassagemTurno.TURNO_TARDE,
                PassagemTurno.TURNO_NOITE
        };
        actvTurno.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, turnos));
        actvTurno.setText(inferirTurnoAtual(), false);

        vincularChipOcorrenciaRapida(view.findViewById(R.id.chip_ocorrencia_recusa_alimentar), etIntercorrencias);
        vincularChipOcorrenciaRapida(view.findViewById(R.id.chip_ocorrencia_febre), etIntercorrencias);
        vincularChipOcorrenciaRapida(view.findViewById(R.id.chip_ocorrencia_queda), etIntercorrencias);
        vincularChipOcorrenciaRapida(view.findViewById(R.id.chip_ocorrencia_recusa_medicacao), etIntercorrencias);

        List<Idoso> idosos = idosoDAO.listarTodos();
        List<String> nomesResidentes = new ArrayList<>();
        nomesResidentes.add("Geral da Instituição");
        for (Idoso idoso : idosos) {
            nomesResidentes.add(idoso.getNome());
        }
        actvResidente.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, nomesResidentes));
        actvResidente.setText(nomesResidentes.get(0), false);

        final int[] idosoSelecionadoIndex = {0};
        actvResidente.setOnItemClickListener((parent, v, pos, id) -> idosoSelecionadoIndex[0] = pos);

        String[] prioridadesLabels = {"Normal", "Atenção", "Urgente"};
        String[] prioridadesCodigos = {
                PassagemTurno.PRIORIDADE_NORMAL,
                PassagemTurno.PRIORIDADE_ATENCAO,
                PassagemTurno.PRIORIDADE_URGENTE
        };
        actvPrioridade.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, prioridadesLabels));
        actvPrioridade.setText(prioridadesLabels[0], false);
        final int[] prioridadeIndex = {0};
        actvPrioridade.setOnItemClickListener((parent, v, pos, id) -> prioridadeIndex[0] = pos);

        PerfilUsuario perfil = SessaoManager.getPerfil(this);
        String nomeUsuario = SessaoManager.getInstance(this).getNomeUsuario();
        etProfissional.setText(nomeUsuario != null && !nomeUsuario.trim().isEmpty() ? nomeUsuario : perfil.getTitulo());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_nova_passagem_titulo)
                .setView(view)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.btn_salvar_passagem_turno, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String profissional = etProfissional.getText() != null ? etProfissional.getText().toString().trim() : "";
            String obs = etObservacoes.getText() != null ? etObservacoes.getText().toString().trim() : "";
            String intercorr = etIntercorrencias.getText() != null ? etIntercorrencias.getText().toString().trim() : "";
            String pendencias = etPendencias.getText() != null ? etPendencias.getText().toString().trim() : "";

            boolean valido = true;
            if (profissional.isEmpty()) {
                tilProfissional.setError("Informe o profissional responsável.");
                valido = false;
            } else {
                tilProfissional.setError(null);
            }
            if (obs.isEmpty()) {
                tilObservacoes.setError("Descreva as observações do plantão.");
                valido = false;
            } else {
                tilObservacoes.setError(null);
            }

            if (!valido) return;

            PassagemTurno nova = new PassagemTurno();
            nova.setTurno(actvTurno.getText().toString().trim().isEmpty()
                    ? inferirTurnoAtual() : actvTurno.getText().toString().trim());
            nova.setProfissionalOrigem(profissional);
            nova.setCargoOrigem(perfil.getTitulo());

            int idxRes = idosoSelecionadoIndex[0];
            if (idxRes > 0 && idxRes - 1 < idosos.size()) {
                Idoso sel = idosos.get(idxRes - 1);
                nova.setIdIdoso(sel.getId());
                nova.setNomeIdoso(sel.getNome());
            } else {
                nova.setIdIdoso(-1);
                nova.setNomeIdoso("Geral da Instituição");
            }

            int idxPrio = prioridadeIndex[0];
            nova.setPrioridade(prioridadesCodigos[Math.max(0, Math.min(idxPrio, prioridadesCodigos.length - 1))]);
            nova.setObservacoes(obs);
            nova.setIntercorrencias(intercorr);
            nova.setPendenciasProximoTurno(pendencias);

            long idGerado = passagemTurnoDAO.inserir(nova);
            if (idGerado != -1) {
                Toast.makeText(this, R.string.sucesso_passagem_registrada, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                carregarPassagens();
            } else {
                Toast.makeText(this, "Erro ao registrar passagem de turno.", Toast.LENGTH_SHORT).show();
            }
        }));

        dialog.show();
    }

    private void confirmarRecebimentoPassagem(PassagemTurno pt) {
        PerfilUsuario perfil = SessaoManager.getPerfil(this);
        String nomeUsuario = SessaoManager.getInstance(this).getNomeUsuario();
        String destino = (nomeUsuario != null && !nomeUsuario.trim().isEmpty())
                ? nomeUsuario : perfil.getTitulo();

        int atualizadas = passagemTurnoDAO.confirmarRecebimento(pt.getId(), destino);
        if (atualizadas > 0) {
            Toast.makeText(this, R.string.sucesso_passagem_conferida, Toast.LENGTH_SHORT).show();
            carregarPassagens();
        }
    }

    private void confirmarExclusaoPassagem(PassagemTurno pt) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Excluir Registro")
                .setMessage("Deseja remover este registro de passagem de turno?")
                .setPositiveButton(R.string.excluir, (d, w) -> {
                    passagemTurnoDAO.excluir(pt.getId());
                    carregarPassagens();
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    private void exportarPassagemPdf() {
        if (!PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File pdf = PdfReportGenerator.gerarRelatorioPassagemTurno(this, todasPassagens);
            Snackbar.make(binding.getRoot(), R.string.sucesso_pdf_gerado, Snackbar.LENGTH_LONG)
                    .setAction(R.string.acao_consultar_offline, v -> abrirNoVisualizadorOffline(pdf, "Passagem de Turno"))
                    .show();
            abrirNoVisualizadorOffline(pdf, "Passagem de Turno");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar PDF de passagem de turno", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void vincularChipOcorrenciaRapida(Chip chip, TextInputEditText etIntercorrencias) {
        if (chip == null || etIntercorrencias == null) {
            return;
        }
        chip.setOnClickListener(v -> {
            String ocorrencia = chip.getText() != null ? chip.getText().toString().trim() : "";
            if (ocorrencia.isEmpty()) {
                return;
            }
            String atual = etIntercorrencias.getText() != null ? etIntercorrencias.getText().toString().trim() : "";
            if (atual.toLowerCase().contains(ocorrencia.toLowerCase())) {
                return;
            }
            String novoTexto = atual.isEmpty() ? ocorrencia : atual + ", " + ocorrencia;
            etIntercorrencias.setText(novoTexto);
            etIntercorrencias.setSelection(novoTexto.length());
        });
    }

    private void compartilharResumoWhatsApp() {
        String mensagem = WhatsAppUtils.gerarResumoPassagemTurnoEquipe(
                ultimoTotalDoses12h,
                ultimoTotalPendentes,
                ultimoAlertasEstoque,
                ultimoAlertasValidade,
                todasPassagens
        );
        WhatsAppUtils.compartilharTextoWhatsAppEquipe(this, mensagem);
    }

    private void abrirNoVisualizadorOffline(File pdf, String titulo) {
        Intent intent = new Intent(this, VisualizadorPdfOfflineActivity.class);
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_CAMINHO_ARQUIVO_PDF, pdf.getAbsolutePath());
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_NOME_RESIDENTE, titulo);
        startActivity(intent);
    }

    private String inferirTurnoAtual() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora >= 6 && hora < 19) {
            return PassagemTurno.TURNO_DIURNO;
        }
        return PassagemTurno.TURNO_NOTURNO;
    }
}
