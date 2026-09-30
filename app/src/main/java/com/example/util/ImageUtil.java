package com.example.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.example.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utilitários para carregamento seguro e salvamento de fotos dos idosos.
 */
public class ImageUtil {

    private static final String TAG = "ImageUtil";

    /**
     * Carrega a foto do caminho local com amostragem segura (evitando OutOfMemoryError).
     * Se o caminho for nulo, vazio ou inválido, exibe a imagem de placeholder padrão.
     */
    public static void carregarFoto(ImageView imageView, String caminhoFoto) {
        if (caminhoFoto != null && !caminhoFoto.trim().isEmpty()) {
            File arquivo = new File(caminhoFoto);
            if (arquivo.exists()) {
                Bitmap bitmap = decodificarBitmapReduzido(caminhoFoto, 300, 300);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            }
        }
        imageView.setImageResource(R.drawable.ic_person_placeholder);
    }

    /**
     * Decodifica a imagem com tamanho proporcionalmente reduzido para exibição suave.
     */
    public static Bitmap decodificarBitmapReduzido(String caminho, int larguraDesejada, int alturaDesejada) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(caminho, options);

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
     * Cria um arquivo temporário no diretório interno para receber a foto da câmera.
     */
    public static File criarArquivoFoto(Context context) {
        File diretorio = new File(context.getFilesDir(), "fotos_idosos");
        if (!diretorio.exists()) {
            boolean criado = diretorio.mkdirs();
            if (!criado) {
                Log.w(TAG, "Diretório já existe ou não pôde ser criado: " + diretorio.getAbsolutePath());
            }
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(diretorio, "FOTO_" + timestamp + ".jpg");
    }

    /**
     * Copia uma imagem selecionada da Galeria para a pasta interna do app usando try-with-resources.
     */
    public static String copiarUriParaArmazenamentoInterno(Context context, Uri uriOrigem) {
        File arquivoDestino = criarArquivoFoto(context);
        try (InputStream in = context.getContentResolver().openInputStream(uriOrigem);
             FileOutputStream out = new FileOutputStream(arquivoDestino)) {
            if (in != null) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                return arquivoDestino.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar arquivo para armazenamento interno", e);
        }
        return null;
    }
}
