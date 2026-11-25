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
import com.example.databinding.ActivityMainBinding;
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
import com.example.util.ValidadeMedicamentoHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.Calendar;
import java.util.List;

/**
 * Tela Inicial Unificada do CuidarPlus (Material Design 3).
 * Renderiza imediatamente na inicialização o Dashboard & Indicadores da ILPI Vovô Lar
 * (residentes, ocupação de vagas, aniversariantes, KPIs do plantão e panorama clínico)
 * junto com todos os Módulos e Operação Diária, sem depender de carregamento assíncrono.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String EXTRA_PERFIL = "EXTRA_PERFIL";
    public static final String PERFIL_ADMIN = PerfilManager.PERFIL_ADMIN;
    public static final String PERFIL_ENFERMEIRO = PerfilManager.PERFIL_ENFERMEIRO;
    public static final String PERFIL_CUIDADOR = PerfilManager.PERFIL_CUIDADOR;

    private ActivityMainBinding binding;
    private DatabaseHelper dbHelper;
    private IdosoDAO idosoDAO;
    private MedicamentoDAO medicamentoDAO;
    private PassagemTurnoDAO passagemTurnoDAO;
    private String perfilAtual = PERFIL_ADMIN;
    private boolean sincronizadoNaCriacao = false;

    /**
     * Launcher para solicitar a permissão em tempo de execução POST_NOTIFICATIONS (Android 13+ / API 33+).
     */
    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "Permissão POST_NOTIFICATIONS concedida com sucesso.");
                } else {
                    Log.w(TAG, "Permissão POST_NOTIFICATIONS negada pelo usuário.");
                    Toast.makeText(
                            this,
                            "Permissão de notificações é essencial para alertas pontuais de remédios.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Vinculação explícita ao layout unificado com Dashboard + Módulos e Operação Diária
        setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.bind(findViewById(R.id.root_dashboard));

        configurarEdgeToEdgeInsets();

        dbHelper = DatabaseHelper.getInstance(this);
        idosoDAO = new IdosoDAO(this);
        medicamentoDAO = new MedicamentoDAO(this);
        passagemTurnoDAO = new PassagemTurnoDAO(this);
        perfilAtual = PerfilManager.getPerfilAtual(this);

        // Renderização imediata e síncrona de todo o Dashboard e módulos antes do primeiro frame na tela
        configurarMenuPrincipal();
        NotificationHelper.criarCanalNotificacao(this);
        atualizarResumoMenuPrincipal();
        sincronizadoNaCriacao = true;

        binding.rootDashboard.post(this::solicitarPermissaoNotificacoesSeNecessario);
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingBottomInicial = binding.containerConteudoDashboard.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDashboard, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarMain.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );
            binding.containerConteudoDashboard.setPadding(
                    binding.containerConteudoDashboard.getPaddingLeft(),
                    binding.containerConteudoDashboard.getPaddingTop(),
                    binding.containerConteudoDashboard.getPaddingRight(),
                    paddingBottomInicial + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void solicitarPermissaoNotificacoesSeNecessario() {
        if (isFinishing() || isDestroyed()) {
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        perfilAtual = PerfilManager.getPerfilAtual(this);
        atualizarResumoMenuPrincipal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sincronizadoNaCriacao) {
            sincronizadoNaCriacao = false;
            return;
        }
        perfilAtual = PerfilManager.getPerfilAtual(this);
        atualizarResumoMenuPrincipal();
    }

    private void configurarMenuPrincipal() {
        atualizarVisualPerfil();

        // Interações diretas dos cards do Dashboard Integrado
        binding.cardResumoTotalResidentes.setOnClickListener(v -> abrirListaIdosos());

        binding.btnAjustarCapacidade.setOnClickListener(v -> {
            if (PermissaoUtils.podeAlterarConfiguracoes(this)) {
                abrirDialogAjustarCapacidade();
            } else {
                exibirAvisoPerfilRestritoCapacidade();
            }
        });

        binding.cardKpiDosesAtrasadas.setOnClickListener(v -> abrirMedicamentos());
        binding.cardKpiEstoqueCritico.setOnClickListener(v -> abrirEstoque());
        binding.cardKpiPassagemTurno.setOnClickListener(v -> abrirPassagemTurno());
        binding.btnDashboardRelatorioGerencialPdf.setOnClickListener(v -> gerarRelatorioGerencialPdf());

        // 1. Ver Fichas dos Idosos
        binding.cardMenuFichas.setOnClickListener(v -> abrirListaIdosos());

        // 2. Controle de Medicamentos & Alarmes
        binding.cardMenuMedicamentos.setOnClickListener(v -> abrirMedicamentos());

        // 2b. Passagem de Turno & Observações
        binding.cardMenuPassagemTurno.setOnClickListener(v -> abrirPassagemTurno());

        // 3. Estoque Geral de Medicamentos & Relatórios PDF
        binding.cardMenuEstoque.setOnClickListener(v -> abrirEstoque());

        // 4. Cadastrar Novo Idoso (restrito ao Administrador)
        binding.cardMenuCadastrar.setOnClickListener(v -> {
            if (PermissaoUtils.podeCadastrarIdoso(this)) {
                Intent intent = new Intent(MainActivity.this, CadastroIdosoActivity.class);
                intent.putExtra(EXTRA_PERFIL, perfilAtual);
                startActivity(intent);
            } else {
                exibirAvisoPerfilRestrito();
            }
        });

        // 5. Opção dedicada: Dashboard & Indicadores da Instituição
        binding.cardMenuDashboard.setOnClickListener(v -> abrirDashboardInstituicao());

        // 6. Seleção de Perfil (ADM / Enfermeiro / Cuidador)
        binding.cardMenuPerfil.setOnClickListener(v -> abrirDialogSelecaoPerfil());
        binding.cardStatusTopo.setOnClickListener(v -> abrirDialogSelecaoPerfil());
    }

    private void abrirListaIdosos() {
        Intent intent = new Intent(MainActivity.this, ListaIdososActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    private void abrirMedicamentos() {
        Intent intent = new Intent(MainActivity.this, MedicamentoActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    private void abrirPassagemTurno() {
        Intent intent = new Intent(MainActivity.this, PassagemTurnoActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    private void abrirEstoque() {
        Intent intent = new Intent(MainActivity.this, EstoqueActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    private void abrirDashboardInstituicao() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    /**
     * Atualiza de forma síncrona todos os cards do Dashboard integrado, os contadores
     * do cabeçalho e os badges dos módulos de operação diária.
     */
    public void atualizarResumoMenuPrincipal() {
        atualizarResumoMenuPrincipal(Calendar.getInstance());
    }

    /**
     * Atualiza de forma síncrona todos os indicadores do Dashboard e do menu usando
     * uma data de referência explícita.
     */
    public void atualizarResumoMenuPrincipal(Calendar dataReferencia) {
        Calendar referencia = dataReferencia != null ? dataReferencia : Calendar.getInstance();
        List<Idoso> todosIdosos = idosoDAO.listarTodos();
        int totalResidentes = todosIdosos.size();

        // Cabeçalho rápido
        if (totalResidentes == 1) {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_singular, totalResidentes));
        } else {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_plural, totalResidentes));
        }

        // Card 1 do Dashboard: Total de Residentes
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

        // Card 2 do Dashboard: Vagas Ocupadas / Disponíveis
        int capacidade = DashboardManager.getCapacidadeTotalVagas(this);
        DashboardManager.ResumoVagas resumoVagas =
                DashboardManager.calcularResumoVagas(totalResidentes, capacidade);
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

        // Card 3 do Dashboard: Aniversariantes do Mês
        int mesAtual1a12 = referencia.get(Calendar.MONTH) + 1;
        String nomeMes = DashboardManager.obterNomeMesPortugues(mesAtual1a12);
        List<DashboardManager.AniversarianteMes> aniversariantes =
                DashboardManager.filtrarAniversariantesDoMes(todosIdosos, referencia);
        int qtdAniversariantes = aniversariantes.size();
        preencherCardAniversariantes(nomeMes, aniversariantes);

        // Badge do atalho Dashboard
        if (qtdAniversariantes > 0) {
            String textoBadge = qtdAniversariantes == 1
                    ? getString(R.string.badge_menu_dashboard_aniv_singular, totalResidentes, capacidade, qtdAniversariantes)
                    : getString(R.string.badge_menu_dashboard_aniv_plural, totalResidentes, capacidade, qtdAniversariantes);
            binding.tvBadgeResumoMenuDashboard.setText(textoBadge);
            binding.tvBadgeResumoMenuDashboard.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.aniversario_badge_bg))
            );
            binding.tvBadgeResumoMenuDashboard.setTextColor(
                    ContextCompat.getColor(this, R.color.aniversario_badge_fg)
            );
        } else {
            binding.tvBadgeResumoMenuDashboard.setText(
                    getString(R.string.badge_menu_dashboard_sem_aniv, totalResidentes, capacidade)
            );
            binding.tvBadgeResumoMenuDashboard.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_light))
            );
            binding.tvBadgeResumoMenuDashboard.setTextColor(
                    ContextCompat.getColor(this, R.color.primary_dark)
            );
        }

        // KPIs do Plantão, Estoque + Validade, Passagem de Turno e Panorama Clínico
        int pendentesTurno = passagemTurnoDAO.contarPendentesConferencia();
        int urgentesTurno = passagemTurnoDAO.contarUrgentesPendentes();
        atualizarKpisOperacionaisEClinicos(todosIdosos, pendentesTurno, urgentesTurno);

        if (urgentesTurno > 0) {
            binding.tvBadgeResumoPassagemTurno.setText(
                    getString(R.string.badge_passagem_urgente_formato, pendentesTurno, urgentesTurno)
            );
            binding.tvBadgeResumoPassagemTurno.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger_light))
            );
            binding.tvBadgeResumoPassagemTurno.setTextColor(
                    ContextCompat.getColor(this, R.color.danger_red)
            );
        } else if (pendentesTurno > 0) {
            binding.tvBadgeResumoPassagemTurno.setText(
                    pendentesTurno == 1
                            ? getString(R.string.badge_passagem_pendente_singular, pendentesTurno)
                            : getString(R.string.badge_passagem_pendente_plural, pendentesTurno)
            );
            binding.tvBadgeResumoPassagemTurno.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.vagas_alerta_bg))
            );
            binding.tvBadgeResumoPassagemTurno.setTextColor(
                    ContextCompat.getColor(this, R.color.vagas_alerta_fg)
            );
        } else {
            binding.tvBadgeResumoPassagemTurno.setText(R.string.badge_passagem_em_dia);
            binding.tvBadgeResumoPassagemTurno.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_safe_container))
            );
            binding.tvBadgeResumoPassagemTurno.setTextColor(
                    ContextCompat.getColor(this, R.color.md3_on_safe_container)
            );
        }

        atualizarVisualPerfil();
    }

    private void atualizarKpisOperacionaisEClinicos(
            List<Idoso> todosIdosos,
            int pendentesTurno,
            int urgentesTurno
    ) {
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
        int alertasValidade = ValidadeMedicamentoHelper.contarAlertasValidade(todosMeds);
        binding.tvKpiEstoqueCriticoValor.setText(String.valueOf(criticosEstoque));
        if (criticosEstoque > 0) {
            binding.tvKpiEstoqueCriticoValor.setTextColor(ContextCompat.getColor(this, R.color.danger_red));
            String statusEstoque = getString(R.string.kpi_estoque_critico_status, limiteMinimo);
            if (alertasValidade > 0) {
                statusEstoque = statusEstoque + " • " + alertasValidade + " venc./próx.";
            }
            binding.tvKpiEstoqueCriticoStatus.setText(statusEstoque);
            binding.cardKpiEstoqueCritico.setStrokeColor(ContextCompat.getColor(this, R.color.danger_red));
        } else if (alertasValidade > 0) {
            binding.tvKpiEstoqueCriticoValor.setTextColor(ContextCompat.getColor(this, R.color.validade_alerta_bg));
            binding.tvKpiEstoqueCriticoStatus.setText(alertasValidade + " venc./próx.");
            binding.cardKpiEstoqueCritico.setStrokeColor(ContextCompat.getColor(this, R.color.validade_alerta_bg));
        } else {
            binding.tvKpiEstoqueCriticoValor.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            binding.tvKpiEstoqueCriticoStatus.setText(R.string.kpi_estoque_em_dia);
            binding.cardKpiEstoqueCritico.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        }

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
                Intent intent = new Intent(MainActivity.this, DetalhesIdosoActivity.class);
                intent.putExtra("EXTRA_IDOSO_ID", idoso.getId());
                intent.putExtra(EXTRA_PERFIL, perfilAtual);
                startActivity(intent);
            });

            binding.containerListaAniversariantes.addView(itemView);
        }
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
                DashboardManager.salvarCapacidadeTotalVagas(MainActivity.this, novaCapacidade);
                atualizarResumoMenuPrincipal();
                Toast.makeText(
                        MainActivity.this,
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
            setPerfil(PERFIL_ADMIN);
            abrirDialogAjustarCapacidade();
        });

        dialogView.findViewById(R.id.btn_cancelar_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void atualizarVisualPerfil() {
        if (PERFIL_ADMIN.equals(perfilAtual)) {
            binding.tvPerfilAtualBadge.setText(R.string.badge_perfil_admin);
            binding.tvPerfilAtualBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_admin, 0, 0, 0);
            binding.tvDescricaoPerfilAtual.setText(R.string.ativo_admin_desc);
            binding.tvDescricaoPerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.admin_color));
            binding.ivIconeMenuPerfil.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.admin_light));
            binding.ivIconeMenuPerfil.setImageTintList(ContextCompat.getColorStateList(this, R.color.admin_color));
        } else if (PERFIL_ENFERMEIRO.equals(perfilAtual)) {
            binding.tvPerfilAtualBadge.setText(R.string.badge_perfil_enfermeiro);
            binding.tvPerfilAtualBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nurse, 0, 0, 0);
            binding.tvDescricaoPerfilAtual.setText(R.string.ativo_enfermeiro_desc);
            binding.tvDescricaoPerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.enfermeiro_color));
            binding.ivIconeMenuPerfil.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.enfermeiro_light));
            binding.ivIconeMenuPerfil.setImageTintList(ContextCompat.getColorStateList(this, R.color.enfermeiro_color));
        } else {
            binding.tvPerfilAtualBadge.setText(R.string.badge_perfil_cuidador);
            binding.tvPerfilAtualBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nurse, 0, 0, 0);
            binding.tvDescricaoPerfilAtual.setText(R.string.ativo_cuidador_desc);
            binding.tvDescricaoPerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.cuidador_color));
            binding.ivIconeMenuPerfil.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.cuidador_light));
            binding.ivIconeMenuPerfil.setImageTintList(ContextCompat.getColorStateList(this, R.color.cuidador_color));
        }

        PerfilUsuario perfilObj = SessaoManager.getPerfil(this);
        boolean podeCadastrarIdoso = PermissaoUtils.podeCadastrarIdoso(perfilObj);
        binding.cardMenuCadastrar.setEnabled(podeCadastrarIdoso);
        binding.cardMenuCadastrar.setAlpha(podeCadastrarIdoso ? 1.0f : 0.45f);

        boolean podeAlterarCapacidade = PermissaoUtils.podeAlterarConfiguracoes(perfilObj);
        binding.btnAjustarCapacidade.setAlpha(podeAlterarCapacidade ? 1.0f : 0.55f);

        boolean podePdf = PermissaoUtils.podeGerarRelatorio(perfilObj);
        binding.btnDashboardRelatorioGerencialPdf.setVisibility(podePdf ? View.VISIBLE : View.GONE);
    }

    private void abrirDialogSelecaoPerfil() {
        String[] opcoes = {
                "Administrador (ADM) - Criar, editar e excluir fichas e remédios",
                "Enfermeiro(a) - Prescrições, edição de remédios e fichas clínicas",
                "Cuidador - Registro de doses diárias, emergência e leitura"
        };
        int checkedItem = 0;
        if (PERFIL_ENFERMEIRO.equals(perfilAtual)) {
            checkedItem = 1;
        } else if (PERFIL_CUIDADOR.equals(perfilAtual)) {
            checkedItem = 2;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.titulo_selecionar_perfil)
                .setSingleChoiceItems(opcoes, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        setPerfil(PERFIL_ADMIN);
                        Toast.makeText(MainActivity.this, R.string.msg_perfil_admin_ativado, Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        setPerfil(PERFIL_ENFERMEIRO);
                        Toast.makeText(MainActivity.this, R.string.msg_perfil_enfermeiro_ativado, Toast.LENGTH_SHORT).show();
                    } else {
                        setPerfil(PERFIL_CUIDADOR);
                        Toast.makeText(MainActivity.this, R.string.msg_perfil_cuidador_ativado, Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    private void setPerfil(String novoPerfil) {
        this.perfilAtual = novoPerfil;
        PerfilUsuario perfilObj = PerfilUsuario.fromCodigo(novoPerfil);
        SessaoManager.getInstance(this).salvarPerfilAtivo(perfilObj);
        PerfilManager.setPerfil(this, novoPerfil);
        atualizarVisualPerfil();
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
            setPerfil(PERFIL_ADMIN);
            Intent intent = new Intent(MainActivity.this, CadastroIdosoActivity.class);
            intent.putExtra(EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        dialogView.findViewById(R.id.btn_cancelar_dialog).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
