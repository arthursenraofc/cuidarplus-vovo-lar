package com.example;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.adapter.IdosoAdapter;
import com.example.database.IdosoDAO;
import com.example.databinding.ActivityListaIdososBinding;
import com.example.model.Idoso;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Activity que exibe a listagem e busca em tempo real de todos os idosos cadastrados no Lar.
 */
public class ListaIdososActivity extends AppCompatActivity implements IdosoAdapter.OnIdosoClickListener {

    private ActivityListaIdososBinding binding;
    private IdosoDAO idosoDAO;
    private IdosoAdapter adapter;
    private String perfilUsuario = MainActivity.PERFIL_ADMIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListaIdososBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        idosoDAO = new IdosoDAO(this);

        // Recupera o perfil selecionado
        if (getIntent().hasExtra(MainActivity.EXTRA_PERFIL)) {
            perfilUsuario = getIntent().getStringExtra(MainActivity.EXTRA_PERFIL);
        }

        configurarToolbar();
        configurarRecyclerView();
        configurarBusca();
        configurarBotoes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarrega a lista toda vez que a tela volta ao primeiro plano (após cadastro/edição/exclusão)
        carregarListaIdosos(binding.etCampoBusca.getText() != null ? binding.etCampoBusca.getText().toString() : "");
    }

    private void configurarToolbar() {
        binding.toolbarLista.setNavigationOnClickListener(v -> finish());

        // Atualiza a identificação do perfil na barra superior usando ContextCompat
        if (MainActivity.PERFIL_ADMIN.equals(perfilUsuario)) {
            binding.tvBadgePerfilAtual.setText(R.string.badge_perfil_admin_lista);
            binding.tvBadgePerfilAtual.setTextColor(ContextCompat.getColor(this, R.color.admin_color));
            binding.fabNovoIdoso.setVisibility(View.VISIBLE);
        } else {
            binding.tvBadgePerfilAtual.setText(R.string.badge_perfil_cuidador_lista);
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
                binding.ivLimparBusca.setVisibility(termo.isEmpty() ? View.GONE : View.VISIBLE);
                carregarListaIdosos(termo);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.ivLimparBusca.setOnClickListener(v -> binding.etCampoBusca.setText(""));
    }

    private void configurarBotoes() {
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
            SharedPreferences prefs = getSharedPreferences("CuidarPlusPrefs", MODE_PRIVATE);
            prefs.edit().putString("perfil_usuario", MainActivity.PERFIL_ADMIN).apply();
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
        intent.putExtra("EXTRA_IDOSO_ID", idoso.getId());
        intent.putExtra(MainActivity.EXTRA_PERFIL, perfilUsuario);
        startActivity(intent);
    }
}
