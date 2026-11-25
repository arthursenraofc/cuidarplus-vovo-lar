package com.example;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.database.PassagemTurnoDAO;
import com.example.databinding.ActivityDashboardBinding;
import com.example.model.DoseAlertaDTO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;
import com.example.model.PerfilUsuario;
import com.example.util.DashboardManager;
import com.example.util.EstoqueAlertaManager;
import com.example.util.ImageUtil;
import com.example.util.NotificationHelper;
import com.example.util.PdfReportGenerator;
import com.example.util.PerfilManager;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.Calendar;
import java.util.List;

/**
 * Tela dedicada de Dashboard da Instituição (Material Design 3).
 * Reúne os cards de resumo visual e operacional da ILPI Vovô Lar:
 * 1. Total de Residentes acolhidos e ocupação de vagas;
 * 2. Alerta de Aniversariantes do Mês;
 * 3. KPIs do Plantão (Doses Atrasadas, Estoque Crítico e Passagem de Turno);
 * 4. Panorama Clínico e Relatório Gerencial Consolidado em PDF offline.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    private ActivityDashboardBinding binding;
    private IdosoDAO idosoDAO;
    private MedicamentoDAO medicamentoDAO;
    private PassagemTurnoDAO passagemTurnoDAO;
    private DatabaseHelper dbHelper;
    private String perfilAtual = MainActivity.PERFIL_ADMIN;
    private boolean sincronizadoNaCriacao = false;

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Log.w(TAG, "Permissão POST_NOTIFICATIONS negada pelo usuário.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarEdgeToEdgeInsets();

        idosoDAO = new IdosoDAO(this);
        medicamentoDAO = new MedicamentoDAO(this);
        passagemTurnoDAO = new PassagemTurnoDAO(this);
        dbHelper = DatabaseHelper.getInstance(this);
        perfilAtual = PerfilManager.getPerfilAtual(this);

        configurarInteracoes();
        NotificationHelper.criarCanalNotificacao(this);
        atualizarResumoDashboard();
        sincronizadoNaCriacao = true;

        binding.rootTelaDashboard.post(this::solicitarPermissaoNotificacoesSeNecessario);
    }

    private void solicitarPermissaoNotificacoesSeNecessario() {
        if (!isTaskRoot() || isFinishing() || isDestroyed()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sincronizadoNaCriacao) {
            sincronizadoNaCriacao = false;
            return;
        }
        perfilAtual = PerfilManager.getPerfilAtual(this);
        atualizarResumoDashboard();
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingBottomInicial = binding.containerCardsDashboard.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootTelaDashboard, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarDashboard.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );
            binding.containerCardsDashboard.setPadding(
                    binding.containerCardsDashboard.getPaddingLeft(),
                    binding.containerCardsDashboard.getPaddingTop(),
                    binding.containerCardsDashboard.getPaddingRight(),
                    paddingBottomInicial + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void configurarInteracoes() {
        if (isTaskRoot()) {
            binding.toolbarDashboard.setNavigationIcon(null);
        } else {
            binding.toolbarDashboard.setNavigationOnClickListener(v -> finish());
        }

        binding.cardResumoTotalResidentes.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ListaIdososActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        binding.btnAjustarCapacidade.setOnClickListener(v -> {
            if (PermissaoUtils.podeAlterarConfiguracoes(this)) {
                abrirDialogAjustarCapacidade();
            } else {
                exibirAvisoPerfilRestritoCapacidade();
            }
        });

        binding.cardKpiDosesAtrasadas.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, MedicamentoActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        binding.cardKpiEstoqueCritico.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, EstoqueActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        binding.cardKpiPassagemTurno.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, PassagemTurnoActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        binding.btnDashboardRelatorioGerencialPdf.setOnClickListener(v -> gerarRelatorioGerencialPdf());

        binding.btnAbrirModulosOperacao.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });
    }

    /**
     * Atualiza todos os cards de resumo visual do Dashboard utilizando a data atual do dispositivo.
     */
    public void atualizarResumoDashboard() {
        atualizarResumoDashboard(Calendar.getInstance());
    }

    /**
     * Atualiza os cards de resumo visual do Dashboard usando uma data de referência explícita,
     * permitindo validação determinística em testes automatizados para qualquer mês do ano.
     */
    public void atualizarResumoDashboard(Calendar dataReferencia) {
        Calendar referencia = dataReferencia != null ? dataReferencia : Calendar.getInstance();
        List<Idoso> todosIdosos = idosoDAO.listarTodos();
        int totalResidentes = todosIdosos.size();

        // 1. Card de Total de Residentes
        if (totalResidentes == 1) {
            binding.tvSubtituloTotalResidentes.setText(
                    getString(R.string.card_total_residentes_sub_singular, totalResidentes)
            );
        } else if (totalResidentes == 0) {
            binding.tvSubtituloTotalResidentes.setText(R.string.card_total_residentes_sub_zero);
        } else {
            binding.tvSubtituloTotalResidentes.setText(
                    getString(R.string.card_total_residentes_sub_plural, totalResidentes)
            );
        }
        binding.tvValorTotalResidentes.setText(String.valueOf(totalResidentes));

        // 2. Card de Ocupação de Vagas (Ocupadas / Disponíveis)
        int capacidadeTotal = DashboardManager.getCapacidadeTotalVagas(this);
        DashboardManager.ResumoVagas resumoVagas =
                DashboardManager.calcularResumoVagas(totalResidentes, capacidadeTotal);

        binding.tvCapacidadeTotalVagas.setText(
                getString(R.string.card_vagas_capacidade_formato, resumoVagas.getCapacidadeTotal())
        );
        binding.tvVagasOcupadasValor.setText(String.valueOf(resumoVagas.getVagasOcupadas()));
        binding.tvVagasDisponiveisValor.setText(String.valueOf(resumoVagas.getVagasDisponiveis()));
        binding.tvPercentualOcupacao.setText(
                getString(R.string.card_vagas_percentual_formato, resumoVagas.getPercentualOcupacao())
        );
        binding.progressOcupacaoVagas.setProgress(resumoVagas.getPercentualOcupacao());

        atualizarEstiloIndicadoresVagas(resumoVagas);

        PerfilUsuario perfilObj = SessaoManager.getPerfil(this);
        boolean podeAlterarCapacidade = PermissaoUtils.podeAlterarConfiguracoes(perfilObj);
        binding.btnAjustarCapacidade.setAlpha(podeAlterarCapacidade ? 1.0f : 0.55f);

        // 3. Card de Alerta de Aniversariantes do Mês
        int mesAtual1a12 = referencia.get(Calendar.MONTH) + 1;
        String nomeMes = DashboardManager.obterNomeMesPortugues(mesAtual1a12);
        List<DashboardManager.AniversarianteMes> aniversariantes =
                DashboardManager.filtrarAniversariantesDoMes(todosIdosos, referencia);

        preencherCardAniversariantes(nomeMes, aniversariantes);

        // 4. KPIs Operacionais e Panorama Clínico (já dispara notificação com a mesma lista carregada)
        atualizarKpisOperacionaisEClinicos(todosIdosos);
    }

    private void atualizarKpisOperacionaisEClinicos(List<Idoso> todosIdosos) {
        List<DoseAlertaDTO> doses = dbHelper.buscarAlertasDoDia();
        int dosesAtrasadas = 0;
        int dosesPendentes = 0;
        for (DoseAlertaDTO d : doses) {
            if ("ATRASADO".equalsIgnoreCase(d.getStatusPontualidade())) {
                dosesAtrasadas++;
                dosesPendentes++;
            } else if (!d.isAdministradoHoje() && !"CONCLUIDO".equalsIgnoreCase(d.getStatusPontualidade())) {
                dosesPendentes++;
            }
        }
        binding.tvKpiDosesAtrasadasValor.setText(String.valueOf(dosesAtrasadas));
        if (dosesAtrasadas > 0) {
            binding.tvKpiDosesAtrasadasValor.setTextColor(ContextCompat.getColor(this, R.color.danger_red));
            binding.tvKpiDosesAtrasadasStatus.setText(getString(R.string.kpi_doses_atrasadas_status, dosesPendentes));
            binding.cardKpiDosesAtrasadas.setStrokeColor(ContextCompat.getColor(this, R.color.danger_red));
        } else {
            binding.tvKpiDosesAtrasadasValor.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            binding.tvKpiDosesAtrasadasStatus.setText(
                    dosesPendentes > 0
                            ? getString(R.string.kpi_doses_atrasadas_status, dosesPendentes)
                            : getString(R.string.kpi_doses_em_dia)
            );
            binding.cardKpiDosesAtrasadas.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        }

        List<Medicamento> todosMeds = medicamentoDAO.listarTodosMedicamentos();
        NotificationHelper.avaliarENotificarEstoqueBaixo(this, todosMeds);
        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(this);
        EstoqueAlertaManager.ResumoAlertaEstoque resumoEstoque =
                EstoqueAlertaManager.avaliarMedicamentos(todosMeds, limiteMinimo);
        int criticosEstoque = resumoEstoque.getQuantidadeAbaixoDoLimite();
        binding.tvKpiEstoqueCriticoValor.setText(String.valueOf(criticosEstoque));
        if (criticosEstoque > 0) {
            binding.tvKpiEstoqueCriticoValor.setTextColor(ContextCompat.getColor(this, R.color.danger_red));
            binding.tvKpiEstoqueCriticoStatus.setText(getString(R.string.kpi_estoque_critico_status, limiteMinimo));
            binding.cardKpiEstoqueCritico.setStrokeColor(ContextCompat.getColor(this, R.color.danger_red));
        } else {
            binding.tvKpiEstoqueCriticoValor.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            binding.tvKpiEstoqueCriticoStatus.setText(R.string.kpi_estoque_em_dia);
            binding.cardKpiEstoqueCritico.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        }

        int pendentesTurno = passagemTurnoDAO.contarPendentesConferencia();
        int urgentesTurno = passagemTurnoDAO.contarUrgentesPendentes();
        binding.tvKpiPassagemTurnoValor.setText(String.valueOf(pendentesTurno));
        if (urgentesTurno > 0) {
            binding.tvKpiPassagemTurnoStatus.setText(getString(R.string.kpi_passagem_pendente_status, urgentesTurno));
            binding.tvKpiPassagemTurnoStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red));
            binding.cardKpiPassagemTurno.setStrokeColor(ContextCompat.getColor(this, R.color.danger_red));
        } else if (pendentesTurno > 0) {
            binding.tvKpiPassagemTurnoStatus.setText(
                    pendentesTurno == 1
                            ? getString(R.string.badge_passagem_pendente_singular, pendentesTurno)
                            : getString(R.string.badge_passagem_pendente_plural, pendentesTurno)
            );
            binding.tvKpiPassagemTurnoStatus.setTextColor(ContextCompat.getColor(this, R.color.vagas_alerta_fg));
            binding.cardKpiPassagemTurno.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        } else {
            binding.tvKpiPassagemTurnoStatus.setText(R.string.kpi_passagem_conferida);
            binding.tvKpiPassagemTurnoStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            binding.cardKpiPassagemTurno.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        }

        int somaIdades = 0;
        int validas = 0;
        int comAlergias = 0;
        for (Idoso i : todosIdosos) {
            int idade = i.calcularIdade();
            if (idade > 0) {
                somaIdades += idade;
                validas++;
            }
            if (i.getAlergias() != null && !i.getAlergias().trim().isEmpty()
                    && !i.getAlergias().toLowerCase().contains("nenhuma")) {
                comAlergias++;
            }
        }
        int mediaIdade = validas > 0 ? Math.round((float) somaIdades / validas) : 0;
        binding.tvClinicoMediaIdade.setText(mediaIdade > 0 ? (mediaIdade + " a") : "-");
        binding.tvClinicoAlergias.setText(String.valueOf(comAlergias));
        binding.tvClinicoPrescricoes.setText(String.valueOf(doses.size()));

        boolean podePdf = PermissaoUtils.podeGerarRelatorio(this);
        binding.btnDashboardRelatorioGerencialPdf.setVisibility(podePdf ? View.VISIBLE : View.GONE);
    }

    private void gerarRelatorioGerencialPdf() {
        if (!PermissaoUtils.podeGerarRelatorio(this)) {
            Toast.makeText(this, "Seu perfil não possui permissão para exportar relatórios PDF.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            List<Idoso> idosos = idosoDAO.listarTodos();
            List<Medicamento> meds = medicamentoDAO.listarTodosMedicamentos();
            List<DoseAlertaDTO> doses = dbHelper.buscarAlertasDoDia();
            List<PassagemTurno> passagens = passagemTurnoDAO.listarTodas();
            File pdf = PdfReportGenerator.gerarRelatorioGerencialIlpi(this, idosos, meds, doses, passagens);

            Snackbar.make(binding.getRoot(), R.string.sucesso_pdf_gerado, Snackbar.LENGTH_LONG)
                    .setAction(R.string.acao_consultar_offline, v -> abrirPdfOffline(pdf))
                    .show();
            abrirPdfOffline(pdf);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar relatório gerencial em PDF", e);
            Toast.makeText(this, R.string.erro_gerar_pdf, Toast.LENGTH_LONG).show();
        }
    }

    private void abrirPdfOffline(File pdf) {
        Intent intent = new Intent(this, VisualizadorPdfOfflineActivity.class);
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_CAMINHO_ARQUIVO_PDF, pdf.getAbsolutePath());
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_NOME_RESIDENTE, getString(R.string.titulo_relatorio_gerencial_pdf));
        startActivity(intent);
    }

    private void atualizarEstiloIndicadoresVagas(DashboardManager.ResumoVagas resumoVagas) {
        if (resumoVagas.isLotacaoCompleta()) {
            int corPerigo = ContextCompat.getColor(this, R.color.danger_red);
            int corFundoPerigo = ContextCompat.getColor(this, R.color.danger_light);
            int corTextoPerigo = ContextCompat.getColor(this, R.color.danger_text);

            binding.progressOcupacaoVagas.setIndicatorColor(corPerigo);
            binding.cardVagasDisponiveis.setCardBackgroundColor(corFundoPerigo);
            binding.tvVagasDisponiveisRotulo.setTextColor(corTextoPerigo);
            binding.tvVagasDisponiveisValor.setTextColor(corPerigo);
            binding.tvVagasDisponiveisLegenda.setTextColor(corTextoPerigo);
            binding.tvVagasDisponiveisLegenda.setText(R.string.card_vagas_lotacao_maxima);

            binding.tvStatusLotacao.setBackgroundTintList(ColorStateList.valueOf(corFundoPerigo));
            binding.tvStatusLotacao.setTextColor(corTextoPerigo);
            if (resumoVagas.getVagasExcedentes() > 0) {
                binding.tvStatusLotacao.setText(
                        getString(R.string.card_vagas_acima_capacidade, resumoVagas.getVagasExcedentes())
                );
            } else {
                binding.tvStatusLotacao.setText(R.string.card_vagas_lotacao_maxima);
            }
        } else if (resumoVagas.getPercentualOcupacao() >= 85) {
            int corAlerta = ContextCompat.getColor(this, R.color.vagas_alerta_fg);
            int corFundoAlerta = ContextCompat.getColor(this, R.color.vagas_alerta_bg);
            int corVerde = ContextCompat.getColor(this, R.color.vagas_disponiveis_fg);
            int corFundoVerde = ContextCompat.getColor(this, R.color.vagas_disponiveis_bg);

            binding.progressOcupacaoVagas.setIndicatorColor(corAlerta);
            binding.cardVagasDisponiveis.setCardBackgroundColor(corFundoVerde);
            binding.tvVagasDisponiveisRotulo.setTextColor(corVerde);
            binding.tvVagasDisponiveisValor.setTextColor(corVerde);
            binding.tvVagasDisponiveisLegenda.setTextColor(corVerde);
            binding.tvVagasDisponiveisLegenda.setText(R.string.card_vagas_leitos_livres);

            binding.tvStatusLotacao.setBackgroundTintList(ColorStateList.valueOf(corFundoAlerta));
            binding.tvStatusLotacao.setTextColor(corAlerta);
            binding.tvStatusLotacao.setText(
                    getString(R.string.card_vagas_disponiveis_rotulo) + ": " + resumoVagas.getVagasDisponiveis()
            );
        } else {
            int corPrimaria = ContextCompat.getColor(this, R.color.primary);
            int corVerde = ContextCompat.getColor(this, R.color.vagas_disponiveis_fg);
            int corFundoVerde = ContextCompat.getColor(this, R.color.vagas_disponiveis_bg);

            binding.progressOcupacaoVagas.setIndicatorColor(corPrimaria);
            binding.cardVagasDisponiveis.setCardBackgroundColor(corFundoVerde);
            binding.tvVagasDisponiveisRotulo.setTextColor(corVerde);
            binding.tvVagasDisponiveisValor.setTextColor(corVerde);
            binding.tvVagasDisponiveisLegenda.setTextColor(corVerde);
            binding.tvVagasDisponiveisLegenda.setText(R.string.card_vagas_leitos_livres);

            binding.tvStatusLotacao.setBackgroundTintList(ColorStateList.valueOf(corFundoVerde));
            binding.tvStatusLotacao.setTextColor(corVerde);
            binding.tvStatusLotacao.setText(
                    resumoVagas.getVagasDisponiveis() + " " + getString(R.string.card_vagas_leitos_livres)
            );
        }
    }

    private void preencherCardAniversariantes(
            String nomeMes,
            List<DashboardManager.AniversarianteMes> aniversariantes
    ) {
        binding.tvTituloAniversariantesMes.setText(
                getString(R.string.card_aniversariantes_titulo_formato, nomeMes)
        );
        binding.containerListaAniversariantes.removeAllViews();

        int qtd = aniversariantes.size();
        if (qtd == 0) {
            binding.cardAlertaAniversariantes.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.surface)
            );
            binding.cardAlertaAniversariantes.setStrokeColor(
                    ContextCompat.getColor(this, R.color.m3_outline_variant)
            );
            binding.tvSubtituloAniversariantesMes.setText(
                    getString(R.string.card_aniversariantes_vazio_titulo, nomeMes)
            );
            binding.tvSubtituloAniversariantesMes.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary)
            );
            binding.tvBadgeQtdAniversariantes.setText(R.string.card_aniversariantes_badge_zero);
            binding.tvBadgeQtdAniversariantes.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.m3_surface_container_high))
            );
            binding.tvBadgeQtdAniversariantes.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary)
            );
            binding.tvSemAniversariantesTitulo.setText(
                    getString(R.string.card_aniversariantes_vazio_titulo, nomeMes)
            );
            binding.containerListaAniversariantes.setVisibility(View.GONE);
            binding.layoutSemAniversariantes.setVisibility(View.VISIBLE);
            return;
        }

        binding.cardAlertaAniversariantes.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.aniversario_card_bg)
        );
        binding.cardAlertaAniversariantes.setStrokeColor(
                ContextCompat.getColor(this, R.color.aniversario_card_stroke)
        );
        binding.tvSubtituloAniversariantesMes.setTextColor(
                ContextCompat.getColor(this, R.color.aniversario_accent)
        );
        binding.tvBadgeQtdAniversariantes.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.aniversario_badge_bg))
        );
        binding.tvBadgeQtdAniversariantes.setTextColor(
                ContextCompat.getColor(this, R.color.aniversario_badge_fg)
        );

        if (qtd == 1) {
            binding.tvSubtituloAniversariantesMes.setText(
                    getString(R.string.card_aniversariantes_alerta_singular, qtd)
            );
            binding.tvBadgeQtdAniversariantes.setText(
                    getString(R.string.card_aniversariantes_badge_singular, qtd)
            );
        } else {
            binding.tvSubtituloAniversariantesMes.setText(
                    getString(R.string.card_aniversariantes_alerta_plural, qtd)
            );
            binding.tvBadgeQtdAniversariantes.setText(
                    getString(R.string.card_aniversariantes_badge_plural, qtd)
            );
        }

        binding.layoutSemAniversariantes.setVisibility(View.GONE);
        binding.containerListaAniversariantes.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (DashboardManager.AniversarianteMes item : aniversariantes) {
            View itemView = inflater.inflate(
                    R.layout.item_aniversariante_mes,
                    binding.containerListaAniversariantes,
                    false
            );

            Idoso idoso = item.getIdoso();
            ShapeableImageView ivFoto = itemView.findViewById(R.id.iv_foto_aniversariante);
            TextView tvNome = itemView.findViewById(R.id.tv_nome_aniversariante);
            TextView tvDetalhe = itemView.findViewById(R.id.tv_detalhe_aniversariante);
            TextView tvBadgeHoje = itemView.findViewById(R.id.tv_badge_hoje_aniversariante);
            TextView tvData = itemView.findViewById(R.id.tv_data_aniversariante);

            ImageUtil.carregarFoto(ivFoto, idoso.getCaminhoFoto());
            tvNome.setText(idoso.getNome());

            String leito = idoso.getLeitoQuarto() != null ? idoso.getLeitoQuarto().trim() : "";
            if (!leito.isEmpty()) {
                tvDetalhe.setText(getString(
                        R.string.item_aniversariante_leito_formato,
                        item.getIdadeNoAno(),
                        leito
                ));
            } else {
                tvDetalhe.setText(getString(
                        R.string.item_aniversariante_idade_formato,
                        item.getIdadeNoAno()
                ));
            }

            tvData.setText(getString(
                    R.string.item_aniversariante_dia_formato,
                    item.getDiaNascimento(),
                    item.getMesNascimento()
            ));
            tvBadgeHoje.setVisibility(item.isAniversarioHoje() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, DetalhesIdosoActivity.class);
                intent.putExtra("EXTRA_IDOSO_ID", idoso.getId());
                intent.putExtra(MainActivity.EXTRA_PERFIL, perfilAtual);
                startActivity(intent);
            });

            binding.containerListaAniversariantes.addView(itemView);
        }
    }

    private void abrirDialogAjustarCapacidade() {
        int capacidadeAtual = DashboardManager.getCapacidadeTotalVagas(this);

        int paddingHorizontal = Math.round(24 * getResources().getDisplayMetrics().density);
        int paddingTop = Math.round(12 * getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0);

        TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint(getString(R.string.hint_capacidade_vagas));
        textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setId(android.R.id.edit);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setText(String.valueOf(capacidadeAtual));
        if (editText.getText() != null) {
            editText.setSelection(editText.getText().length());
        }

        textInputLayout.addView(editText);
        container.addView(textInputLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_capacidade_titulo)
                .setMessage(R.string.dialog_capacidade_mensagem)
                .setView(container)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.btn_salvar_alteracoes, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String valorDigitado = editText.getText() != null ? editText.getText().toString().trim() : "";
            try {
                int novaCapacidade = Integer.parseInt(valorDigitado);
                if (novaCapacidade < 1) {
                    textInputLayout.setError(getString(R.string.erro_capacidade_invalida));
                    return;
                }
                DashboardManager.salvarCapacidadeTotalVagas(DashboardActivity.this, novaCapacidade);
                atualizarResumoDashboard();
                Toast.makeText(
                        DashboardActivity.this,
                        getString(R.string.sucesso_capacidade_atualizada, novaCapacidade),
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
            } catch (NumberFormatException ex) {
                textInputLayout.setError(getString(R.string.erro_capacidade_invalida));
            }
        }));

        dialog.show();
    }

    private void exibirAvisoPerfilRestritoCapacidade() {
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
            perfilAtual = MainActivity.PERFIL_ADMIN;
            SessaoManager.getInstance(this).salvarPerfilAtivo(PerfilUsuario.ADMINISTRADOR);
            PerfilManager.setPerfil(this, MainActivity.PERFIL_ADMIN);
            atualizarResumoDashboard();
            abrirDialogAjustarCapacidade();
        });

        dialogView.findViewById(R.id.btn_cancelar_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
