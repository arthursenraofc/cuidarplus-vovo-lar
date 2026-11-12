package com.example;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.EstoqueAdapter;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.databinding.ActivityEstoqueBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.util.PdfReportGenerator;
import com.example.util.WhatsAppUtils;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity para Controle do Estoque Geral de Medicamentos e Exportação de Relatórios PDF 100% Offline.
 * Permite a visualização unificada de todo o estoque da ILPI Vovô Lar, emissão de PDF com tabelas
 * formatadas e acionamento rápido de mensagens via WhatsApp para reposição de doses críticas.
 */
public class EstoqueActivity extends AppCompatActivity {

    private static final String TAG = "EstoqueActivity";

    private ActivityEstoqueBinding binding;
    private MedicamentoDAO medicamentoDAO;
    private IdosoDAO idosoDAO;
    private EstoqueAdapter adapter;
    private List<Medicamento> todosMedicamentos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEstoqueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        medicamentoDAO = new MedicamentoDAO(this);
        idosoDAO = new IdosoDAO(this);

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
        boolean podeGerarRelatorio = com.example.util.PermissaoUtils.podeGerarRelatorio(this);
        binding.btnExportarRelatorioPdf.setVisibility(podeGerarRelatorio ? View.VISIBLE : View.GONE);
        binding.btnExportarRelatorioPdf.setEnabled(podeGerarRelatorio);
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
        // Exemplo e implementação completa do clique no botão "Exportar Relatório em PDF"
        binding.btnExportarRelatorioPdf.setOnClickListener(v -> exportarRelatorioEstoquePdf());
    }

    /**
     * Executa a geração do Relatório Geral de Estoque em PDF 100% Offline
     * e o abre ou compartilha via FileProvider.
     */
    private void exportarRelatorioEstoquePdf() {
        if (!com.example.util.PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (todosMedicamentos.isEmpty()) {
            Toast.makeText(this, "Nenhum medicamento disponível para emissão do relatório.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.gerando_pdf, Toast.LENGTH_SHORT).show();

        try {
            // Gera o documento nativo usando PdfDocument, Canvas e Paint
            File arquivoPdf = PdfReportGenerator.gerarRelatorioEstoque(this, todosMedicamentos);

            // Feedback visual com Snackbar interativo em Material Design 3
            Snackbar.make(
                    binding.getRoot(),
                    R.string.sucesso_pdf_gerado,
                    Snackbar.LENGTH_LONG
            ).setAction(R.string.btn_exportar_pdf_resumo, view -> {
                PdfReportGenerator.abrirOuCompartilharPdf(EstoqueActivity.this, arquivoPdf);
            }).show();

            // Abre o PDF imediatamente no leitor de documentos do sistema ou compartilhador
            PdfReportGenerator.abrirOuCompartilharPdf(this, arquivoPdf);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar PDF de estoque", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void carregarDadosEstoque() {
        try {
            todosMedicamentos = medicamentoDAO.listarTodosMedicamentos();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar lista de estoque", e);
            todosMedicamentos = new ArrayList<>();
        }
        adapter.atualizarLista(todosMedicamentos);

        int total = todosMedicamentos.size();
        int limiteMinimo = com.example.util.EstoqueAlertaManager.getLimiteMinimoEstoque(this);
        int criticos = com.example.util.EstoqueAlertaManager
                .avaliarMedicamentos(todosMedicamentos, limiteMinimo)
                .getQuantidadeAbaixoDoLimite();

        String totalTxt = total == 1 ? "1 medicamento registrado" : total + " medicamentos registrados";
        binding.tvResumoTotalItens.setText(totalTxt);

        String criticosTxt = criticos == 1 ? "1 item em estoque crítico / reposição" : criticos + " itens em estoque crítico / reposição";
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
