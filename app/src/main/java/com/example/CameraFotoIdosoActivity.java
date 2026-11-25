package com.example;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.databinding.ActivityCameraFotoIdosoBinding;
import com.example.util.ImageUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela nativa de captura de foto de perfil dos residentes utilizando CameraX
 * (PreviewView + ProcessCameraProvider + ImageCapture).
 *
 * Permite alternar câmera frontal/traseira, controlar flash, revisar a foto capturada
 * e gravar a imagem final corrigida por EXIF no armazenamento privado do aplicativo.
 */
public class CameraFotoIdosoActivity extends AppCompatActivity {

    private static final String TAG = "CameraFotoIdoso";

    public static final String EXTRA_NOME_RESIDENTE = "EXTRA_NOME_RESIDENTE";
    public static final String EXTRA_CAMINHO_FOTO_SALVA = "EXTRA_CAMINHO_FOTO_SALVA";

    private static final String STATE_LENS_FACING = "state_lens_facing";
    private static final String STATE_FLASH_MODE = "state_flash_mode";
    private static final String STATE_ARQUIVO_TEMP = "state_arquivo_temp";

    private ActivityCameraFotoIdosoBinding binding;
    private ExecutorService cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera cameraAtiva;

    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private boolean capturaEmAndamento = false;
    private File arquivoCapturaTemporario = null;

    private final ActivityResultLauncher<String> permissaoCameraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
                if (Boolean.TRUE.equals(concedida)) {
                    ocultarEstadoErroOuPermissao();
                    inicializarCameraX();
                } else {
                    Log.i(TAG, "Permissão CAMERA negada na tela de captura CameraX.");
                    exibirEstadoPermissaoNegada();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityCameraFotoIdosoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (savedInstanceState != null) {
            lensFacing = savedInstanceState.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK);
            flashMode = savedInstanceState.getInt(STATE_FLASH_MODE, ImageCapture.FLASH_MODE_OFF);
            String caminhoTemp = savedInstanceState.getString(STATE_ARQUIVO_TEMP, null);
            if (caminhoTemp != null && !caminhoTemp.isEmpty()) {
                File candidato = new File(caminhoTemp);
                if (candidato.exists() && candidato.length() > 0) {
                    arquivoCapturaTemporario = candidato;
                }
            }
        }

        configurarEdgeToEdgeInsets();
        configurarContextoResidente();
        configurarControles();
        atualizarIndicadorFlash();

        if (arquivoCapturaTemporario != null && arquivoCapturaTemporario.exists() && arquivoCapturaTemporario.length() > 0) {
            exibirRevisaoFotoCapturada(arquivoCapturaTemporario);
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            inicializarCameraX();
        } else {
            exibirEstadoPermissaoNegada();
            permissaoCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_LENS_FACING, lensFacing);
        outState.putInt(STATE_FLASH_MODE, flashMode);
        if (arquivoCapturaTemporario != null) {
            outState.putString(STATE_ARQUIVO_TEMP, arquivoCapturaTemporario.getAbsolutePath());
        }
    }

    @Override
    protected void onDestroy() {
        if (!isChangingConfigurations()) {
            ImageUtil.removerArquivoSilenciosamente(arquivoCapturaTemporario);
            arquivoCapturaTemporario = null;
        }
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }

    private void configurarEdgeToEdgeInsets() {
        final int topBase = binding.layoutTopoCamera.getPaddingTop();
        final int bottomCapturaBase = binding.layoutControlesCaptura.getPaddingBottom();
        final int bottomConfirmacaoBase = binding.layoutAcoesConfirmacao.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCameraFoto, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.layoutTopoCamera.setPadding(
                    binding.layoutTopoCamera.getPaddingLeft() + systemBars.left,
                    topBase + systemBars.top,
                    binding.layoutTopoCamera.getPaddingRight() + systemBars.right,
                    binding.layoutTopoCamera.getPaddingBottom()
            );
            binding.layoutControlesCaptura.setPadding(
                    binding.layoutControlesCaptura.getPaddingLeft(),
                    binding.layoutControlesCaptura.getPaddingTop(),
                    binding.layoutControlesCaptura.getPaddingRight(),
                    bottomCapturaBase + systemBars.bottom
            );
            binding.layoutAcoesConfirmacao.setPadding(
                    binding.layoutAcoesConfirmacao.getPaddingLeft(),
                    binding.layoutAcoesConfirmacao.getPaddingTop(),
                    binding.layoutAcoesConfirmacao.getPaddingRight(),
                    bottomConfirmacaoBase + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void configurarContextoResidente() {
        String nomeResidente = getIntent() != null ? getIntent().getStringExtra(EXTRA_NOME_RESIDENTE) : null;
        if (nomeResidente != null && !nomeResidente.trim().isEmpty()) {
            binding.tvSubtituloResidenteCamera.setText(getString(R.string.rotulo_responsavel_residente, nomeResidente.trim()));
        } else {
            binding.tvSubtituloResidenteCamera.setText(R.string.subtitulo_camera_padrao);
        }
    }

    private void configurarControles() {
        binding.btnFecharCamera.setOnClickListener(v -> cancelarCapturaESair());
        binding.btnFecharEstadoCamera.setOnClickListener(v -> cancelarCapturaESair());

        binding.btnSolicitarPermissaoCamera.setOnClickListener(v ->
                permissaoCameraLauncher.launch(Manifest.permission.CAMERA)
        );

        binding.btnAlternarFlash.setOnClickListener(v -> alternarModoFlash());
        binding.btnAlternarCamera.setOnClickListener(v -> alternarCameraFrontalTraseira());
        binding.btnDispararCaptura.setOnClickListener(v -> capturarFoto());

        binding.btnRefazerFoto.setOnClickListener(v -> refazerFoto());
        binding.btnConfirmarFoto.setOnClickListener(v -> confirmarFotoCapturada());
    }

    private void inicializarCameraX() {
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                try {
                    cameraProvider = cameraProviderFuture.get();
                    vincularCasosDeUsoCamera();
                } catch (Exception e) {
                    Log.w(TAG, "Não foi possível obter ProcessCameraProvider: " + e.getMessage());
                    exibirEstadoCameraIndisponivel();
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.w(TAG, "CameraX indisponível neste ambiente: " + e.getMessage());
            exibirEstadoCameraIndisponivel();
        }
    }

    private void vincularCasosDeUsoCamera() {
        if (cameraProvider == null) {
            exibirEstadoCameraIndisponivel();
            return;
        }

        CameraSelector seletorSelecionado = resolverSeletorDisponivel();
        if (seletorSelecionado == null) {
            exibirEstadoCameraIndisponivel();
            return;
        }

        int rotacaoAlvo = Surface.ROTATION_0;
        Display display = binding.previewViewCamera.getDisplay();
        if (display != null) {
            rotacaoAlvo = display.getRotation();
        }

        Preview preview = new Preview.Builder()
                .setTargetRotation(rotacaoAlvo)
                .build();
        preview.setSurfaceProvider(binding.previewViewCamera.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .setTargetRotation(rotacaoAlvo)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraAtiva = cameraProvider.bindToLifecycle(this, seletorSelecionado, preview, imageCapture);
            ocultarEstadoErroOuPermissao();
            atualizarVisibilidadeBotaoFlash();
            atualizarBotaoAlternarCamera();
        } catch (Exception e) {
            Log.w(TAG, "Falha ao vincular casos de uso da CameraX: " + e.getMessage());
            exibirEstadoCameraIndisponivel();
        }
    }

    private CameraSelector resolverSeletorDisponivel() {
        CameraSelector preferido = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();
        try {
            if (cameraProvider.hasCamera(preferido)) {
                return preferido;
            }
            int alternativa = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            CameraSelector seletorAlternativo = new CameraSelector.Builder()
                    .requireLensFacing(alternativa)
                    .build();
            if (cameraProvider.hasCamera(seletorAlternativo)) {
                lensFacing = alternativa;
                return seletorAlternativo;
            }
        } catch (CameraInfoUnavailableException e) {
            Log.w(TAG, "Informações de câmera indisponíveis: " + e.getMessage());
        }
        return null;
    }

    private void atualizarBotaoAlternarCamera() {
        if (cameraProvider == null) {
            binding.btnAlternarCamera.setEnabled(false);
            return;
        }
        try {
            boolean temTraseira = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
            boolean temFrontal = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
            binding.btnAlternarCamera.setEnabled(temTraseira && temFrontal);
            binding.btnAlternarCamera.setAlpha((temTraseira && temFrontal) ? 1.0f : 0.45f);
        } catch (CameraInfoUnavailableException e) {
            binding.btnAlternarCamera.setEnabled(false);
        }
    }

    private void atualizarVisibilidadeBotaoFlash() {
        boolean possuiFlash = cameraAtiva != null && cameraAtiva.getCameraInfo().hasFlashUnit();
        binding.btnAlternarFlash.setVisibility(possuiFlash ? View.VISIBLE : View.INVISIBLE);
        binding.btnAlternarFlash.setEnabled(possuiFlash);
    }

    private void alternarCameraFrontalTraseira() {
        if (capturaEmAndamento) {
            return;
        }
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        vincularCasosDeUsoCamera();
    }

    private void alternarModoFlash() {
        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
        } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            flashMode = ImageCapture.FLASH_MODE_ON;
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF;
        }
        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }
        atualizarIndicadorFlash();
    }

    private void atualizarIndicadorFlash() {
        if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            binding.btnAlternarFlash.setImageResource(R.drawable.ic_flash_auto);
            binding.btnAlternarFlash.setContentDescription(getString(R.string.desc_flash_auto));
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            binding.btnAlternarFlash.setImageResource(R.drawable.ic_flash_on);
            binding.btnAlternarFlash.setContentDescription(getString(R.string.desc_flash_ligado));
        } else {
            binding.btnAlternarFlash.setImageResource(R.drawable.ic_flash_off);
            binding.btnAlternarFlash.setContentDescription(getString(R.string.desc_flash_desligado));
        }
    }

    private void capturarFoto() {
        if (imageCapture == null || capturaEmAndamento) {
            return;
        }

        File destinoTemp;
        try {
            ImageUtil.removerArquivoSilenciosamente(arquivoCapturaTemporario);
            destinoTemp = ImageUtil.criarArquivoTemporarioCaptura(this);
            arquivoCapturaTemporario = destinoTemp;
        } catch (Exception e) {
            Log.w(TAG, "Falha ao criar arquivo temporário para CameraX: " + e.getMessage());
            Toast.makeText(this, R.string.msg_erro_captura_camerax, Toast.LENGTH_SHORT).show();
            return;
        }

        capturaEmAndamento = true;
        binding.btnDispararCaptura.setEnabled(false);
        binding.progressCapturaFoto.setVisibility(View.VISIBLE);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(destinoTemp).build();

        imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(() -> {
                            capturaEmAndamento = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            binding.btnDispararCaptura.setEnabled(true);
                            binding.progressCapturaFoto.setVisibility(View.GONE);
                            exibirRevisaoFotoCapturada(destinoTemp);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.w(TAG, "Erro na captura CameraX: " + exception.getMessage());
                        ImageUtil.removerArquivoSilenciosamente(destinoTemp);
                        runOnUiThread(() -> {
                            capturaEmAndamento = false;
                            arquivoCapturaTemporario = null;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            binding.btnDispararCaptura.setEnabled(true);
                            binding.progressCapturaFoto.setVisibility(View.GONE);
                            Toast.makeText(
                                    CameraFotoIdosoActivity.this,
                                    R.string.msg_erro_captura_camerax,
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );
    }

    @VisibleForTesting
    void exibirRevisaoFotoCapturada(File arquivoFoto) {
        if (arquivoFoto == null || !arquivoFoto.exists() || arquivoFoto.length() <= 0) {
            return;
        }
        arquivoCapturaTemporario = arquivoFoto;
        ImageUtil.carregarFoto(binding.ivPreviewCapturaConfirmacao, arquivoFoto.getAbsolutePath());
        binding.layoutConfirmacaoFoto.setVisibility(View.VISIBLE);
    }

    private void refazerFoto() {
        ImageUtil.removerArquivoSilenciosamente(arquivoCapturaTemporario);
        arquivoCapturaTemporario = null;
        binding.ivPreviewCapturaConfirmacao.setImageDrawable(null);
        binding.layoutConfirmacaoFoto.setVisibility(View.GONE);
    }

    private void confirmarFotoCapturada() {
        final File arquivoParaPersistir = arquivoCapturaTemporario;
        if (arquivoParaPersistir == null || !arquivoParaPersistir.exists() || arquivoParaPersistir.length() <= 0) {
            Toast.makeText(this, R.string.erro_salvar_foto_camera, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnConfirmarFoto.setEnabled(false);
        binding.btnRefazerFoto.setEnabled(false);

        cameraExecutor.execute(() -> {
            String caminhoPrivadoFinal = ImageUtil.salvarFotoCapturadaNoArmazenamentoPrivado(
                    getApplicationContext(),
                    arquivoParaPersistir
            );
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                arquivoCapturaTemporario = null;
                if (caminhoPrivadoFinal != null && !caminhoPrivadoFinal.isEmpty()) {
                    Intent resultado = new Intent();
                    resultado.putExtra(EXTRA_CAMINHO_FOTO_SALVA, caminhoPrivadoFinal);
                    setResult(RESULT_OK, resultado);
                    finish();
                } else {
                    binding.btnConfirmarFoto.setEnabled(true);
                    binding.btnRefazerFoto.setEnabled(true);
                    Toast.makeText(
                            CameraFotoIdosoActivity.this,
                            R.string.erro_salvar_foto_camera,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });
    }

    private void cancelarCapturaESair() {
        ImageUtil.removerArquivoSilenciosamente(arquivoCapturaTemporario);
        arquivoCapturaTemporario = null;
        setResult(RESULT_CANCELED);
        finish();
    }

    private void exibirEstadoPermissaoNegada() {
        binding.layoutEstadoCamera.setVisibility(View.VISIBLE);
        binding.tvMensagemEstadoCamera.setText(R.string.msg_permissao_camera_necessaria);
        binding.btnSolicitarPermissaoCamera.setVisibility(View.VISIBLE);
        binding.btnDispararCaptura.setEnabled(false);
    }

    private void exibirEstadoCameraIndisponivel() {
        binding.layoutEstadoCamera.setVisibility(View.VISIBLE);
        binding.tvMensagemEstadoCamera.setText(R.string.msg_camera_x_indisponivel);
        binding.btnSolicitarPermissaoCamera.setVisibility(View.GONE);
        binding.btnDispararCaptura.setEnabled(false);
    }

    private void ocultarEstadoErroOuPermissao() {
        binding.layoutEstadoCamera.setVisibility(View.GONE);
        binding.btnDispararCaptura.setEnabled(true);
    }
}
