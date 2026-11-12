package com.example;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import com.example.database.AcessoNaoAutorizadoException;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.databinding.ActivityDetalhesIdosoBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PerfilUsuario;
import com.example.util.FichaIdosoPdfGenerator;
import com.example.util.ImageUtil;
import com.example.util.PermissaoUtils;
import com.example.util.ResumoProntuarioPdfGenerator;
import com.example.util.SessaoManager;
import com.example.util.WhatsAppUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Activity para Visualização Completa da Ficha e Prontuário do Residente (ILPI Vovô Lar),
 * incluindo captura de foto de perfil via câmera nativa com armazenamento privado local
 * (Context.getFilesDir()), geração e exportação assíncrona do Prontuário em PDF A4 (100% offline),
 * contato rápido com o familiar via WhatsApp, discagem de emergência e controle RBAC.
 */
public class DetalhesIdosoActivity extends AppCompatActivity {

    private static final String TAG = "DetalhesIdosoActivity";

    public static final String EXTRA_IDOSO_ID = "EXTRA_IDOSO_ID";
    public static final String EXTRA_IDOSO = "EXTRA_IDOSO";
    public static final String EXTRA_NOME_IDOSO = "nomeIdoso";
    public static final String EXTRA_TELEFONE_FAMILIAR = "telefoneFamiliar";

    private static final String STATE_CAMINHO_ARQUIVO_TEMP = "state_caminho_arquivo_temp";
    private static final String STATE_CAMINHO_FOTO_SESSAO = "state_caminho_foto_sessao";

    private static final String WHATSAPP_API_BASE_URL = "https://api.whatsapp.com/send?phone=";
    private static final String PACKAGE_WHATSAPP = "com.whatsapp";
    private static final String PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b";

    private ActivityDetalhesIdosoBinding binding;
    private IdosoDAO idosoDAO;
    private MedicamentoDAO medicamentoDAO;
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    volatile Future<?> ultimaTarefaPdf = null;
    volatile Future<?> ultimaTarefaResumoOffline = null;

    private int idosoId = -1;
    private String perfilUsuario = MainActivity.PERFIL_ADMIN;
    private Idoso idosoAtual = null;
    private File arquivoResumoOfflineAtual = null;

    // Controle de captura de foto via câmera nativa para o perfil do residente
    private File arquivoFotoTemporario = null;
    private String caminhoFotoCapturadaSessao = null;

    // Dados recuperados via Intent quando enviados diretamente
    private Idoso idosoViaIntent = null;
    private String nomeIdosoIntent = null;
    private String telefoneFamiliarIntent = null;
    private boolean possuiExtrasDiretos = false;

    private final ActivityResultLauncher<Intent> cameraPerfilLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::processarResultadoCameraPerfil
            );

    private final ActivityResultLauncher<String> permissaoCameraPerfilLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    concedida -> {
                        if (Boolean.TRUE.equals(concedida)) {
                            iniciarCapturaCameraPerfil();
                        } else {
                            Log.i(TAG, "Permissão CAMERA negada ao tentar atualizar foto do residente.");
                            Toast.makeText(
                                    this,
                                    R.string.msg_permissao_camera_necessaria,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    private final ActivityResultLauncher<String> salvarResumoPastaLocalLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/pdf"),
                    uriDestino -> {
                        if (uriDestino == null || arquivoResumoOfflineAtual == null || !arquivoResumoOfflineAtual.exists()) {
                            return;
                        }
                        try {
                            ResumoProntuarioPdfGenerator.copiarParaUriLocal(
                                    this,
                                    arquivoResumoOfflineAtual,
                                    uriDestino
                            );
                            Toast.makeText(this, R.string.sucesso_copia_salva_pasta, Toast.LENGTH_SHORT).show();
                            Snackbar.make(binding.getRoot(), R.string.sucesso_copia_salva_pasta, Snackbar.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.w(TAG, "Erro ao copiar resumo PDF para pasta local: " + e.getMessage());
                            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_LONG).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityDetalhesIdosoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarEdgeToEdgeInsets();

        idosoDAO = new IdosoDAO(this);
        medicamentoDAO = new MedicamentoDAO(this);

        if (!recuperarParametrosIntent()) {
            Toast.makeText(this, "Registro não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (savedInstanceState != null) {
            String caminhoTemp = savedInstanceState.getString(STATE_CAMINHO_ARQUIVO_TEMP, null);
            if (caminhoTemp != null && !caminhoTemp.isEmpty()) {
                arquivoFotoTemporario = new File(caminhoTemp);
            }
            caminhoFotoCapturadaSessao = savedInstanceState.getString(STATE_CAMINHO_FOTO_SESSAO, null);
        }

        configurarToolbar();
        configurarPermissoesPerfil();
        configurarBotoes();
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingBottomBase = binding.containerConteudoDetalhes.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDetalhesIdoso, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarDetalhes.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            binding.containerConteudoDetalhes.setPadding(
                    binding.containerConteudoDetalhes.getPaddingLeft(),
                    binding.containerConteudoDetalhes.getPaddingTop(),
                    binding.containerConteudoDetalhes.getPaddingRight(),
                    paddingBottomBase + systemBars.bottom
            );
            return windowInsets;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (arquivoFotoTemporario != null) {
            outState.putString(STATE_CAMINHO_ARQUIVO_TEMP, arquivoFotoTemporario.getAbsolutePath());
        }
        if (caminhoFotoCapturadaSessao != null) {
            outState.putString(STATE_CAMINHO_FOTO_SESSAO, caminhoFotoCapturadaSessao);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        perfilUsuario = com.example.util.PerfilManager.getPerfilAtual(this);
        configurarPermissoesPerfil();
        carregarDados();
    }

    @Override
    protected void onDestroy() {
        if (ultimaTarefaPdf != null && !ultimaTarefaPdf.isDone()) {
            ultimaTarefaPdf.cancel(true);
        }
        if (ultimaTarefaResumoOffline != null && !ultimaTarefaResumoOffline.isDone()) {
            ultimaTarefaResumoOffline.cancel(true);
        }
        pdfExecutor.shutdown();
        super.onDestroy();
    }

    /**
     * Recupera o identificador do banco SQLite, o objeto Idoso serializado
     * ou os parâmetros diretos (nomeIdoso e telefoneFamiliar) enviados via Intent.
     */
    private boolean recuperarParametrosIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }

        Bundle extras = intent.getExtras();
        if (extras != null && extras.containsKey(EXTRA_IDOSO_ID)) {
            Object rawId = extras.get(EXTRA_IDOSO_ID);
            if (rawId instanceof Number) {
                idosoId = ((Number) rawId).intValue();
            }
        }

        if (intent.hasExtra(MainActivity.EXTRA_PERFIL)) {
            perfilUsuario = intent.getStringExtra(MainActivity.EXTRA_PERFIL);
        }

        // Suporte ao objeto Idoso passado diretamente via Intent
        if (intent.hasExtra(EXTRA_IDOSO)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                idosoViaIntent = intent.getSerializableExtra(EXTRA_IDOSO, Idoso.class);
            } else {
                @SuppressWarnings("deprecation")
                Object extraObj = intent.getSerializableExtra(EXTRA_IDOSO);
                if (extraObj instanceof Idoso) {
                    idosoViaIntent = (Idoso) extraObj;
                }
            }
            if (idosoViaIntent != null && idosoId == -1 && idosoViaIntent.getId() > 0) {
                idosoId = idosoViaIntent.getId();
            }
        }

        // Suporte aos extras diretos nomeIdoso e telefoneFamiliar
        if (intent.hasExtra(EXTRA_NOME_IDOSO) || intent.hasExtra(EXTRA_TELEFONE_FAMILIAR)) {
            possuiExtrasDiretos = true;
            nomeIdosoIntent = intent.getStringExtra(EXTRA_NOME_IDOSO);
            telefoneFamiliarIntent = intent.getStringExtra(EXTRA_TELEFONE_FAMILIAR);
        }

        return idosoId != -1 || idosoViaIntent != null || possuiExtrasDiretos;
    }

    private void configurarToolbar() {
        binding.toolbarDetalhes.setNavigationOnClickListener(v -> finish());
    }

    private void configurarPermissoesPerfil() {
        PerfilUsuario perfil = SessaoManager.getPerfil(this);
        boolean podeEditar = PermissaoUtils.podeEditarIdoso(perfil);
        boolean podeExcluir = PermissaoUtils.podeExcluirIdoso(perfil);
        boolean podeGerarPdf = PermissaoUtils.podeGerarRelatorio(perfil);
        boolean podeEnviarWhatsApp = PermissaoUtils.podeEnviarMensagemWhatsApp(perfil);
        boolean podeAtualizarFoto = PermissaoUtils.podeAtualizarFotoPerfil(perfil);

        // 1. Oculta os botões de edição e exclusão de idoso para quem não for ADMINISTRADOR
        if (!podeEditar && !podeExcluir) {
            binding.layoutAcoesAdmin.setVisibility(View.GONE);
        } else {
            binding.layoutAcoesAdmin.setVisibility(View.VISIBLE);
            binding.btnEditarFicha.setVisibility(podeEditar ? View.VISIBLE : View.GONE);
            binding.btnEditarFicha.setEnabled(podeEditar);
            binding.btnExcluirFicha.setVisibility(podeExcluir ? View.VISIBLE : View.GONE);
            binding.btnExcluirFicha.setEnabled(podeExcluir);
        }

        // 2. Exportação e Compartilhamento da Ficha em PDF (ADMINISTRADOR e ENFERMEIRO)
        binding.layoutAcoesPdf.setVisibility(podeGerarPdf ? View.VISIBLE : View.GONE);
        binding.btnExportarPdf.setVisibility(podeGerarPdf ? View.VISIBLE : View.GONE);
        binding.btnExportarPdf.setEnabled(podeGerarPdf);
        binding.btnCompartilharPdf.setVisibility(podeGerarPdf ? View.VISIBLE : View.GONE);
        binding.btnCompartilharPdf.setEnabled(podeGerarPdf);

        // 3. Contato rápido via WhatsApp (ADMINISTRADOR, ENFERMEIRO e CUIDADOR)
        binding.btnWhatsappContato.setVisibility(podeEnviarWhatsApp ? View.VISIBLE : View.GONE);
        binding.btnWhatsappContato.setEnabled(podeEnviarWhatsApp);

        // 4. Captura unificada de foto de perfil via câmera (badge flutuante + toque na foto)
        binding.btnCameraBadgeDetalhe.setVisibility(podeAtualizarFoto ? View.VISIBLE : View.GONE);
        binding.btnCameraBadgeDetalhe.setEnabled(podeAtualizarFoto);
        binding.ivDetalheFoto.setClickable(podeAtualizarFoto);
        binding.ivDetalheFoto.setFocusable(podeAtualizarFoto);
    }

    private void carregarDados() {
        if (idosoId != -1) {
            idosoAtual = idosoDAO.buscarPorId(idosoId);
        }

        // Fallback para objeto Idoso ou extras diretos enviados via Intent
        if (idosoAtual == null && idosoViaIntent != null) {
            idosoAtual = idosoViaIntent;
        } else if (idosoAtual == null && possuiExtrasDiretos) {
            idosoAtual = new Idoso();
            idosoAtual.setNome(nomeIdosoIntent != null ? nomeIdosoIntent : "");
            idosoAtual.setContatoEmergencia(telefoneFamiliarIntent != null ? telefoneFamiliarIntent : "");
        }

        if (idosoAtual == null) {
            Toast.makeText(this, "Residente não encontrado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (idosoId == -1 && caminhoFotoCapturadaSessao != null && !caminhoFotoCapturadaSessao.isEmpty()) {
            idosoAtual.setCaminhoFoto(caminhoFotoCapturadaSessao);
        }

        // Foto de perfil com placeholder dinâmico e atualização do rótulo do botão
        atualizarExibicaoFotoPerfil(idosoAtual.getCaminhoFoto());

        // Nome
        binding.tvDetalheNome.setText(obterNomeResidenteAtual());

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

        // Alergias com contraste MD3 semântico (ErrorContainer para alergias ativas / SafeContainer quando livre)
        String alergias = idosoAtual.getAlergias();
        boolean possuiAlergiaRelevante = alergias != null
                && !alergias.trim().isEmpty()
                && !alergias.trim().toLowerCase(java.util.Locale.ROOT).contains("nenhuma");
        if (alergias != null && !alergias.trim().isEmpty()) {
            binding.tvDetalheAlergias.setText(alergias);
        } else {
            binding.tvDetalheAlergias.setText(R.string.nenhuma_alergia);
        }
        if (possuiAlergiaRelevante) {
            binding.tvDetalheAlergias.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_allergy_container))
            );
            binding.tvDetalheAlergias.setTextColor(ContextCompat.getColor(this, R.color.md3_on_allergy_container));
            binding.tvDetalheAlergias.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_warning, 0, 0, 0
            );
            TextViewCompat.setCompoundDrawableTintList(
                    binding.tvDetalheAlergias,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_allergy_icon))
            );
        } else {
            binding.tvDetalheAlergias.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_safe_container))
            );
            binding.tvDetalheAlergias.setTextColor(ContextCompat.getColor(this, R.color.md3_on_safe_container));
            binding.tvDetalheAlergias.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_check, 0, 0, 0
            );
            TextViewCompat.setCompoundDrawableTintList(
                    binding.tvDetalheAlergias,
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_on_safe_container))
            );
        }

        // Restrições Médicas
        String restricoes = idosoAtual.getRestricoesMedicas();
        if (restricoes != null && !restricoes.trim().isEmpty()) {
            binding.tvDetalheRestricoes.setText(restricoes);
        } else {
            binding.tvDetalheRestricoes.setText(R.string.nenhuma_restricao);
        }

        // Contato Familiar / Emergência
        String contato = obterTelefoneFamiliarAtual();
        if (contato != null && !contato.trim().isEmpty()) {
            binding.tvDetalheContatoEmergencia.setText(contato);
            binding.btnLigarContato.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetalheContatoEmergencia.setText(R.string.nenhum_contato);
            binding.btnLigarContato.setVisibility(View.GONE);
        }

        // Se está exibindo dados apenas via Intent sem ID persistido no SQLite, oculta ações dependentes de ID
        if (idosoId == -1) {
            binding.btnVerMedicamentos.setVisibility(View.GONE);
            binding.layoutAcoesAdmin.setVisibility(View.GONE);
        }

        atualizarStatusResumoPdfOffline();
    }

    private void atualizarStatusResumoPdfOffline() {
        arquivoResumoOfflineAtual = ResumoProntuarioPdfGenerator.obterResumoSalvoOffline(this, idosoAtual);
        if (arquivoResumoOfflineAtual != null && arquivoResumoOfflineAtual.exists()) {
            String dataFormatada = ResumoProntuarioPdfGenerator.formatarDataAtualizacao(
                    arquivoResumoOfflineAtual.lastModified()
            );
            String tamanhoFormatado = ResumoProntuarioPdfGenerator.formatarTamanhoArquivo(
                    arquivoResumoOfflineAtual.length()
            );
            binding.tvStatusResumoOffline.setText(
                    getString(R.string.status_resumo_salvo_offline, dataFormatada, tamanhoFormatado)
            );
            binding.tvStatusResumoOffline.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md3_safe_container))
            );
            binding.tvStatusResumoOffline.setTextColor(
                    ContextCompat.getColor(this, R.color.md3_on_safe_container)
            );
            binding.btnAbrirResumoOffline.setVisibility(View.VISIBLE);
            binding.btnSalvarPastaResumoPdf.setVisibility(View.VISIBLE);
        } else {
            binding.tvStatusResumoOffline.setText(R.string.status_resumo_nao_salvo);
            binding.tvStatusResumoOffline.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.m3_surface_container_low))
            );
            binding.tvStatusResumoOffline.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary)
            );
            binding.btnAbrirResumoOffline.setVisibility(View.GONE);
            binding.btnSalvarPastaResumoPdf.setVisibility(View.GONE);
        }
    }

    private void atualizarExibicaoFotoPerfil(String caminhoFoto) {
        ImageUtil.carregarFoto(binding.ivDetalheFoto, caminhoFoto);
        String descricaoAcao = ImageUtil.estaExibindoPlaceholder(binding.ivDetalheFoto)
                ? getString(R.string.btn_capturar_foto_perfil)
                : getString(R.string.btn_atualizar_foto_perfil);
        binding.btnCameraBadgeDetalhe.setContentDescription(descricaoAcao);
    }

    private void configurarBotoes() {
        // Ação unificada de captura de foto de perfil (toque direto na foto ou no botão flutuante sobreposto)
        View.OnClickListener acaoCapturarFoto = v -> solicitarCapturaFotoPerfil();
        binding.ivDetalheFoto.setOnClickListener(acaoCapturarFoto);
        binding.btnCameraBadgeDetalhe.setOnClickListener(acaoCapturarFoto);

        // Ação de discar para o contato de emergência via Intent nativa
        binding.btnLigarContato.setOnClickListener(v -> {
            String contato = obterTelefoneFamiliarAtual();
            String numeroLimpo = extrairNumeroTelefone(contato);
            if (!numeroLimpo.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + numeroLimpo));
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.erro_telefone_familiar_vazio, Toast.LENGTH_SHORT).show();
            }
        });

        // Ação rápida para contatar o familiar/responsável via WhatsApp
        binding.btnWhatsappContato.setOnClickListener(v -> contatarFamiliarViaWhatsApp());

        // Ação de Ver Medicamentos e Alarmes
        binding.btnVerMedicamentos.setOnClickListener(v -> {
            Intent intent = new Intent(DetalhesIdosoActivity.this, MedicamentoActivity.class);
            intent.putExtra(MedicamentoActivity.EXTRA_IDOSO_ID, idosoId);
            if (idosoAtual != null) {
                intent.putExtra(MedicamentoActivity.EXTRA_NOME_IDOSO, obterNomeResidenteAtual());
            }
            startActivity(intent);
        });

        // Resumo do Prontuário em PDF para Consulta Offline no Armazenamento Local
        binding.btnGerarResumoPdf.setOnClickListener(v -> gerarESalvarResumoProntuarioOffline());
        binding.btnAbrirResumoOffline.setOnClickListener(v -> {
            if (arquivoResumoOfflineAtual != null && arquivoResumoOfflineAtual.exists()) {
                abrirVisualizadorResumoOffline(arquivoResumoOfflineAtual);
            } else {
                gerarESalvarResumoProntuarioOffline();
            }
        });
        binding.btnSalvarPastaResumoPdf.setOnClickListener(v -> {
            if (arquivoResumoOfflineAtual != null && arquivoResumoOfflineAtual.exists()) {
                salvarResumoPastaLocalLauncher.launch(arquivoResumoOfflineAtual.getName());
            } else {
                gerarESalvarResumoProntuarioOffline();
            }
        });

        // Manipulador de clique para "Exportar Ficha em PDF"
        binding.btnExportarPdf.setOnClickListener(v -> gerarEExpedirProntuarioPdf(false));

        // Manipulador de clique para compartilhamento direto (WhatsApp / E-mail via ACTION_SEND)
        binding.btnCompartilharPdf.setOnClickListener(v -> gerarEExpedirProntuarioPdf(true));

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

    private void solicitarCapturaFotoPerfil() {
        if (!PermissaoUtils.podeAtualizarFotoPerfil(this)) {
            Toast.makeText(this, "Sem permissão para atualizar foto de perfil.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarCapturaCameraPerfil();
        } else {
            permissaoCameraPerfilLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void iniciarCapturaCameraPerfil() {
        try {
            arquivoFotoTemporario = ImageUtil.criarArquivoTemporarioCaptura(this);
            Intent intentCamera = ImageUtil.criarIntentCapturaCamera(this, arquivoFotoTemporario);
            cameraPerfilLauncher.launch(intentCamera);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Nenhum aplicativo de câmera disponível no dispositivo.");
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
            arquivoFotoTemporario = null;
            Toast.makeText(this, R.string.erro_camera_indisponivel, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível iniciar a captura de foto: " + e.getMessage());
            ImageUtil.removerArquivoSilenciosamente(arquivoFotoTemporario);
            arquivoFotoTemporario = null;
            Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void processarResultadoCameraPerfil(ActivityResult resultado) {
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

        if (novoCaminhoPrivado == null) {
            Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
            return;
        }

        String caminhoAnterior = (idosoAtual != null) ? idosoAtual.getCaminhoFoto() : null;

        try {
            if (idosoId != -1) {
                int linhas = idosoDAO.atualizarFotoPerfil(idosoId, novoCaminhoPrivado);
                if (linhas <= 0) {
                    ImageUtil.removerFotoPrivadaSeExistir(this, novoCaminhoPrivado);
                    Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            caminhoFotoCapturadaSessao = novoCaminhoPrivado;
            if (idosoAtual != null) {
                idosoAtual.setCaminhoFoto(novoCaminhoPrivado);
            }
            if (idosoViaIntent != null) {
                idosoViaIntent.setCaminhoFoto(novoCaminhoPrivado);
            }

            if (caminhoAnterior != null && !caminhoAnterior.equals(novoCaminhoPrivado)) {
                ImageUtil.removerFotoPrivadaSeExistir(this, caminhoAnterior);
            }

            atualizarExibicaoFotoPerfil(novoCaminhoPrivado);
            setResult(RESULT_OK);
            Toast.makeText(this, R.string.sucesso_foto_perfil_atualizada, Toast.LENGTH_SHORT).show();
        } catch (AcessoNaoAutorizadoException ex) {
            ImageUtil.removerFotoPrivadaSeExistir(this, novoCaminhoPrivado);
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void gerarESalvarResumoProntuarioOffline() {
        if (idosoAtual == null && idosoId == -1) {
            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnGerarResumoPdf.setEnabled(false);
        Toast.makeText(this, R.string.gerando_resumo_pdf, Toast.LENGTH_SHORT).show();

        final int idConsulta = idosoId;
        final Idoso fallbackIdoso = idosoAtual;

        ultimaTarefaResumoOffline = pdfExecutor.submit(() -> {
            try {
                Idoso idosoParaResumo = (idConsulta != -1) ? idosoDAO.buscarPorId(idConsulta) : fallbackIdoso;
                if (idosoParaResumo == null) {
                    idosoParaResumo = fallbackIdoso;
                }
                if (idosoParaResumo == null) {
                    throw new IllegalStateException("Dados do residente indisponíveis para gerar resumo.");
                }

                List<Medicamento> prescricoes = (idConsulta != -1)
                        ? medicamentoDAO.listarPorIdoso(idConsulta)
                        : Collections.emptyList();

                final File arquivoGerado = ResumoProntuarioPdfGenerator.gerarESalvarResumoOffline(
                        getApplicationContext(),
                        idosoParaResumo,
                        prescricoes
                );
                final String nomeResidente = idosoParaResumo.getNome();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnGerarResumoPdf.setEnabled(true);
                    arquivoResumoOfflineAtual = arquivoGerado;
                    atualizarStatusResumoPdfOffline();

                    Toast.makeText(
                            DetalhesIdosoActivity.this,
                            R.string.sucesso_resumo_pdf_offline,
                            Toast.LENGTH_SHORT
                    ).show();

                    Snackbar.make(
                            binding.getRoot(),
                            R.string.sucesso_resumo_pdf_offline,
                            Snackbar.LENGTH_LONG
                    ).setAction(R.string.acao_consultar_offline, view ->
                            abrirVisualizadorResumoOffline(arquivoGerado)
                    ).show();

                    Intent intentVisualizador = new Intent(DetalhesIdosoActivity.this, VisualizadorPdfOfflineActivity.class);
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
                Log.e(TAG, "Erro ao gerar e salvar resumo em PDF offline", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnGerarResumoPdf.setEnabled(true);
                    Toast.makeText(
                            DetalhesIdosoActivity.this,
                            R.string.erro_gerar_resumo_pdf,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void abrirVisualizadorResumoOffline(File arquivoResumo) {
        if (arquivoResumo == null || !arquivoResumo.exists()) {
            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, VisualizadorPdfOfflineActivity.class);
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_CAMINHO_ARQUIVO_PDF, arquivoResumo.getAbsolutePath());
        intent.putExtra(VisualizadorPdfOfflineActivity.EXTRA_NOME_RESIDENTE, obterNomeResidenteAtual());
        startActivity(intent);
    }

    private void gerarEExpedirProntuarioPdf(boolean compartilharDiretamente) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(this);
        if (!PermissaoUtils.podeGerarRelatorio(perfilAtivo)) {
            Toast.makeText(this, "Seu perfil atual não possui permissão para exportar prontuários em PDF.", Toast.LENGTH_LONG).show();
            return;
        }

        if (idosoAtual == null && idosoId == -1) {
            Toast.makeText(this, "Residente não encontrado para emissão do prontuário.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnExportarPdf.setEnabled(false);
        binding.btnCompartilharPdf.setEnabled(false);
        Toast.makeText(this, R.string.gerando_pdf, Toast.LENGTH_SHORT).show();

        final int idConsulta = idosoId;
        final Idoso fallbackIdoso = idosoAtual;

        ultimaTarefaPdf = pdfExecutor.submit(() -> {
            try {
                Idoso idosoParaPdf = (idConsulta != -1) ? idosoDAO.buscarPorId(idConsulta) : fallbackIdoso;
                if (idosoParaPdf == null) {
                    idosoParaPdf = fallbackIdoso;
                }
                if (idosoParaPdf == null) {
                    throw new IllegalStateException("Dados do residente indisponíveis no banco SQLite.");
                }

                List<Medicamento> prescricoes = (idConsulta != -1)
                        ? medicamentoDAO.listarPorIdoso(idConsulta)
                        : Collections.emptyList();

                final File arquivoPdf = FichaIdosoPdfGenerator.gerarProntuarioPdf(
                        getApplicationContext(),
                        idosoParaPdf,
                        prescricoes
                );
                final String nomeResidente = idosoParaPdf.getNome();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnExportarPdf.setEnabled(true);
                    binding.btnCompartilharPdf.setEnabled(true);

                    Toast.makeText(
                            DetalhesIdosoActivity.this,
                            R.string.sucesso_ficha_pdf_salva,
                            Toast.LENGTH_SHORT
                    ).show();

                    Snackbar.make(
                            binding.getRoot(),
                            R.string.sucesso_ficha_pdf_salva,
                            Snackbar.LENGTH_LONG
                    ).setAction(R.string.acao_compartilhar_pdf, view ->
                            FichaIdosoPdfGenerator.compartilharPdf(
                                    DetalhesIdosoActivity.this,
                                    arquivoPdf,
                                    nomeResidente
                            )
                    ).show();

                    if (compartilharDiretamente) {
                        FichaIdosoPdfGenerator.compartilharPdf(DetalhesIdosoActivity.this, arquivoPdf, nomeResidente);
                    } else {
                        FichaIdosoPdfGenerator.abrirPdf(DetalhesIdosoActivity.this, arquivoPdf);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao gerar e salvar prontuário em PDF", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.btnExportarPdf.setEnabled(true);
                    binding.btnCompartilharPdf.setEnabled(true);
                    Toast.makeText(
                            DetalhesIdosoActivity.this,
                            R.string.erro_gerar_ficha_pdf,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void contatarFamiliarViaWhatsApp() {
        String telefoneComDdi = "";
        String mensagemPadrao = "";
        try {
            String telefoneBruto = obterTelefoneFamiliarAtual();
            if (telefoneBruto == null || telefoneBruto.trim().isEmpty()) {
                Toast.makeText(this, R.string.erro_telefone_familiar_vazio, Toast.LENGTH_LONG).show();
                return;
            }

            telefoneComDdi = higienizarTelefoneComDdiBrasil(telefoneBruto);
            if (telefoneComDdi.isEmpty()) {
                Toast.makeText(this, R.string.erro_telefone_familiar_invalido, Toast.LENGTH_LONG).show();
                return;
            }

            String nomeResidente = obterNomeResidenteAtual();
            String nomeExibicao = (!nomeResidente.isEmpty())
                    ? nomeResidente
                    : getString(R.string.residente_padrao);
            mensagemPadrao = getString(R.string.msg_padrao_whatsapp_familiar, nomeExibicao);
            String urlWhatsApp = montarUrlApiWhatsApp(telefoneComDdi, nomeResidente);

            Intent intentWhatsApp = new Intent(Intent.ACTION_VIEW, Uri.parse(urlWhatsApp));
            intentWhatsApp.setPackage(PACKAGE_WHATSAPP);

            try {
                startActivity(intentWhatsApp);
            } catch (ActivityNotFoundException exPadrao) {
                Intent intentWhatsAppBusiness = new Intent(Intent.ACTION_VIEW, Uri.parse(urlWhatsApp));
                intentWhatsAppBusiness.setPackage(PACKAGE_WHATSAPP_BUSINESS);
                startActivity(intentWhatsAppBusiness);
            }
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "Aplicativo WhatsApp não instalado no dispositivo; exibindo alternativas de contato.");
            Toast.makeText(this, R.string.erro_whatsapp_nao_instalado, Toast.LENGTH_LONG).show();
            WhatsAppUtils.exibirAlternativasSemWhatsApp(this, telefoneComDdi, mensagemPadrao);
        } catch (Exception e) {
            Log.w(TAG, "Erro inesperado ao tentar abrir o WhatsApp: " + e.getMessage());
            Toast.makeText(this, R.string.erro_abrir_whatsapp_generico, Toast.LENGTH_LONG).show();
        }
    }

    public static String higienizarTelefoneComDdiBrasil(String telefoneBruto) {
        if (telefoneBruto == null || telefoneBruto.trim().isEmpty()) {
            return "";
        }

        String apenasDigitos = telefoneBruto.replaceAll("[^0-9]", "");

        if (apenasDigitos.startsWith("0") && (apenasDigitos.length() == 11 || apenasDigitos.length() == 12)) {
            apenasDigitos = apenasDigitos.substring(1);
        }

        if (apenasDigitos.length() < 10) {
            return "";
        }

        if (apenasDigitos.startsWith("55") && (apenasDigitos.length() == 12 || apenasDigitos.length() == 13)) {
            return apenasDigitos;
        }

        return "55" + apenasDigitos;
    }

    public String montarUrlApiWhatsApp(String telefoneComDdi, String nomeResidente) throws UnsupportedEncodingException {
        String nomeExibicao = (nomeResidente != null && !nomeResidente.trim().isEmpty())
                ? nomeResidente.trim()
                : getString(R.string.residente_padrao);

        String mensagemPadrao = getString(R.string.msg_padrao_whatsapp_familiar, nomeExibicao);
        String mensagemCodificada = URLEncoder.encode(mensagemPadrao, StandardCharsets.UTF_8.name());

        return WHATSAPP_API_BASE_URL + telefoneComDdi + "&text=" + mensagemCodificada;
    }

    private String obterNomeResidenteAtual() {
        if (idosoAtual != null && idosoAtual.getNome() != null && !idosoAtual.getNome().trim().isEmpty()) {
            return idosoAtual.getNome().trim();
        }
        if (nomeIdosoIntent != null && !nomeIdosoIntent.trim().isEmpty()) {
            return nomeIdosoIntent.trim();
        }
        return "";
    }

    private String obterTelefoneFamiliarAtual() {
        if (idosoAtual != null && idosoAtual.getContatoEmergencia() != null
                && !idosoAtual.getContatoEmergencia().trim().isEmpty()) {
            return idosoAtual.getContatoEmergencia().trim();
        }
        if (telefoneFamiliarIntent != null && !telefoneFamiliarIntent.trim().isEmpty()) {
            return telefoneFamiliarIntent.trim();
        }
        return null;
    }

    private void confirmarExclusao() {
        if (idosoAtual == null) return;

        final String caminhoFotoResidente = idosoAtual.getCaminhoFoto();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirmar_exclusao_titulo)
                .setMessage(getString(R.string.confirmar_exclusao_msg, obterNomeResidenteAtual()))
                .setPositiveButton(R.string.excluir, (dialog, which) -> {
                    try {
                        int linhas = idosoDAO.excluir(idosoId);
                        if (linhas > 0) {
                            ImageUtil.removerFotoPrivadaSeExistir(DetalhesIdosoActivity.this, caminhoFotoResidente);
                            Toast.makeText(DetalhesIdosoActivity.this, getString(R.string.sucesso_excluido), Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(DetalhesIdosoActivity.this, "Erro ao remover registro.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (AcessoNaoAutorizadoException ex) {
                        Toast.makeText(DetalhesIdosoActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    private String extrairNumeroTelefone(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[^0-9+]", "");
    }
}
