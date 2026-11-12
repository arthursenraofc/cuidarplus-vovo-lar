package com.example;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.databinding.ActivityVisualizadorPdfOfflineBinding;
import com.example.util.FichaIdosoPdfGenerator;
import com.example.util.ResumoProntuarioPdfGenerator;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

/**
 * Visualizador 100% nativo e offline de resumos em PDF salvos no armazenamento local
 * utilizando {@link PdfRenderer}, permitindo consulta sem internet ou aplicativos externos,
 * além de exportação para a pasta Downloads ou qualquer diretório escolhido pelo usuário (SAF).
 */
public class VisualizadorPdfOfflineActivity extends AppCompatActivity {

    private static final String TAG = "VisualizadorPdfOffline";

    public static final String EXTRA_CAMINHO_ARQUIVO_PDF = "EXTRA_CAMINHO_ARQUIVO_PDF";
    public static final String EXTRA_NOME_RESIDENTE = "EXTRA_NOME_RESIDENTE";

    private ActivityVisualizadorPdfOfflineBinding binding;
    private File arquivoPdfAtual;
    private String nomeResidente = "";

    private final ActivityResultLauncher<String> escolherPastaLocalLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/pdf"),
                    uriDestino -> {
                        if (uriDestino == null || arquivoPdfAtual == null) {
                            return;
                        }
                        try {
                            ResumoProntuarioPdfGenerator.copiarParaUriLocal(this, arquivoPdfAtual, uriDestino);
                            Snackbar.make(
                                    binding.getRoot(),
                                    R.string.sucesso_copia_salva_pasta,
                                    Snackbar.LENGTH_LONG
                            ).show();
                        } catch (Exception e) {
                            Log.w(TAG, "Falha ao gravar cópia na pasta escolhida: " + e.getMessage());
                            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_LONG).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityVisualizadorPdfOfflineBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarEdgeToEdgeInsets();

        String caminho = getIntent().getStringExtra(EXTRA_CAMINHO_ARQUIVO_PDF);
        String nomeExtra = getIntent().getStringExtra(EXTRA_NOME_RESIDENTE);
        nomeResidente = (nomeExtra != null && !nomeExtra.trim().isEmpty())
                ? nomeExtra.trim()
                : getString(R.string.residente_padrao);

        if (caminho == null || caminho.trim().isEmpty()) {
            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        arquivoPdfAtual = new File(caminho);
        if (!arquivoPdfAtual.exists() || arquivoPdfAtual.length() <= 0) {
            Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        configurarToolbar();
        preencherMetadadosLocais();
        configurarAcoesSalvamento();
        renderizarPaginasPdfOffline();
    }

    private void configurarEdgeToEdgeInsets() {
        final int paddingBottomBase = binding.containerConteudoVisualizador.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootVisualizadorPdf, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbarVisualizadorPdf.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            binding.containerConteudoVisualizador.setPadding(
                    binding.containerConteudoVisualizador.getPaddingLeft(),
                    binding.containerConteudoVisualizador.getPaddingTop(),
                    binding.containerConteudoVisualizador.getPaddingRight(),
                    paddingBottomBase + systemBars.bottom
            );
            return windowInsets;
        });
    }

    private void configurarToolbar() {
        binding.toolbarVisualizadorPdf.setNavigationOnClickListener(v -> finish());
    }

    private void preencherMetadadosLocais() {
        binding.tvNomeResidentePdf.setText(nomeResidente);
        String tamanho = ResumoProntuarioPdfGenerator.formatarTamanhoArquivo(arquivoPdfAtual.length());
        String dataSalvo = ResumoProntuarioPdfGenerator.formatarDataAtualizacao(arquivoPdfAtual.lastModified());
        binding.tvMetadadosArquivoPdf.setText(
                getString(R.string.formato_metadados_pdf_offline, arquivoPdfAtual.getName(), tamanho, dataSalvo)
        );
    }

    private void configurarAcoesSalvamento() {
        binding.btnSalvarDownloadsRapido.setOnClickListener(v -> {
            try {
                String nomeSalvo = ResumoProntuarioPdfGenerator.salvarCopiaEmDownloads(this, arquivoPdfAtual);
                String msg = getString(R.string.sucesso_copia_salva_downloads, nomeSalvo);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao salvar cópia em Downloads: " + e.getMessage());
                Toast.makeText(this, R.string.erro_gerar_resumo_pdf, Toast.LENGTH_LONG).show();
            }
        });

        binding.btnEscolherPastaLocal.setOnClickListener(v ->
                escolherPastaLocalLauncher.launch(arquivoPdfAtual.getName())
        );

        binding.btnCompartilharResumoViewer.setOnClickListener(v ->
                FichaIdosoPdfGenerator.compartilharPdf(this, arquivoPdfAtual, nomeResidente)
        );

        binding.btnAbrirLeitorExterno.setOnClickListener(v ->
                FichaIdosoPdfGenerator.abrirPdf(this, arquivoPdfAtual)
        );
    }

    private void renderizarPaginasPdfOffline() {
        binding.containerPaginasPdf.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int larguraTelaUtil = Math.max(
                595,
                getResources().getDisplayMetrics().widthPixels - Math.round(32f * density)
        );

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                arquivoPdfAtual,
                ParcelFileDescriptor.MODE_READ_ONLY
        );
             PdfRenderer renderer = new PdfRenderer(pfd)) {

            int totalPaginas = renderer.getPageCount();
            if (totalPaginas <= 0) {
                exibirAvisoFallback();
                return;
            }

            for (int i = 0; i < totalPaginas; i++) {
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    int bmpWidth = Math.max(page.getWidth() * 2, larguraTelaUtil);
                    int bmpHeight = Math.round((float) bmpWidth * page.getHeight() / page.getWidth());

                    Bitmap bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    MaterialCardView cardPagina = new MaterialCardView(this);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.bottomMargin = Math.round(16f * density);
                    cardPagina.setLayoutParams(cardParams);
                    cardPagina.setRadius(12f * density);
                    cardPagina.setCardElevation(2f * density);
                    cardPagina.setCardBackgroundColor(Color.WHITE);
                    cardPagina.setStrokeWidth(Math.round(1f * density));
                    cardPagina.setStrokeColor(ContextCompat.getColor(this, R.color.divider));

                    ImageView ivPagina = new ImageView(this);
                    ivPagina.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                    ivPagina.setAdjustViewBounds(true);
                    ivPagina.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ivPagina.setContentDescription(getString(R.string.desc_pagina_pdf_renderizada));
                    ivPagina.setImageBitmap(bitmap);

                    cardPagina.addView(ivPagina);
                    binding.containerPaginasPdf.addView(cardPagina);
                }
            }
            binding.tvAvisoFallbackPdf.setVisibility(View.GONE);
        } catch (Throwable t) {
            Log.i(TAG, "Renderizador nativo indisponível neste ambiente; exibindo metadados locais.");
            exibirAvisoFallback();
        }
    }

    private void exibirAvisoFallback() {
        binding.tvAvisoFallbackPdf.setVisibility(View.VISIBLE);
        binding.tvAvisoFallbackPdf.setText(
                getString(R.string.aviso_visualizador_fallback, arquivoPdfAtual.getName())
        );
    }
}
