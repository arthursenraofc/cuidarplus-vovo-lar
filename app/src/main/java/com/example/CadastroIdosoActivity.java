package com.example;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.database.IdosoDAO;
import com.example.databinding.ActivityCadastroIdosoBinding;
import com.example.model.Idoso;
import com.example.util.ImageUtil;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/**
 * CadastroIdosoActivity (Sprint 1 - CuidarPlus)
 * Activity em Java puro responsável por:
 * 1. Coleta e validação de dados clínicos e cadastrais do idoso.
 * 2. Integração segura com Câmera (FileProvider) e Galeria.
 * 3. Persistência local no banco de dados SQLite (IdosoDAO).
 * 4. Tratamento completo de exceções e permissões de hardware.
 */
public class CadastroIdosoActivity extends AppCompatActivity {

    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";
    private static final String TAG = "CadastroIdoso";

    private static final String[] TIPOS_SANGUINEOS = {
            "Selecione o tipo sanguíneo",
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Não informado"
    };

    private ActivityCadastroIdosoBinding binding;
    private IdosoDAO idosoDAO;

    private int idosoIdEmEdicao = -1;
    private String caminhoFotoAtual = "";
    private Uri uriFotoTemporaria = null;
    private File arquivoFotoTemporario = null;

    // 1. Launcher para captura de foto da Câmera Nativa
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), sucesso -> {
                if (Boolean.TRUE.equals(sucesso) && arquivoFotoTemporario != null) {
                    caminhoFotoAtual = arquivoFotoTemporario.getAbsolutePath();
                    ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);
                    Toast.makeText(this, "Foto capturada e salva com sucesso!", Toast.LENGTH_SHORT).show();
                }
            });

    // 2. Launcher para solicitação de Permissão de Câmera em tempo de execução
    private final ActivityResultLauncher<String> permissaoCameraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                if (Boolean.TRUE.equals(concedida)) {
                    iniciarCapturaCamera();
                } else {
                    // Tratamento amigável de negação de permissão
                    Toast.makeText(this, 
                            "Permissão de câmera necessária para fotografar os residentes. Por favor, conceda o acesso.", 
                            Toast.LENGTH_LONG).show();
                }
            });

    // 3. Launcher para seleção de foto da Galeria
    private final ActivityResultLauncher<String> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String caminhoSalvo = ImageUtil.copiarUriParaArmazenamentoInterno(this, uri);
                    if (caminhoSalvo != null) {
                        caminhoFotoAtual = caminhoSalvo;
                        ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);
                        Toast.makeText(this, "Foto selecionada da galeria!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Não foi possível carregar a imagem da galeria.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCadastroIdosoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        idosoDAO = new IdosoDAO(this);

        configurarToolbar();
        configurarSpinnerSangue();
        configurarDatePicker();
        configurarBotoesFoto();
        configurarBotoesAcao();

        // Verifica se a Activity foi aberta em modo de edição
        if (getIntent().hasExtra(EXTRA_IDOSO_ID)) {
            long idLong = getIntent().getLongExtra(EXTRA_IDOSO_ID, -1);
            if (idLong != -1) {
                idosoIdEmEdicao = (int) idLong;
            } else {
                idosoIdEmEdicao = getIntent().getIntExtra(EXTRA_IDOSO_ID, -1);
            }

            if (idosoIdEmEdicao != -1) {
                carregarDadosParaEdicao(idosoIdEmEdicao);
            }
        }
    }

    private void configurarToolbar() {
        binding.toolbarCadastro.setNavigationOnClickListener(v -> finish());
    }

    private void configurarSpinnerSangue() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TIPOS_SANGUINEOS);
        binding.spTipoSanguineo.setAdapter(adapter);
    }

    private void configurarDatePicker() {
        binding.etDataNascimento.setOnClickListener(v -> exibirDatePicker());
        binding.tilDataNascimento.setEndIconOnClickListener(v -> exibirDatePicker());
    }

    private void exibirDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int anoSugerido = calendar.get(Calendar.YEAR) - 70; // Foco em idosos
        int mes = calendar.get(Calendar.MONTH);
        int dia = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String dataFormatada = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
            binding.etDataNascimento.setText(dataFormatada);
            binding.tilDataNascimento.setError(null);
        }, anoSugerido, mes, dia);
        dialog.show();
    }

    private void configurarBotoesFoto() {
        binding.btnTirarFotoCamera.setOnClickListener(v -> verificarPermissaoCamera());
        binding.btnEscolherFotoGaleria.setOnClickListener(v -> galeriaLauncher.launch("image/*"));
    }

    /**
     * Verifica permissão de câmera e aciona o launcher adequado.
     */
    private void verificarPermissaoCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCapturaCamera();
        } else {
            permissaoCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Cria arquivo temporário no armazenamento interno e invoca a câmera através do FileProvider.
     */
    private void iniciarCapturaCamera() {
        try {
            arquivoFotoTemporario = ImageUtil.criarArquivoFoto(this);
            uriFotoTemporaria = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    arquivoFotoTemporario
            );
            cameraLauncher.launch(uriFotoTemporaria);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao acessar armazenamento para foto", e);
            Toast.makeText(this, "Erro ao acessar armazenamento para a foto: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void configurarBotoesAcao() {
        binding.btnSalvarCadastro.setOnClickListener(v -> validarESalvar());
        binding.btnCancelarCadastro.setOnClickListener(v -> finish());
    }

    private void carregarDadosParaEdicao(int id) {
        Idoso idoso = idosoDAO.buscarPorId(id);
        if (idoso != null) {
            binding.toolbarCadastro.setTitle(R.string.titulo_editar_idoso);
            binding.btnSalvarCadastro.setText(R.string.btn_atualizar);

            binding.etNome.setText(idoso.getNome());
            binding.etDataNascimento.setText(idoso.getDataNascimento());
            binding.etAlergias.setText(idoso.getAlergias());
            binding.etRestricoes.setText(idoso.getRestricoesMedicas());
            binding.etContatoEmergencia.setText(idoso.getContatoEmergencia());

            caminhoFotoAtual = idoso.getCaminhoFoto() != null ? idoso.getCaminhoFoto() : "";
            ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);

            for (int i = 0; i < TIPOS_SANGUINEOS.length; i++) {
                if (TIPOS_SANGUINEOS[i].equalsIgnoreCase(idoso.getTipoSanguineo())) {
                    binding.spTipoSanguineo.setSelection(i);
                    break;
                }
            }
        }
    }

    /**
     * Valida os campos obrigatórios e realiza a gravação no SQLite.
     */
    private void validarESalvar() {
        String nome = binding.etNome.getText() != null ? binding.etNome.getText().toString().trim() : "";
        String dataNasc = binding.etDataNascimento.getText() != null ? binding.etDataNascimento.getText().toString().trim() : "";
        String alergias = binding.etAlergias.getText() != null ? binding.etAlergias.getText().toString().trim() : "";
        String restricoes = binding.etRestricoes.getText() != null ? binding.etRestricoes.getText().toString().trim() : "";
        String contato = binding.etContatoEmergencia.getText() != null ? binding.etContatoEmergencia.getText().toString().trim() : "";

        String tipoSangue = binding.spTipoSanguineo.getSelectedItem() != null ? binding.spTipoSanguineo.getSelectedItem().toString() : "Não informado";
        if (binding.spTipoSanguineo.getSelectedItemPosition() == 0) {
            tipoSangue = "Não informado";
        }

        // 1. Validação de Nome Obrigatório
        if (nome.isEmpty()) {
            binding.tilNome.setError("Informe o nome completo do residente.");
            binding.etNome.requestFocus();
            Toast.makeText(this, "Atenção: O nome do idoso é obrigatório!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            binding.tilNome.setError(null);
        }

        // 2. Validação de Data de Nascimento Obrigatória
        if (dataNasc.isEmpty()) {
            binding.tilDataNascimento.setError("Selecione a data de nascimento.");
            Toast.makeText(this, "Atenção: A data de nascimento é obrigatória!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            binding.tilDataNascimento.setError(null);
        }

        // 3. Validação de Contato de Emergência Obrigatório
        if (contato.isEmpty()) {
            binding.tilContatoEmergencia.setError("Informe um contato com telefone para emergências.");
            binding.etContatoEmergencia.requestFocus();
            Toast.makeText(this, "Atenção: O contato de emergência é obrigatório!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            binding.tilContatoEmergencia.setError(null);
        }

        try {
            if (idosoIdEmEdicao == -1) {
                // Inserção no SQLite
                Idoso novoIdoso = new Idoso(nome, dataNasc, tipoSangue, alergias, restricoes, contato, caminhoFotoAtual);
                long idCriado = idosoDAO.inserir(novoIdoso);

                if (idCriado > 0) {
                    Toast.makeText(this, "✅ " + getString(R.string.sucesso_salvo), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish(); // Retorna automaticamente para a lista de idosos
                } else {
                    Toast.makeText(this, "Erro ao gravar no banco SQLite.", Toast.LENGTH_LONG).show();
                }
            } else {
                // Atualização no SQLite
                Idoso idosoAtualizado = new Idoso(idosoIdEmEdicao, nome, dataNasc, tipoSangue, alergias, restricoes, contato, caminhoFotoAtual);
                int linhas = idosoDAO.atualizar(idosoAtualizado);

                if (linhas > 0) {
                    Toast.makeText(this, "✅ " + getString(R.string.sucesso_atualizado), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish(); // Retorna automaticamente
                } else {
                    Toast.makeText(this, "Erro ao atualizar dados.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha na operação com o banco de dados", e);
            Toast.makeText(this, "Falha na operação com o banco de dados: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
