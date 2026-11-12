package com.example;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

import com.example.database.IdosoDAO;
import com.example.databinding.ActivityMainBinding;
import com.example.model.Idoso;
import com.example.model.PerfilUsuario;
import com.example.util.DashboardManager;
import com.example.util.NotificationHelper;
import com.example.util.PerfilManager;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;

/**
 * Menu Principal do CuidarPlus (Material Design 3).
 * Focado na operação diária da equipe da ILPI Vovô Lar, oferecendo acesso direto a
 * prontuários, medicamentos, estoque, cadastro de residentes, seleção de perfil e
 * uma opção dedicada ("Dashboard & Indicadores") para consulta sob demanda dos indicadores.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String EXTRA_PERFIL = "EXTRA_PERFIL";
    public static final String PERFIL_ADMIN = PerfilManager.PERFIL_ADMIN;
    public static final String PERFIL_ENFERMEIRO = PerfilManager.PERFIL_ENFERMEIRO;
    public static final String PERFIL_CUIDADOR = PerfilManager.PERFIL_CUIDADOR;

    private ActivityMainBinding binding;
    private IdosoDAO idosoDAO;
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

        // Vinculação explícita e estrita ao layout unificado da Sprint 3 (Módulos e Operação Diária)
        setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.bind(findViewById(R.id.root_dashboard));

        configurarEdgeToEdgeInsets();

        idosoDAO = new IdosoDAO(this);
        perfilAtual = PerfilManager.getPerfilAtual(this);

        // Renderização imediata e síncrona dos módulos antes do primeiro frame na tela
        configurarMenuPrincipal();
        atualizarResumoMenuPrincipal();
        sincronizadoNaCriacao = true;

        NotificationHelper.criarCanalNotificacao(this);
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

        // 1. Ver Fichas dos Idosos
        binding.cardMenuFichas.setOnClickListener(v -> abrirListaIdosos());

        // 2. Controle de Medicamentos & Alarmes
        binding.cardMenuMedicamentos.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MedicamentoActivity.class);
            intent.putExtra(EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        // 3. Estoque Geral de Medicamentos & Relatórios PDF
        binding.cardMenuEstoque.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EstoqueActivity.class);
            intent.putExtra(EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

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

    private void abrirDashboardInstituicao() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        intent.putExtra(EXTRA_PERFIL, perfilAtual);
        startActivity(intent);
    }

    /**
     * Atualiza o contador rápido do cabeçalho e o badge compacto da opção "Dashboard & Indicadores".
     */
    public void atualizarResumoMenuPrincipal() {
        List<Idoso> todosIdosos = idosoDAO.listarTodos();
        int totalResidentes = todosIdosos.size();

        if (totalResidentes == 1) {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_singular, totalResidentes));
        } else {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_plural, totalResidentes));
        }

        int capacidade = DashboardManager.getCapacidadeTotalVagas(this);
        List<DashboardManager.AniversarianteMes> aniversariantes =
                DashboardManager.filtrarAniversariantesDoMes(todosIdosos, Calendar.getInstance());
        int qtdAniversariantes = aniversariantes.size();

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

        atualizarVisualPerfil();
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
