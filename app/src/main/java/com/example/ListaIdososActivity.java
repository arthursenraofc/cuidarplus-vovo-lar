package com.example;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.IdosoAdapter;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.databinding.ActivityFichasIdososBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.util.ResumoProntuarioPdfGenerator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Activity que exibe a listagem e busca em tempo real (Pill Search MD3) de todos os idosos
 * cadastrados na ILPI Vovô Lar, com espaçamento seguro (paddingBottom="88dp") para o FAB
 * e geração rápida de Resumo do Prontuário em PDF para consulta offline.
 */
public class ListaIdososActivity extends AppCompatActivity implements IdosoAdapter.OnIdosoClickListener {

    private static final String TAG = "ListaIdososActivity";

    private ActivityFichasIdososBinding binding;
    private IdosoDAO idosoDAO;
    private MedicamentoDAO medicamentoDAO;
    private IdosoAdapter adapter;
    private String perfilUsuario = MainActivity.PERFIL_ADMIN;
    private final ExecutorService resumoPdfExecutor = Executors.newSingleThreadExecutor();
    volatile Future<?> ultimaTarefaResumoLista = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityFichasIdososBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarEdgeToEdgeInsets();

        idosoDAO = new IdosoDAO(this);
        medicamentoDAO = new MedicamentoDAO(this);

        // Recupera o perfil ativo da sessão ou da Intent
        perfilUsuario = com.example.util.PerfilManager.getPerfilAtual(this);
        if (getIntent().hasExtra(MainActivity.EXTRA_PERFIL)) {
            String extraPerfil = getIntent().getStringExtra(MainActivity.EXTRA_PERFIL);
            if (extraPerfil != null && !extraPerfil.trim().isEmpty()) {
                perfilUsuario = extraPerfil;
            }
        }

        configurarToolbar();
        configurarRecyclerView();
        configurarBusca();
        configurarBotoes();
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingBottomBase = binding.rvListaIdosos.getPaddingBottom();
        final int fabMarginBottomBase;
        ViewGroup.LayoutParams lp = binding.fabNovoIdoso.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            fabMarginBottomBase = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        } else {
            fabMarginBottomBase = 0;
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootFichasIdosos, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarLista.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            binding.rvListaIdosos.setPadding(
                    binding.rvListaIdosos.getPaddingLeft(),
                    binding.rvListaIdosos.getPaddingTop(),
                    binding.rvListaIdosos.getPaddingRight(),
                    paddingBottomBase + systemBars.bottom
            );
            ViewGroup.LayoutParams fabLp = binding.fabNovoIdoso.getLayoutParams();
            if (fabLp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) fabLp;
                mlp.bottomMargin = fabMarginBottomBase + systemBars.bottom;
                binding.fabNovoIdoso.setLayoutParams(mlp);
            }
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        perfilUsuario = com.example.util.PerfilManager.getPerfilAtual(this);
        configurarToolbar();
        // Recarrega a lista toda vez que a tela volta ao primeiro plano (após cadastro/edição/exclusão)
        carregarListaIdosos(binding.etCampoBusca.getText() != null ? binding.etCampoBusca.getText().toString() : "");
    }

    @Override
    protected void onDestroy() {
        if (ultimaTarefaResumoLista != null && !ultimaTarefaResumoLista.isDone()) {
            ultimaTarefaResumoLista.cancel(true);
        }
        resumoPdfExecutor.shutdown();
        super.onDestroy();
    }

    private void configurarToolbar() {
        binding.toolbarLista.setNavigationOnClickListener(v -> finish());

        // Atualiza a identificação do perfil na barra superior com tokens MD3 colorContainer / onColorContainer
        if (MainActivity.PERFIL_ADMIN.equals(perfilUsuario)) {
            binding.tvBadgePerfilAtual.setText(R.string.badge_perfil_admin_lista);
            binding.tvBadgePerfilAtual.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.admin_light))
            );
            binding.tvBadgePerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.on_primary_container));
            binding.fabNovoIdoso.setVisibility(View.VISIBLE);
        } else if (MainActivity.PERFIL_ENFERMEIRO.equals(perfilUsuario)) {
            binding.tvBadgePerfilAtual.setText(R.string.badge_perfil_enfermeiro_lista);
            binding.tvBadgePerfilAtual.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.enfermeiro_light))
            );
            binding.tvBadgePerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.md3_on_secondary_container));
            binding.fabNovoIdoso.setVisibility(View.GONE);
        } else {
            binding.tvBadgePerfilAtual.setText(R.string.badge_perfil_cuidador_lista);
            binding.tvBadgePerfilAtual.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.cuidador_light))
            );
            binding.tvBadgePerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.cuidador_color));
            // Cuidador não tem permissão para cadastrar novos residentes
            binding.fabNovoIdoso.setVisibility(View.GONE);
        }
    }

    private void configurarRecyclerView() {
        adapter = new IdosoAdapter(this);
        binding.rvListaIdosos.setLayoutManager(new LinearLayoutManager(this));
        binding.rvListaIdosos.setAdapter(adapter);
    }

    private void configurarBusca() {
        binding.etCampoBusca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String termo = s != null ? s.toString() : "";
                carregarListaIdosos(termo);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void configurarBotoes() {
        binding.btnGerarResumoSelecionado.setOnClickListener(v -> exibirDialogoSelecionarIdosoParaResumo());

        binding.fabNovoIdoso.setOnClickListener(v -> {
            if (MainActivity.PERFIL_ADMIN.equals(perfilUsuario)) {
                Intent intent = new Intent(ListaIdososActivity.this, CadastroIdosoActivity.class);
                intent.putExtra(MainActivity.EXTRA_PERFIL, perfilUsuario);
                startActivity(intent);
            } else {
                exibirAvisoPerfilRestrito();
            }
        });
    }

    private void exibirDialogoSelecionarIdosoParaResumo() {
        final List<Idoso> idosos = idosoDAO.buscarPorTermo("");
        if (idosos.isEmpty()) {
            Toast.makeText(this, R.string.dialog_selecionar_idoso_resumo_vazio, Toast.LENGTH_LONG).show();
            return;
        }

        CharSequence[] opcoes = new CharSequence[idosos.size()];
        String tagSalvoOffline = getString(R.string.item_idoso_resumo_salvo_tag);
        for (int i = 0; i < idosos.size(); i++) {
            Idoso item = idosos.get(i);
            StringBuilder rotulo = new StringBuilder(
                    item.getNome() != null ? item.getNome() : "Residente"
            );
            if (item.getLeitoQuarto() != null && !item.getLeitoQuarto().trim().isEmpty()) {
                rotulo.append(" (").append(item.getLeitoQuarto().trim()).append(")");
            }
            File resumoExistente = ResumoProntuarioPdfGenerator.obterResumoSalvoOffline(this, item);
            if (resumoExistente != null && resumoExistente.exists()) {
                rotulo.append(tagSalvoOffline);
            }
            opcoes[i] = rotulo.toString();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_selecionar_idoso_resumo_titulo)
                .setItems(opcoes, (dialog, which) -> {
                    if (which >= 0 && which < idosos.size()) {
                        gerarResumoPdfParaIdosoSelecionado(idosos.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void gerarResumoPdfParaIdosoSelecionado(Idoso idosoSelecionado) {
        if (idosoSelecionado == null) {
            return;
        }

        binding.btnGerarResumoSelecionado.setEnabled(false);
        Toast.makeText(this, R.string.gerando_resumo_pdf, Toast.LENGTH_SHORT).show();

        final int idResidente = idosoSelecionado.getId();
        ultimaTarefaResumoLista = resumoPdfExecutor.submit(() -> {
            try {
                Idoso idosoAtualizado = (idResidente > 0) ? idosoDAO.buscarPorId(idResidente) : idosoSelecionado;
                if (idosoAtualizado == null) {
                    idosoAtualizado = idosoSelecionado;
                }

                List<Medicamento> prescricoes = (idResidente > 0)
                        ? medicamentoDAO.listarPorIdoso(idResidente)
                        : Collections.emptyList();

                final File arquivoGerado = ResumoProntuarioPdfGenerator.gerarESalvarResumoOffline(
                        getApplicationContext(),
                        idosoAtualizado,
                        prescricoes
                );
                final String nomeResidente = idosoAtualizado.getNome();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnGerarResumoSelecionado.setEnabled(true);
                    Toast.makeText(
                            ListaIdososActivity.this,
                            R.string.sucesso_resumo_pdf_offline,
                            Toast.LENGTH_SHORT
                    ).show();

                    Snackbar.make(
                            binding.getRoot(),
                            R.string.sucesso_resumo_pdf_offline,
                            Snackbar.LENGTH_LONG
                    ).show();

                    Intent intentVisualizador = new Intent(ListaIdososActivity.this, VisualizadorPdfOfflineActivity.class);
                    intentVisualizador.putExtra(
                            VisualizadorPdfOfflineActivity.EXTRA_CAMINHO_ARQUIVO_PDF,
                            arquivoGerado.getAbsolutePath()
                    );
                    intentVisualizador.putExtra(
                            VisualizadorPdfOfflineActivity.EXTRA_NOME_RESIDENTE,
                            nomeResidente
                    );
                    startActivity(intentVisualizador);
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao gerar resumo PDF do residente selecionado", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnGerarResumoSelecionado.setEnabled(true);
                    Toast.makeText(
                            ListaIdososActivity.this,
                            R.string.erro_gerar_resumo_pdf,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void exibirAvisoPerfilRestrito() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_aviso_perfil_restrito, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_alternar_adm).setOnClickListener(v -> {
            dialog.dismiss();
            perfilUsuario = MainActivity.PERFIL_ADMIN;
            com.example.util.SessaoManager.getInstance(this)
                    .salvarPerfilAtivo(com.example.model.PerfilUsuario.ADMINISTRADOR);
            configurarToolbar();
            Intent intent = new Intent(ListaIdososActivity.this, CadastroIdosoActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilUsuario);
            startActivity(intent);
        });

        dialogView.findViewById(R.id.btn_cancelar_dialog).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void carregarListaIdosos(String termoBusca) {
        List<Idoso> idosos = idosoDAO.buscarPorTermo(termoBusca);
        adapter.setLista(idosos);

        int total = idosos.size();
        if (total == 1) {
            binding.tvTotalResidentes.setText(getString(R.string.total_residentes_singular, total));
        } else {
            binding.tvTotalResidentes.setText(getString(R.string.total_residentes_plural, total));
        }

        if (idosos.isEmpty()) {
            binding.layoutEstadoVazio.setVisibility(View.VISIBLE);
            binding.rvListaIdosos.setVisibility(View.GONE);
            if (termoBusca != null && !termoBusca.trim().isEmpty()) {
                binding.tvVazioTitulo.setText(getString(R.string.busca_sem_resultado, termoBusca));
                binding.tvVazioSubtitulo.setText(R.string.busca_sem_resultado_sub);
            } else {
                binding.tvVazioTitulo.setText(R.string.sem_idosos_titulo);
                binding.tvVazioSubtitulo.setText(R.string.sem_idosos_subtitulo);
            }
        } else {
            binding.layoutEstadoVazio.setVisibility(View.GONE);
            binding.rvListaIdosos.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onIdosoClick(Idoso idoso) {
        // Abre a tela de prontuário e detalhes clínicos do idoso selecionado
        Intent intent = new Intent(this, DetalhesIdosoActivity.class);
        intent.putExtra(DetalhesIdosoActivity.EXTRA_IDOSO_ID, idoso.getId());
        intent.putExtra(MainActivity.EXTRA_PERFIL, perfilUsuario);
        startActivity(intent);
    }
}
