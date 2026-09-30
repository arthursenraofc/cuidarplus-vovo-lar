package com.example;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.database.IdosoDAO;
import com.example.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dashboard / Tela Principal do CuidarPlus (Sprint 1)
 * Oferece navegação direta e acessível para:
 * 1. [Cadastrar Novo Idoso]
 * 2. [Ver Fichas dos Idosos]
 * 3. [Seleção de Perfil (ADM / Cuidador)]
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_PERFIL = "EXTRA_PERFIL";
    public static final String PERFIL_ADMIN = "ADMIN";
    public static final String PERFIL_CUIDADOR = "CUIDADOR";
    private static final String PREFS_NAME = "CuidarPlusPrefs";
    private static final String KEY_PERFIL = "perfil_usuario";

    private ActivityMainBinding binding;
    private IdosoDAO idosoDAO;
    private String perfilAtual = PERFIL_ADMIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        idosoDAO = new IdosoDAO(this);

        // Recupera perfil salvo nas preferências (padrão: ADMIN)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        perfilAtual = prefs.getString(KEY_PERFIL, PERFIL_ADMIN);

        configurarDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarResumoDashboard();
    }

    private void configurarDashboard() {
        atualizarVisualPerfil();

        // 1. Botão [Cadastrar Novo Idoso]
        binding.cardMenuCadastrar.setOnClickListener(v -> {
            if (PERFIL_ADMIN.equals(perfilAtual)) {
                Intent intent = new Intent(MainActivity.this, CadastroIdosoActivity.class);
                intent.putExtra(EXTRA_PERFIL, perfilAtual);
                startActivity(intent);
            } else {
                exibirAvisoPerfilRestrito();
            }
        });

        // 2. Botão [Ver Fichas dos Idosos]
        binding.cardMenuFichas.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ListaIdososActivity.class);
            intent.putExtra(EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        // 3. Botão [Controle de Medicamentos & Alarmes] (Sprint 2)
        binding.cardMenuMedicamentos.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ListaMedicamentosActivity.class);
            intent.putExtra(EXTRA_PERFIL, perfilAtual);
            startActivity(intent);
        });

        // 4. Botão [Seleção de Perfil (ADM / Cuidador)]
        binding.cardMenuPerfil.setOnClickListener(v -> abrirDialogSelecaoPerfil());

        // Clique no resumo superior também abre seletor de perfil
        binding.cardStatusTopo.setOnClickListener(v -> abrirDialogSelecaoPerfil());
    }

    /**
     * Atualiza as informações do topo com contador de residentes e estado do perfil.
     */
    private void atualizarResumoDashboard() {
        int totalResidentes = idosoDAO.contarTotalIdosos();
        if (totalResidentes == 1) {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_singular, totalResidentes));
        } else {
            binding.tvContadorDashboard.setText(getString(R.string.contador_residentes_plural, totalResidentes));
        }
        atualizarVisualPerfil();
    }

    /**
     * Ajusta cores, textos e ícones com base no perfil ativo usando ContextCompat.
     */
    private void atualizarVisualPerfil() {
        if (PERFIL_ADMIN.equals(perfilAtual)) {
            binding.tvPerfilAtualBadge.setText(R.string.badge_perfil_admin);
            binding.tvPerfilAtualBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_admin, 0, 0, 0);
            binding.tvDescricaoPerfilAtual.setText(R.string.ativo_admin_desc);
            binding.tvDescricaoPerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.admin_color));
            binding.ivIconeMenuPerfil.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.admin_light));
            binding.ivIconeMenuPerfil.setImageTintList(ContextCompat.getColorStateList(this, R.color.admin_color));
        } else {
            binding.tvPerfilAtualBadge.setText(R.string.badge_perfil_cuidador);
            binding.tvPerfilAtualBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nurse, 0, 0, 0);
            binding.tvDescricaoPerfilAtual.setText(R.string.ativo_cuidador_desc);
            binding.tvDescricaoPerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.cuidador_color));
            binding.ivIconeMenuPerfil.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.cuidador_light));
            binding.ivIconeMenuPerfil.setImageTintList(ContextCompat.getColorStateList(this, R.color.cuidador_color));
        }
    }

    /**
     * Exibe o diálogo para alternar o perfil entre Administrador e Cuidador.
     */
    private void abrirDialogSelecaoPerfil() {
        String[] opcoes = {
                "Administrador (ADM) - Criar, editar e excluir fichas",
                "Cuidador / Enfermagem - Leitura e emergência"
        };
        int checkedItem = PERFIL_ADMIN.equals(perfilAtual) ? 0 : 1;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.titulo_selecionar_perfil)
                .setSingleChoiceItems(opcoes, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        setPerfil(PERFIL_ADMIN);
                        Toast.makeText(MainActivity.this, R.string.msg_perfil_admin_ativado, Toast.LENGTH_SHORT).show();
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_PERFIL, novoPerfil).apply();
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
