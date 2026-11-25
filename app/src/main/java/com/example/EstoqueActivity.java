package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.EstoqueAdapter;
import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.database.PassagemTurnoDAO;
import com.example.databinding.ActivityEstoqueBinding;
import com.example.model.DoseAlertaDTO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;
import com.example.util.EstoqueAlertaManager;
import com.example.util.NotificationHelper;
import com.example.util.PdfReportGenerator;
import com.example.util.PermissaoUtils;
import com.example.util.WhatsAppUtils;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Activity para Controle do Estoque Geral de Medicamentos e Hub de Relatórios PDF 100% Offline.
 * Permite a visualização unificada de todo o estoque da ILPI Vovô Lar (priorizando itens críticos),
 * emissão de relatórios em PDF (Estoque, Doses do Dia, Gerencial Consolidado) e acionamento rápido
 * de mensagens via WhatsApp para reposição.
 */
public class EstoqueActivity extends AppCompatActivity {

    private static final String TAG = "EstoqueActivity";

    private ActivityEstoqueBinding binding;
    private MedicamentoDAO medicamentoDAO;
    private IdosoDAO idosoDAO;
    private PassagemTurnoDAO passagemTurnoDAO;
    private DatabaseHelper dbHelper;
    private EstoqueAdapter adapter;
    private List<Medicamento> todosMedicamentos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEstoqueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        medicamentoDAO = new MedicamentoDAO(this);
        idosoDAO = new IdosoDAO(this);
        passagemTurnoDAO = new PassagemTurnoDAO(this);
        dbHelper = DatabaseHelper.getInstance(this);

        configurarToolbar();
        configurarRecyclerView();
        configurarBusca();
        configurarExportacaoPdf();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aplicarRestricoesPerfil();
        carregarDadosEstoque();
    }

    private void aplicarRestricoesPerfil() {
        boolean podeGerarRelatorio = PermissaoUtils.podeGerarRelatorio(this);
        int visibilidade = podeGerarRelatorio ? View.VISIBLE : View.GONE;
        binding.btnExportarRelatorioPdf.setVisibility(visibilidade);
        binding.btnExportarRelatorioPdf.setEnabled(podeGerarRelatorio);
        binding.containerRelatoriosSecundarios.setVisibility(visibilidade);
        binding.btnExportarDosesPdf.setEnabled(podeGerarRelatorio);
        binding.btnExportarGerencialPdf.setEnabled(podeGerarRelatorio);
    }

    private void configurarToolbar() {
        binding.toolbarEstoque.setNavigationOnClickListener(v -> finish());
    }

    private void configurarRecyclerView() {
        binding.rvEstoqueMedicamentos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EstoqueAdapter(this, todosMedicamentos, this::solicitarReposicaoWhatsApp);
        binding.rvEstoqueMedicamentos.setAdapter(adapter);
    }

    private void configurarBusca() {
        binding.etBuscaEstoque.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString() : "";
                if (query.trim().isEmpty()) {
                    binding.ivLimparBusca.setVisibility(View.GONE);
                } else {
                    binding.ivLimparBusca.setVisibility(View.VISIBLE);
                }
                adapter.filtrar(query);
                atualizarVisibilidadeLista();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.ivLimparBusca.setOnClickListener(v -> binding.etBuscaEstoque.setText(""));
    }

    private void configurarExportacaoPdf() {
        binding.btnExportarRelatorioPdf.setOnClickListener(v -> exportarRelatorioEstoquePdf());
        binding.btnExportarDosesPdf.setOnClickListener(v -> exportarRelatorioDosesPdf());
        binding.btnExportarGerencialPdf.setOnClickListener(v -> exportarRelatorioGerencialPdf());
    }

    private void exportarRelatorioEstoquePdf() {
        if (!PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (todosMedicamentos.isEmpty()) {
            Toast.makeText(this, "Nenhum medicamento disponível para emissão do relatório.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.gerando_pdf, Toast.LENGTH_SHORT).show();
        try {
            File arquivoPdf = PdfReportGenerator.gerarRelatorioEstoque(this, todosMedicamentos);
            notificarEAbrirPdfOffline(arquivoPdf, getString(R.string.titulo_relatorio_estoque_pdf));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar PDF de estoque", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void exportarRelatorioDosesPdf() {
        if (!PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.gerando_pdf, Toast.LENGTH_SHORT).show();
        try {
            List<DoseAlertaDTO> doses = dbHelper.buscarAlertasDoDia();
            File arquivoPdf = PdfReportGenerator.gerarRelatorioAdministracaoDoses(this, doses);
            notificarEAbrirPdfOffline(arquivoPdf, getString(R.string.titulo_relatorio_doses_pdf));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar PDF de doses do dia", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void exportarRelatorioGerencialPdf() {
        if (!PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.gerando_pdf, Toast.LENGTH_SHORT).show();
        try {
            List<Idoso> idosos = idosoDAO.listarTodos();
            List<DoseAlertaDTO> doses = dbHelper.buscarAlertasDoDia();
            List<PassagemTurno> passagens = passagemTurnoDAO.listarTodas();
            File arquivoPdf = PdfReportGenerator.gerarRelatorioGerencialIlpi(
                    this, idosos, todosMedicamentos, doses, passagens
            );
            notificarEAbrirPdfOffline(arquivoPdf, getString(R.string.titulo_relatorio_gerencial_pdf));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar PDF gerencial da ILPI", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void notificarEAbrirPdfOffline(File arquivoPdf, String tituloRelatorio) {
        Snackbar.make(binding.getRoot(), R.string.sucesso_pdf_gerado, Snackbar.LENGTH_LONG)
                .setAction(R.string.acao_consultar_offline, v -> abrirNoVisualizadorOffline(arquivoPdf, tituloRelatorio))
                .show();
        abrirNoVisualizadorOffline(arquivoPdf, tituloRelatorio);
    }

    private void abrirNoVisualizadorOffline(File arquivoPdf, String titulo) {
        Intent intent = new Intent(this, VisualizadorPdfOfflineActivity.class);
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_CAMINHO_ARQUIVO_PDF, arquivoPdf.getAbsolutePath());
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_NOME_RESIDENTE, titulo);
        startActivity(intent);
    }

    private void carregarDadosEstoque() {
        try {
            todosMedicamentos = medicamentoDAO.listarTodosMedicamentos();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar lista de estoque", e);
            todosMedicamentos = new ArrayList<>();
        }

        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(this);
        todosMedicamentos.sort(Comparator
                .comparingInt((Medicamento m) -> {
                    boolean critico = EstoqueAlertaManager.estaAbaixoDoLimiteMinimo(m, limiteMinimo);
                    com.example.util.ValidadeMedicamentoHelper.AvaliacaoValidade av =
                            com.example.util.ValidadeMedicamentoHelper.avaliarValidade(m.getDataValidade());
                    if (critico || av.getStatus() == com.example.util.ValidadeMedicamentoHelper.StatusValidade.VENCIDO) {
                        return 0;
                    }
                    if (av.getStatus() == com.example.util.ValidadeMedicamentoHelper.StatusValidade.VENCIMENTO_PROXIMO
                            || EstoqueAlertaManager.extrairQuantidadeNumerica(m.getEstoque()) <= limiteMinimo + 5) {
                        return 1;
                    }
                    return 2;
                })
                .thenComparingInt(m -> EstoqueAlertaManager.extrairQuantidadeNumerica(m.getEstoque()))
                .thenComparing(m -> m.getNomeMedicamento() != null ? m.getNomeMedicamento() : "", String.CASE_INSENSITIVE_ORDER));

        adapter.atualizarLista(todosMedicamentos);
        String filtroAtual = binding.etBuscaEstoque.getText() != null ? binding.etBuscaEstoque.getText().toString() : "";
        if (!filtroAtual.trim().isEmpty()) {
            adapter.filtrar(filtroAtual);
        }

        int total = todosMedicamentos.size();
        EstoqueAlertaManager.ResumoAlertaEstoque resumo =
                NotificationHelper.avaliarENotificarEstoqueBaixo(this, todosMedicamentos);
        int criticos = resumo.getQuantidadeAbaixoDoLimite();
        int alertasValidade = com.example.util.ValidadeMedicamentoHelper.contarAlertasValidade(todosMedicamentos);

        String totalTxt = total == 1 ? "1 medicamento registrado" : total + " medicamentos registrados";
        binding.tvResumoTotalItens.setText(totalTxt);

        String criticosTxt = criticos == 1
                ? "1 item em estoque crítico / reposição (< " + limiteMinimo + " un.)"
                : criticos + " itens em estoque crítico / reposição (< " + limiteMinimo + " un.)";
        if (alertasValidade > 0) {
            criticosTxt += getString(R.string.resumo_estoque_validade_sufixo, alertasValidade);
        }
        binding.tvResumoItensCriticos.setText(criticosTxt);

        atualizarVisibilidadeLista();
    }

    private void atualizarVisibilidadeLista() {
        if (adapter.getItemCount() == 0) {
            binding.layoutEstoqueVazio.setVisibility(View.VISIBLE);
            binding.rvEstoqueMedicamentos.setVisibility(View.GONE);
        } else {
            binding.layoutEstoqueVazio.setVisibility(View.GONE);
            binding.rvEstoqueMedicamentos.setVisibility(View.VISIBLE);
        }
    }

    private void solicitarReposicaoWhatsApp(Medicamento med) {
        if (med == null) return;

        Idoso idoso = null;
        if (med.getIdIdoso() > 0) {
            idoso = idosoDAO.buscarPorId(med.getIdIdoso());
        }

        if (idoso != null && idoso.getContatoEmergencia() != null && !idoso.getContatoEmergencia().trim().isEmpty()) {
            String mensagem = WhatsAppUtils.gerarMensagemReposicaoMedicamento(
                    idoso.getNome(),
                    med.getNomeMedicamento(),
                    med.getDosagem()
            );
            WhatsAppUtils.abrirConversaFamiliar(this, idoso.getContatoEmergencia(), mensagem);
        } else {
            Toast.makeText(this, "Nenhum familiar cadastrado para este residente.", Toast.LENGTH_LONG).show();
        }
    }
}
