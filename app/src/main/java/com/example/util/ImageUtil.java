package com.example.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;

import androidx.core.content.FileProvider;

import com.example.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Utilitários para captura via câmera, armazenamento privado local (Context.getFilesDir())
 * e carregamento seguro com imagem de placeholder para o perfil dos residentes.
 */
public final class ImageUtil {

    private static final String TAG = "ImageUtil";

    public static final String DIRETORIO_FOTOS_PRIVADO = "fotos_idosos";
    public static final String DIRETORIO_CAPTURAS_TEMP = "capturas_camera";
    public static final String ESTADO_PLACEHOLDER = "PLACEHOLDER";
    public static final int PLACEHOLDER_RES_ID = R.drawable.img_resident_placeholder;

    private static final int LARGURA_EXIBICAO_PADRAO = 512;
    private static final int ALTURA_EXIBICAO_PADRAO = 512;
    private static final int DIMENSAO_MAXIMA_SALVAMENTO = 1024;
    private static final int QUALIDADE_JPEG_SALVAMENTO = 88;

    private ImageUtil() {
        // Classe utilitária estática
    }

    /**
     * Verifica se o caminho aponta para um arquivo de imagem existente e não vazio.
     */
    public static boolean possuiFotoValida(String caminhoFoto) {
        if (caminhoFoto == null || caminhoFoto.trim().isEmpty()) {
            return false;
        }
        File arquivo = new File(caminhoFoto.trim());
        return arquivo.exists() && arquivo.isFile() && arquivo.length() > 0;
    }

    /**
     * Carrega a foto local armazenada nos arquivos privados do aplicativo com amostragem segura.
     * Caso o caminho seja nulo, vazio, inexistente ou inválido, exibe a imagem de placeholder padrão.
     */
    public static void carregarFoto(ImageView imageView, String caminhoFoto) {
        if (imageView == null) {
            return;
        }

        if (possuiFotoValida(caminhoFoto)) {
            String caminhoLimpo = caminhoFoto.trim();
            Bitmap bitmap = decodificarBitmapReduzido(
                    caminhoLimpo,
                    LARGURA_EXIBICAO_PADRAO,
                    ALTURA_EXIBICAO_PADRAO
            );
            if (bitmap != null) {
                Bitmap bitmapOrientado = corrigirOrientacaoExif(caminhoLimpo, bitmap);
                imageView.setImageBitmap(bitmapOrientado);
                imageView.setTag(R.id.tag_foto_estado, caminhoLimpo);
                return;
            }
        }

        imageView.setImageResource(PLACEHOLDER_RES_ID);
        imageView.setTag(R.id.tag_foto_estado, ESTADO_PLACEHOLDER);
    }

    /**
     * Retorna se o ImageView está exibindo atualmente o placeholder padrão.
     */
    public static boolean estaExibindoPlaceholder(ImageView imageView) {
        if (imageView == null) {
            return true;
        }
        Object estado = imageView.getTag(R.id.tag_foto_estado);
        return estado == null || ESTADO_PLACEHOLDER.equals(estado);
    }

    /**
     * Decodifica a imagem com tamanho proporcionalmente reduzido para evitar OutOfMemoryError.
     */
    public static Bitmap decodificarBitmapReduzido(String caminho, int larguraDesejada, int alturaDesejada) {
        if (caminho == null || caminho.trim().isEmpty()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(caminho, options);

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }

        options.inSampleSize = calcularTamanhoAmostra(options, larguraDesejada, alturaDesejada);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(caminho, options);
    }

    private static int calcularTamanhoAmostra(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Retorna o diretório privado interno do aplicativo (getFilesDir()/fotos_idosos) onde as fotos
     * definitivas dos residentes são persistidas.
     */
    public static File obterDiretorioFotosPrivado(Context context) {
        File diretorio = new File(context.getFilesDir(), DIRETORIO_FOTOS_PRIVADO);
        if (!diretorio.exists() && !diretorio.mkdirs()) {
            Log.w(TAG, "Diretório privado já existe ou não pôde ser criado: " + diretorio.getAbsolutePath());
        }
        return diretorio;
    }

    /**
     * Cria um novo arquivo único no diretório privado interno (getFilesDir()/fotos_idosos).
     */
    public static File criarArquivoFoto(Context context) {
        File diretorio = obterDiretorioFotosPrivado(context);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String sufixoUnico = UUID.randomUUID().toString().substring(0, 6);
        return new File(diretorio, "FOTO_" + timestamp + "_" + sufixoUnico + ".jpg");
    }

    /**
     * Cria um arquivo temporário na área de cache do aplicativo para receber a saída bruta da câmera,
     * evitando arquivos órfãos vazios em getFilesDir() caso o usuário cancele a captura.
     */
    public static File criarArquivoTemporarioCaptura(Context context) throws IOException {
        File diretorioTemp = new File(context.getCacheDir(), DIRETORIO_CAPTURAS_TEMP);
        if (!diretorioTemp.exists() && !diretorioTemp.mkdirs()) {
            Log.w(TAG, "Diretório temporário de captura não pôde ser criado: " + diretorioTemp.getAbsolutePath());
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return File.createTempFile("CAPTURA_" + timestamp + "_", ".jpg", diretorioTemp);
    }

    /**
     * Monta a Intent segura para captura de imagem usando a câmera do dispositivo com FileProvider.
     */
    public static Intent criarIntentCapturaCamera(Context context, File arquivoSaida) {
        Uri uriSaida = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                arquivoSaida
        );
        Intent intentCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, uriSaida);
        intentCamera.setClipData(ClipData.newRawUri("foto_perfil_residente", uriSaida));
        intentCamera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intentCamera;
    }

    /**
     * Processa o arquivo capturado pela câmera (corrigindo rotação EXIF e redimensionando)
     * e o salva definitivamente nos arquivos privados internos do app (getFilesDir()/fotos_idosos).
     * Remove o arquivo temporário de captura ao final.
     */
    public static String salvarFotoCapturadaNoArmazenamentoPrivado(Context context, File arquivoCapturado) {
        if (context == null || arquivoCapturado == null || !arquivoCapturado.exists() || arquivoCapturado.length() == 0) {
            removerArquivoSilenciosamente(arquivoCapturado);
            return null;
        }

        try {
            String caminhoOrigem = arquivoCapturado.getAbsolutePath();
            Bitmap bitmapDecodificado = decodificarBitmapReduzido(
                    caminhoOrigem,
                    DIMENSAO_MAXIMA_SALVAMENTO,
                    DIMENSAO_MAXIMA_SALVAMENTO
            );
            if (bitmapDecodificado == null) {
                return null;
            }

            Bitmap bitmapOrientado = corrigirOrientacaoExif(caminhoOrigem, bitmapDecodificado);
            Bitmap bitmapAjustado = limitarDimensoesMaximas(bitmapOrientado, DIMENSAO_MAXIMA_SALVAMENTO);

            File arquivoDestinoPrivado = criarArquivoFoto(context);
            try (FileOutputStream out = new FileOutputStream(arquivoDestinoPrivado)) {
                boolean comprimido = bitmapAjustado.compress(
                        Bitmap.CompressFormat.JPEG,
                        QUALIDADE_JPEG_SALVAMENTO,
                        out
                );
                out.flush();
                if (!comprimido || arquivoDestinoPrivado.length() == 0) {
                    removerArquivoSilenciosamente(arquivoDestinoPrivado);
                    return null;
                }
            }

            return arquivoDestinoPrivado.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível salvar a foto capturada no armazenamento privado: " + e.getMessage());
            return null;
        } finally {
            removerArquivoSilenciosamente(arquivoCapturado);
        }
    }

    /**
     * Salva um Bitmap retornado diretamente nos extras da câmera nos arquivos privados do app.
     */
    public static String salvarBitmapNoArmazenamentoPrivado(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) {
            return null;
        }

        File arquivoDestinoPrivado = criarArquivoFoto(context);
        Bitmap bitmapAjustado = limitarDimensoesMaximas(bitmap, DIMENSAO_MAXIMA_SALVAMENTO);
        try (FileOutputStream out = new FileOutputStream(arquivoDestinoPrivado)) {
            boolean comprimido = bitmapAjustado.compress(
                    Bitmap.CompressFormat.JPEG,
                    QUALIDADE_JPEG_SALVAMENTO,
                    out
            );
            out.flush();
            if (comprimido && arquivoDestinoPrivado.length() > 0) {
                return arquivoDestinoPrivado.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível gravar bitmap da câmera no armazenamento privado: " + e.getMessage());
        }
        removerArquivoSilenciosamente(arquivoDestinoPrivado);
        return null;
    }

    /**
     * Copia uma imagem selecionada da Galeria para a pasta privada interna do app (getFilesDir()/fotos_idosos).
     */
    public static String copiarUriParaArmazenamentoInterno(Context context, Uri uriOrigem) {
        if (context == null || uriOrigem == null) {
            return null;
        }
        try {
            File arquivoTemp = criarArquivoTemporarioCaptura(context);
            try (InputStream in = context.getContentResolver().openInputStream(uriOrigem);
                 FileOutputStream out = new FileOutputStream(arquivoTemp)) {
                if (in == null) {
                    removerArquivoSilenciosamente(arquivoTemp);
                    return null;
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return salvarFotoCapturadaNoArmazenamentoPrivado(context, arquivoTemp);
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível copiar arquivo para armazenamento interno: " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove com segurança uma foto antiga caso ela pertença ao diretório privado do aplicativo.
     */
    public static void removerFotoPrivadaSeExistir(Context context, String caminhoFotoAntiga) {
        if (context == null || caminhoFotoAntiga == null || caminhoFotoAntiga.trim().isEmpty()) {
            return;
        }
        try {
            File arquivoAntigo = new File(caminhoFotoAntiga.trim());
            if (!arquivoAntigo.exists() || !arquivoAntigo.isFile()) {
                return;
            }
            String raizPrivada = context.getFilesDir().getCanonicalPath();
            String caminhoCanonicoArquivo = arquivoAntigo.getCanonicalPath();
            if (caminhoCanonicoArquivo.startsWith(raizPrivada)) {
                removerArquivoSilenciosamente(arquivoAntigo);
            }
        } catch (IOException e) {
            Log.w(TAG, "Falha ao verificar exclusão de foto privada antiga: " + e.getMessage());
        }
    }

    public static void removerArquivoSilenciosamente(File arquivo) {
        if (arquivo != null && arquivo.exists()) {
            if (!arquivo.delete()) {
                Log.w(TAG, "Arquivo temporário não pôde ser removido: " + arquivo.getAbsolutePath());
            }
        }
    }

    private static Bitmap corrigirOrientacaoExif(String caminhoArquivo, Bitmap bitmap) {
        if (bitmap == null || caminhoArquivo == null) {
            return bitmap;
        }
        try {
            ExifInterface exif = new ExifInterface(caminhoArquivo);
            int orientacao = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            int anguloRotacao = 0;
            if (orientacao == ExifInterface.ORIENTATION_ROTATE_90) {
                anguloRotacao = 90;
            } else if (orientacao == ExifInterface.ORIENTATION_ROTATE_180) {
                anguloRotacao = 180;
            } else if (orientacao == ExifInterface.ORIENTATION_ROTATE_270) {
                anguloRotacao = 270;
            }

            if (anguloRotacao != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(anguloRotacao);
                return Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );
            }
        } catch (IOException ignored) {
            // Mantém o bitmap original caso não existam metadados EXIF
        }
        return bitmap;
    }

    private static Bitmap limitarDimensoesMaximas(Bitmap bitmap, int maxDimensao) {
        int largura = bitmap.getWidth();
        int altura = bitmap.getHeight();
        if (largura <= maxDimensao && altura <= maxDimensao) {
            return bitmap;
        }
        float proporcao = Math.min((float) maxDimensao / largura, (float) maxDimensao / altura);
        int novaLargura = Math.max(1, Math.round(largura * proporcao));
        int novaAltura = Math.max(1, Math.round(altura * proporcao));
        return Bitmap.createScaledBitmap(bitmap, novaLargura, novaAltura, true);
    }
}
