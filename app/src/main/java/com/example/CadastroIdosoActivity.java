package com.example;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.database.IdosoDAO;
import com.example.databinding.ActivityCadastroIdosoBinding;
import com.example.model.Idoso;
import com.example.util.ImageUtil;
import com.example.util.MascaraDataWatcher;
import com.example.util.MascaraTelefoneWatcher;
import com.example.util.ValidadorCadastroUtils;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/**
 * CadastroIdosoActivity (CuidarPlus - ILPI Vovô Lar)
 * Activity responsável por:
 * 1. Formatação em tempo real com TextWatchers (Data DD/MM/AAAA e Telefone BR dinâmico).
 * 2. Validação semântica completa com feedback visual Material Design 3 (TextInputLayout.setError).
 * 3. Captura segura de foto pela câmera do dispositivo (FileProvider -> arquivos privados getFilesDir())
 *    com placeholder dinâmico e Photo Picker para seleção opcional.
 * 4. Higienização dos dados e persistência local no SQLite (IdosoDAO).
 */
public class CadastroIdosoActivity extends AppCompatActivity {

    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";
    private static final String TAG = "CadastroIdoso";

    private static final String STATE_CAMINHO_FOTO_ATUAL = "state_caminho_foto_atual";
    private static final String STATE_CAMINHO_FOTO_ORIGINAL = "state_caminho_foto_original";
    private static final String STATE_CAMINHO_ARQUIVO_TEMP = "state_caminho_arquivo_temp";

    private static final String[] TIPOS_SANGUINEOS = {
            "Selecione o tipo sanguíneo",
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Não informado"
    };

    private ActivityCadastroIdosoBinding binding;
    private IdosoDAO idosoDAO;

    private int idosoIdEmEdicao = -1;
    private String caminhoFotoAtual = "";
    private String caminhoFotoOriginalBanco = "";
    private File arquivoFotoTemporario = null;

    // 1. Launcher para captura de foto da Câmera do Dispositivo (FileProvider -> getFilesDir())
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::processarResultadoCamera);

    // 2. Launcher para solicitação de Permissão de Câmera em tempo de execução
    private final ActivityResultLauncher<String> permissaoCameraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                if (Boolean.TRUE.equals(concedida)) {
                    iniciarCapturaCamera();
                } else {
                    Log.i(TAG, "Permissão CAMERA negada pelo usuário no cadastro de residente.");
                    Toast.makeText(this, R.string.msg_permissao_camera_necessaria, Toast.LENGTH_LONG).show();
                }
            });

    // 3. Launcher zero-permissão (Android Photo Picker) para seleção de foto existente
    private final ActivityResultLauncher<PickVisualMediaRequest> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    String caminhoSalvo = ImageUtil.copiarUriParaArmazenamentoInterno(this, uri);
                    if (caminhoSalvo != null) {
                        substituirPreviewFotoTemporaria(caminhoSalvo);
                        Toast.makeText(this, "Foto selecionada e salva!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Não foi possível carregar a imagem selecionada.", Toast.LENGTH_SHORT).show();
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
        configurarMascarasEValidacaoEmTempoReal();
        configurarDatePickerOpcional();
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

            if (idosoIdEmEdicao != -1 && savedInstanceState == null) {
                carregarDadosParaEdicao(idosoIdEmEdicao);
            } else if (idosoIdEmEdicao != -1) {
                binding.toolbarCadastro.setTitle(R.string.titulo_editar_idoso);
                binding.btnSalvarCadastro.setText(R.string.btn_atualizar);
            }
        }

        if (savedInstanceState != null) {
            caminhoFotoAtual = savedInstanceState.getString(STATE_CAMINHO_FOTO_ATUAL, "");
            caminhoFotoOriginalBanco = savedInstanceState.getString(STATE_CAMINHO_FOTO_ORIGINAL, "");
            String caminhoTemp = savedInstanceState.getString(STATE_CAMINHO_ARQUIVO_TEMP, null);
            if (caminhoTemp != null && !caminhoTemp.isEmpty()) {
                arquivoFotoTemporario = new File(caminhoTemp);
            }
        }

        // Garante exibição inicial da foto salva ou do placeholder do perfil
        ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CAMINHO_FOTO_ATUAL, caminhoFotoAtual);
        outState.putString(STATE_CAMINHO_FOTO_ORIGINAL, caminhoFotoOriginalBanco);
        if (arquivoFotoTemporario != null) {
            outState.putString(STATE_CAMINHO_ARQUIVO_TEMP, arquivoFotoTemporario.getAbsolutePath());
        }
    }

    private void configurarToolbar() {
        binding.toolbarCadastro.setNavigationOnClickListener(v -> cancelarESair());
    }

    private void configurarSpinnerSangue() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                TIPOS_SANGUINEOS
        );
        binding.spTipoSanguineo.setAdapter(adapter);
    }

    private void configurarMascarasEValidacaoEmTempoReal() {
        binding.etDataNascimento.addTextChangedListener(
                new MascaraDataWatcher(binding.etDataNascimento, binding.tilDataNascimento)
        );

        binding.etContatoEmergencia.addTextChangedListener(
                new MascaraTelefoneWatcher(binding.etContatoEmergencia, binding.tilContatoEmergencia)
        );

        binding.etNome.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding.tilNome.getError() != null) {
                    binding.tilNome.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void configurarDatePickerOpcional() {
        binding.tilDataNascimento.setEndIconOnClickListener(v -> exibirDatePicker());
    }

    private void exibirDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int anoSugerido = calendar.get(Calendar.YEAR) - 70;
        int mes = calendar.get(Calendar.MONTH);
        int dia = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String dataFormatada = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
            binding.etDataNascimento.setText(dataFormatada);
            binding.tilDataNascimento.setError(null);
        }, anoSugerido, mes, dia);
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void configurarBotoesFoto() {
        binding.ivPreviewFoto.setOnClickListener(v -> verificarPermissaoCamera());
        binding.btnTirarFotoCamera.setOnClickListener(v -> verificarPermissaoCamera());
        binding.btnEscolherFotoGaleria.setOnClickListener(v ->
                galeriaLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build())
        );
    }

    private void verificarPermissaoCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCapturaCamera();
        } else {
            permissaoCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void iniciarCapturaCamera() {
        try {
            arquivoFotoTemporario = ImageUtil.criarArquivoTemporarioCaptura(this);
            Intent intentCamera = ImageUtil.criarIntentCapturaCamera(this, arquivoFotoTemporario);
            cameraLauncher.launch(intentCamera);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Nenhum aplicativo de câmera disponível no dispositivo.");
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
            arquivoFotoTemporario = null;
            Toast.makeText(this, R.string.erro_camera_indisponivel, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Falha ao preparar captura de foto: " + e.getMessage());
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
            arquivoFotoTemporario = null;
            Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void processarResultadoCamera(ActivityResult resultado) {
        if (resultado.getResultCode() != RESULT_OK) {
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
            arquivoFotoTemporario = null;
            return;
        }

        String novoCaminhoPrivado = null;
        if (arquivoFotoTemporario != null && arquivoFotoTemporario.exists() && arquivoFotoTemporario.length() > 0) {
            novoCaminhoPrivado = ImageUtil.salvarFotoCapturadaNoArmazenamentoPrivado(this, arquivoFotoTemporario);
        } else {
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
        }
        arquivoFotoTemporario = null;

        if (novoCaminhoPrivado == null && resultado.getData() != null && resultado.getData().getExtras() != null) {
            Object extraBitmap = resultado.getData().getExtras().get("data");
            if (extraBitmap instanceof Bitmap) {
                novoCaminhoPrivado = ImageUtil.salvarBitmapNoArmazenamentoPrivado(this, (Bitmap) extraBitmap);
            }
        }

        if (novoCaminhoPrivado != null) {
            substituirPreviewFotoTemporaria(novoCaminhoPrivado);
            Toast.makeText(this, R.string.sucesso_foto_perfil_atualizada, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void substituirPreviewFotoTemporaria(String novoCaminhoPrivado) {
        if (caminhoFotoAtual != null
                && !caminhoFotoAtual.isEmpty()
                && !caminhoFotoAtual.equals(caminhoFotoOriginalBanco)
                && !caminhoFotoAtual.equals(novoCaminhoPrivado)) {
            ImageUtil.removerFotoPrivadaSeExistir(this, caminhoFotoAtual);
        }
        caminhoFotoAtual = novoCaminhoPrivado;
        ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);
    }

    private void cancelarESair() {
        if (caminhoFotoAtual != null
                && !caminhoFotoAtual.isEmpty()
                && !caminhoFotoAtual.equals(caminhoFotoOriginalBanco)) {
            ImageUtil.removerFotoPrivadaSeExistir(this, caminhoFotoAtual);
        }
        ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
        finish();
    }

    private void configurarBotoesAcao() {
        binding.btnSalvarCadastro.setOnClickListener(v -> validarESalvar());
        binding.btnCancelarCadastro.setOnClickListener(v -> cancelarESair());
    }

    private void carregarDadosParaEdicao(int id) {
        Idoso idoso = idosoDAO.buscarPorId(id);
        if (idoso != null) {
            binding.toolbarCadastro.setTitle(R.string.titulo_editar_idoso);
            binding.btnSalvarCadastro.setText(R.string.btn_atualizar);

            binding.etNome.setText(idoso.getNome());
            binding.etDataNascimento.setText(ValidadorCadastroUtils.higienizarData(idoso.getDataNascimento()));
            binding.etAlergias.setText(idoso.getAlergias());
            binding.etRestricoes.setText(idoso.getRestricoesMedicas());
            binding.etContatoEmergencia.setText(
                    ValidadorCadastroUtils.higienizarTelefoneFormatado(idoso.getContatoEmergencia())
            );

            caminhoFotoOriginalBanco = idoso.getCaminhoFoto() != null ? idoso.getCaminhoFoto() : "";
            caminhoFotoAtual = caminhoFotoOriginalBanco;
            ImageUtil.carregarFoto(binding.ivPreviewFoto, caminhoFotoAtual);

            for (int i = 0; i < TIPOS_SANGUINEOS.length; i++) {
                if (TIPOS_SANGUINEOS[i].equalsIgnoreCase(idoso.getTipoSanguineo())) {
                    binding.spTipoSanguineo.setSelection(i);
                    break;
                }
            }
        }
    }

    private void validarESalvar() {
        String nomeBruto = obterTextoCampo(binding.etNome);
        String dataNascBruta = obterTextoCampo(binding.etDataNascimento);
        String alergiasBruta = obterTextoCampo(binding.etAlergias);
        String restricoesBruta = obterTextoCampo(binding.etRestricoes);
        String telefoneBruto = obterTextoCampo(binding.etContatoEmergencia);

        boolean formularioValido = true;

        String nomeHigienizado = ValidadorCadastroUtils.higienizarTexto(nomeBruto);
        if (nomeHigienizado.isEmpty()) {
            binding.tilNome.setErrorEnabled(true);
            binding.tilNome.setError("Informe o nome completo do residente.");
            binding.etNome.requestFocus();
            formularioValido = false;
        } else {
            binding.tilNome.setError(null);
        }

        if (!ValidadorCadastroUtils.validarDataNascimento(dataNascBruta, binding.tilDataNascimento)) {
            if (formularioValido) {
                binding.etDataNascimento.requestFocus();
            }
            formularioValido = false;
        }

        if (!ValidadorCadastroUtils.validarTelefone(telefoneBruto, binding.tilContatoEmergencia)) {
            if (formularioValido) {
                binding.etContatoEmergencia.requestFocus();
            }
            formularioValido = false;
        }

        if (!formularioValido) {
            Toast.makeText(this, "Verifique os campos destacados antes de salvar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String dataNascimentoHigienizada = ValidadorCadastroUtils.higienizarData(dataNascBruta);
        String telefoneApenasDigitos = ValidadorCadastroUtils.higienizarTelefoneApenasDigitos(telefoneBruto);
        String telefonePadronizado = MascaraTelefoneWatcher.formatarTelefone(telefoneApenasDigitos);
        String alergiasHigienizadas = ValidadorCadastroUtils.higienizarTexto(alergiasBruta);
        String restricoesHigienizadas = ValidadorCadastroUtils.higienizarTexto(restricoesBruta);

        String tipoSangue = binding.spTipoSanguineo.getSelectedItem() != null
                ? binding.spTipoSanguineo.getSelectedItem().toString()
                : "Não informado";
        if (binding.spTipoSanguineo.getSelectedItemPosition() == 0) {
            tipoSangue = "Não informado";
        }

        try {
            if (idosoIdEmEdicao == -1) {
                Idoso novoIdoso = new Idoso(
                        nomeHigienizado,
                        dataNascimentoHigienizada,
                        tipoSangue,
                        alergiasHigienizadas,
                        restricoesHigienizadas,
                        telefonePadronizado,
                        caminhoFotoAtual
                );
                long idCriado = idosoDAO.inserir(novoIdoso);

                if (idCriado > 0) {
                    Toast.makeText(this, getString(R.string.sucesso_salvo), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Erro ao gravar no banco SQLite.", Toast.LENGTH_LONG).show();
                }
            } else {
                Idoso idosoAtualizado = new Idoso(
                        idosoIdEmEdicao,
                        nomeHigienizado,
                        dataNascimentoHigienizada,
                        tipoSangue,
                        alergiasHigienizadas,
                        restricoesHigienizadas,
                        telefonePadronizado,
                        caminhoFotoAtual
                );
                int linhas = idosoDAO.atualizar(idosoAtualizado);

                if (linhas > 0) {
                    if (!caminhoFotoOriginalBanco.isEmpty()
                            && !caminhoFotoOriginalBanco.equals(caminhoFotoAtual)) {
                        ImageUtil.removerFotoPrivadaSeExistir(this, caminhoFotoOriginalBanco);
                    }
                    Toast.makeText(this, getString(R.string.sucesso_atualizado), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Erro ao atualizar dados.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha na operação com o banco de dados", e);
            Toast.makeText(this, "Falha na operação com o banco de dados: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String obterTextoCampo(com.google.android.material.textfield.TextInputEditText campo) {
        return campo.getText() != null ? campo.getText().toString() : "";
    }
}
