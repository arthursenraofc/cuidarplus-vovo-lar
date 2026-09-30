package com.example;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.database.IdosoDAO;
import com.example.databinding.ActivityDetalhesIdosoBinding;
import com.example.model.Idoso;
import com.example.util.ImageUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Activity para Visualização Completa da Ficha e Prontuário do Idoso,
 * com ações de ligação de emergência, edição e exclusão.
 */
public class DetalhesIdosoActivity extends AppCompatActivity {

    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";

    private ActivityDetalhesIdosoBinding binding;
    private IdosoDAO idosoDAO;
    private int idosoId = -1;
    private String perfilUsuario = MainActivity.PERFIL_ADMIN;
    private Idoso idosoAtual = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetalhesIdosoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        idosoDAO = new IdosoDAO(this);

        if (getIntent().hasExtra(EXTRA_IDOSO_ID)) {
            long idLong = getIntent().getLongExtra(EXTRA_IDOSO_ID, -1);
            if (idLong != -1) {
                idosoId = (int) idLong;
            } else {
                idosoId = getIntent().getIntExtra(EXTRA_IDOSO_ID, -1);
            }
        }
        if (getIntent().hasExtra(MainActivity.EXTRA_PERFIL)) {
            perfilUsuario = getIntent().getStringExtra(MainActivity.EXTRA_PERFIL);
        }

        if (idosoId == -1) {
            Toast.makeText(this, "Registro não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        configurarToolbar();
        configurarPermissoesPerfil();
        configurarBotoes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarDados();
    }

    private void configurarToolbar() {
        binding.toolbarDetalhes.setNavigationOnClickListener(v -> finish());
    }

    private void configurarPermissoesPerfil() {
        // Se o usuário for Cuidador, oculta as opções de edição e exclusão
        if (MainActivity.PERFIL_CUIDADOR.equals(perfilUsuario)) {
            binding.layoutAcoesAdmin.setVisibility(View.GONE);
        } else {
            binding.layoutAcoesAdmin.setVisibility(View.VISIBLE);
        }
    }

    private void carregarDados() {
        idosoAtual = idosoDAO.buscarPorId(idosoId);
        if (idosoAtual == null) {
            Toast.makeText(this, "Residente não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Foto com tratamento circular
        ImageUtil.carregarFoto(binding.ivDetalheFoto, idosoAtual.getCaminhoFoto());

        // Nome
        binding.tvDetalheNome.setText(idosoAtual.getNome());

        // Idade e Data de Nascimento
        int idade = idosoAtual.calcularIdade();
        if (idade > 0) {
            binding.tvDetalheIdade.setText(getString(R.string.idade_calculada, idade));
        } else {
            binding.tvDetalheIdade.setText(R.string.idade_nao_informada);
        }

        String dataNasc = idosoAtual.getDataNascimento();
        binding.tvDetalheNascimento.setText(getString(R.string.formato_nascimento,
                (dataNasc != null && !dataNasc.isEmpty() ? dataNasc : "-")));

        // Tipo Sanguíneo
        String sangue = idosoAtual.getTipoSanguineo();
        if (sangue != null && !sangue.trim().isEmpty() && !sangue.equalsIgnoreCase("Não informado")) {
            binding.tvDetalheTipoSanguineo.setVisibility(View.VISIBLE);
            binding.tvDetalheTipoSanguineo.setText(getString(R.string.formato_tipo_sanguineo, sangue));
        } else {
            binding.tvDetalheTipoSanguineo.setVisibility(View.GONE);
        }

        // Alergias
        String alergias = idosoAtual.getAlergias();
        if (alergias != null && !alergias.trim().isEmpty()) {
            binding.tvDetalheAlergias.setText(alergias);
        } else {
            binding.tvDetalheAlergias.setText(R.string.nenhuma_alergia);
        }

        // Restrições Médicas
        String restricoes = idosoAtual.getRestricoesMedicas();
        if (restricoes != null && !restricoes.trim().isEmpty()) {
            binding.tvDetalheRestricoes.setText(restricoes);
        } else {
            binding.tvDetalheRestricoes.setText(R.string.nenhuma_restricao);
        }

        // Contato de Emergência
        String contato = idosoAtual.getContatoEmergencia();
        if (contato != null && !contato.trim().isEmpty()) {
            binding.tvDetalheContatoEmergencia.setText(contato);
            binding.btnLigarContato.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetalheContatoEmergencia.setText(R.string.nenhum_contato);
            binding.btnLigarContato.setVisibility(View.GONE);
        }
    }

    private void configurarBotoes() {
        // Ação de discar para o contato de emergência via Intent nativa
        binding.btnLigarContato.setOnClickListener(v -> {
            if (idosoAtual != null && idosoAtual.getContatoEmergencia() != null) {
                String numeroLimpo = extrairNumeroTelefone(idosoAtual.getContatoEmergencia());
                if (!numeroLimpo.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + numeroLimpo));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Número de telefone não identificado no contato.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Ação de Ver Medicamentos e Alarmes (Sprint 2)
        binding.btnVerMedicamentos.setOnClickListener(v -> {
            Intent intent = new Intent(DetalhesIdosoActivity.this, ListaMedicamentosActivity.class);
            intent.putExtra(ListaMedicamentosActivity.EXTRA_IDOSO_ID, idosoId);
            if (idosoAtual != null) {
                intent.putExtra(ListaMedicamentosActivity.EXTRA_NOME_IDOSO, idosoAtual.getNome());
            }
            startActivity(intent);
        });

        // Ação de Editar Ficha (Apenas Admin)
        binding.btnEditarFicha.setOnClickListener(v -> {
            Intent intent = new Intent(DetalhesIdosoActivity.this, CadastroIdosoActivity.class);
            intent.putExtra(CadastroIdosoActivity.EXTRA_IDOSO_ID, idosoId);
            intent.putExtra(MainActivity.EXTRA_PERFIL, perfilUsuario);
            startActivity(intent);
        });

        // Ação de Excluir Ficha (Apenas Admin) com Confirmação
        binding.btnExcluirFicha.setOnClickListener(v -> confirmarExclusao());
    }

    private void confirmarExclusao() {
        if (idosoAtual == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirmar_exclusao_titulo)
                .setMessage(getString(R.string.confirmar_exclusao_msg, idosoAtual.getNome()))
                .setPositiveButton(R.string.excluir, (dialog, which) -> {
                    int linhas = idosoDAO.excluir(idosoId);
                    if (linhas > 0) {
                        Toast.makeText(DetalhesIdosoActivity.this, getString(R.string.sucesso_excluido), Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(DetalhesIdosoActivity.this, "Erro ao remover registro.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    /**
     * Extrai apenas os dígitos de uma string de contato para chamada telefônica.
     */
    private String extrairNumeroTelefone(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[^0-9+]", "");
    }
}
