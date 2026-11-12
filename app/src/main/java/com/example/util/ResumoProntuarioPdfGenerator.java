package com.example.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.example.model.Idoso;
import com.example.model.Medicamento;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gerador nativo do Resumo Executivo do Prontuário em formato PDF (1 página A4 - 595 x 842)
 * focado nas informações principais do residente selecionado, com persistência no armazenamento
 * local privado (consulta 100% offline) e exportação para pastas locais / Downloads via MediaStore e SAF.
 */
public final class ResumoProntuarioPdfGenerator {

    private static final String TAG = "ResumoProntuarioPdf";

    public static final int A4_WIDTH = 595;
    public static final int A4_HEIGHT = 842;

    public static final String DIRETORIO_RESUMOS_OFFLINE = "resumos_prontuario_offline";

    private static final int MARGIN_LEFT = 36;
    private static final int MARGIN_RIGHT = 559;
    private static final int MARGIN_TOP = 32;
    private static final int MAX_MEDS_RESUMO = 8;

    // Paleta Material Design 3 de alto contraste para leitura em tela e impressão
    private static final int COLOR_PRIMARY = Color.parseColor("#5B4397");
    private static final int COLOR_PRIMARY_CONTAINER = Color.parseColor("#EADDFF");
    private static final int COLOR_ON_PRIMARY_CONTAINER = Color.parseColor("#21005D");
    private static final int COLOR_ALLERGY_BG = Color.parseColor("#FCE8E6");
    private static final int COLOR_ALLERGY_TEXT = Color.parseColor("#410E0B");
    private static final int COLOR_SAFE_BG = Color.parseColor("#E6F4EA");
    private static final int COLOR_SAFE_TEXT = Color.parseColor("#0D5324");
    private static final int COLOR_CLINICAL_BG = Color.parseColor("#E2F3F5");
    private static final int COLOR_CLINICAL_TEXT = Color.parseColor("#00363D");
    private static final int COLOR_SURFACE_CARD = Color.parseColor("#F8FAFC");
    private static final int COLOR_BORDER = Color.parseColor("#CBD5E1");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#1D1B20");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#49454F");

    private ResumoProntuarioPdfGenerator() {
        // Classe utilitária estática
    }

    /**
     * Gera o Resumo em PDF das informações principais do prontuário do idoso selecionado
     * e grava automaticamente no armazenamento local do aplicativo para consulta offline.
     */
    public static File gerarESalvarResumoOffline(
            Context context,
            Idoso idoso,
            List<Medicamento> prescricoes
    ) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Contexto inválido para geração do resumo em PDF.");
        }
        if (idoso == null) {
            throw new IllegalArgumentException("Selecione um residente válido para gerar o resumo em PDF.");
        }

        List<Medicamento> todosMeds = prescricoes != null ? prescricoes : new ArrayList<>();
        List<Medicamento> medsAtivos = new ArrayList<>();
        for (Medicamento m : todosMeds) {
            if (m != null && m.isAtivo()) {
                medsAtivos.add(m);
            }
        }

        String dataEmissao = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
                .format(new Date());

        PdfDocument pdfDocument = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = desenharCabecalhoResumo(canvas, dataEmissao);
            y = desenharIdentificacaoPrincipal(canvas, idoso, y);
            y = desenharQuadroClinicoEssencial(canvas, idoso, y);
            y = desenharContatoEmergenciaResumo(canvas, idoso, y);
            desenharSinteseMedicamentosAtivos(canvas, medsAtivos, todosMeds.size(), y);
            desenharRodapeOffline(canvas, idoso);

            pdfDocument.finishPage(page);

            File arquivoOffline = obterArquivoDestinoOffline(context, idoso);
            // Remove versões anteriores do mesmo ID caso o nome do residente tenha sido editado
            limparResumosAntigosDoMesmoId(context, idoso.getId(), arquivoOffline.getName());

            try (FileOutputStream fos = new FileOutputStream(arquivoOffline)) {
                pdfDocument.writeTo(fos);
                fos.flush();
            }

            // Mantém espelho no diretório privado Documents para rápida inspeção local
            espaharNoDiretorioDocumentosApp(context, arquivoOffline);

            Log.i(TAG, "Resumo do prontuário salvo localmente para consulta offline: "
                    + arquivoOffline.getAbsolutePath());
            return arquivoOffline;
        } finally {
            pdfDocument.close();
        }
    }

    /**
     * Retorna o arquivo PDF de resumo já salvo no armazenamento local para o residente,
     * ou {@code null} caso ainda não tenha sido gerado.
     */
    public static File obterResumoSalvoOffline(Context context, Idoso idoso) {
        if (context == null || idoso == null) {
            return null;
        }
        File candidatoDireto = obterArquivoDestinoOffline(context, idoso);
        if (candidatoDireto.exists() && candidatoDireto.length() > 0) {
            return candidatoDireto;
        }
        if (idoso.getId() > 0) {
            return obterResumoSalvoOfflinePorId(context, idoso.getId());
        }
        return null;
    }

    /**
     * Busca no diretório local offline qualquer resumo associado ao ID do residente.
     */
    public static File obterResumoSalvoOfflinePorId(Context context, int idosoId) {
        if (context == null || idosoId <= 0) {
            return null;
        }
        File pasta = obterDiretorioResumosOffline(context);
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) {
            return null;
        }
        String prefixo = "Resumo_Prontuario_ID" + idosoId + "_";
        for (File f : arquivos) {
            if (f.isFile() && f.getName().startsWith(prefixo) && f.getName().endsWith(".pdf") && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    /**
     * Copia o resumo PDF gerado para uma URI escolhida pelo usuário no seletor nativo
     * do sistema Android (Storage Access Framework - ACTION_CREATE_DOCUMENT).
     */
    public static void copiarParaUriLocal(Context context, File arquivoPdfOrigem, Uri uriDestino)
            throws IOException {
        if (context == null || arquivoPdfOrigem == null || !arquivoPdfOrigem.exists() || uriDestino == null) {
            throw new IllegalArgumentException("Arquivo PDF ou destino local inválido.");
        }

        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = new FileInputStream(arquivoPdfOrigem);
             OutputStream out = resolver.openOutputStream(uriDestino)) {
            if (out == null) {
                throw new IOException("Não foi possível abrir o fluxo de gravação na pasta selecionada.");
            }
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                out.write(buffer, 0, lidos);
            }
            out.flush();
        }
    }

    /**
     * Salva uma cópia direta na pasta local Downloads/CuidarPlus do aparelho sem exigir permissões
     * de armazenamento (via MediaStore.Downloads no Android 10+ ou pasta Downloads privada do app em API 24-28).
     *
     * @return Nome/caminho amigável onde a cópia foi gravada
     */
    public static String salvarCopiaEmDownloads(Context context, File arquivoPdfOrigem) throws IOException {
        if (context == null || arquivoPdfOrigem == null || !arquivoPdfOrigem.exists()) {
            throw new IllegalArgumentException("Arquivo PDF de resumo inexistente.");
        }

        String nomeExibicao = arquivoPdfOrigem.getName();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, nomeExibicao);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CuidarPlus");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (itemUri != null) {
                    try (InputStream in = new FileInputStream(arquivoPdfOrigem);
                         OutputStream out = resolver.openOutputStream(itemUri)) {
                        if (out == null) {
                            throw new IOException("Falha ao abrir saída em MediaStore.Downloads.");
                        }
                        byte[] buffer = new byte[8192];
                        int lidos;
                        while ((lidos = in.read(buffer)) != -1) {
                            out.write(buffer, 0, lidos);
                        }
                        out.flush();
                    }

                    ContentValues concluido = new ContentValues();
                    concluido.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(itemUri, concluido, null, null);
                    return nomeExibicao;
                }
            } catch (Exception e) {
                Log.w(TAG, "MediaStore.Downloads indisponível neste ambiente, aplicando fallback local: " + e.getMessage());
            }
        }

        // Fallback API 24-28 ou ambientes sem provedor MediaStore ativo
        File pastaDownloadsApp = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (pastaDownloadsApp == null) {
            pastaDownloadsApp = new File(context.getFilesDir(), "Downloads_CuidarPlus");
        }
        if (!pastaDownloadsApp.exists() && !pastaDownloadsApp.mkdirs()) {
            Log.w(TAG, "Diretório fallback de Downloads já existente ou não criado.");
        }

        File copia = new File(pastaDownloadsApp, nomeExibicao);
        try (InputStream in = new FileInputStream(arquivoPdfOrigem);
             OutputStream out = new FileOutputStream(copia)) {
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                out.write(buffer, 0, lidos);
            }
            out.flush();
        }
        return copia.getName();
    }

    public static File obterDiretorioResumosOffline(Context context) {
        File dir = new File(context.getFilesDir(), DIRETORIO_RESUMOS_OFFLINE);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static String sugerirNomeArquivoResumo(Idoso idoso) {
        int id = (idoso != null && idoso.getId() > 0) ? idoso.getId() : 0;
        String nomeSanitizado = sanitizarNome(idoso != null ? idoso.getNome() : "Residente");
        if (id > 0) {
            return "Resumo_Prontuario_ID" + id + "_" + nomeSanitizado + ".pdf";
        }
        return "Resumo_Prontuario_" + nomeSanitizado + ".pdf";
    }

    public static String formatarTamanhoArquivo(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f MB", mb);
    }

    public static String formatarDataAtualizacao(long timestampMillis) {
        return new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
                .format(new Date(timestampMillis));
    }

    // =========================================================================
    // DESENHO DO RESUMO EXECUTIVO A4 (1 PÁGINA)
    // =========================================================================

    private static int desenharCabecalhoResumo(Canvas canvas, String dataEmissao) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        fill.setColor(COLOR_PRIMARY);
        canvas.drawRoundRect(new RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT, MARGIN_TOP + 58), 10f, 10f, fill);

        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(14f);
        canvas.drawText("ILPI Vovô Lar • Resumo Executivo do Prontuário", MARGIN_LEFT + 16, MARGIN_TOP + 24, text);

        text.setColor(COLOR_PRIMARY_CONTAINER);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(9f);
        canvas.drawText(
                "CuidarPlus (Consulta Offline Local)  •  Gerado em: " + dataEmissao,
                MARGIN_LEFT + 16,
                MARGIN_TOP + 43,
                text
        );

        return MARGIN_TOP + 74;
    }

    private static int desenharIdentificacaoPrincipal(Canvas canvas, Idoso idoso, int yTopo) {
        int y = desenharSubtituloSecao(canvas, "1. IDENTIFICAÇÃO PRINCIPAL DO RESIDENTE", yTopo);

        Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFill.setColor(COLOR_SURFACE_CARD);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(COLOR_BORDER);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);

        RectF box = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 92);
        canvas.drawRoundRect(box, 8f, 8f, cardFill);
        canvas.drawRoundRect(box, 8f, 8f, border);

        Paint label = criarPaintTexto(COLOR_TEXT_SECONDARY, 9.2f, true);
        Paint valor = criarPaintTexto(COLOR_TEXT_PRIMARY, 10f, false);
        Paint destaque = criarPaintTexto(COLOR_ON_PRIMARY_CONTAINER, 12f, true);

        String nome = textoOuPadrao(idoso.getNome(), "Não informado");
        canvas.drawText(truncar(nome, 48), MARGIN_LEFT + 14, y + 24, destaque);

        // Badge Tipo Sanguíneo no canto direito
        String sangue = "Tipo Sanguíneo: " + textoOuPadrao(idoso.getTipoSanguineo(), "N/I");
        Paint badgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeFill.setColor(COLOR_PRIMARY_CONTAINER);
        canvas.drawRoundRect(new RectF(MARGIN_RIGHT - 138, y + 10, MARGIN_RIGHT - 12, y + 32), 11f, 11f, badgeFill);
        Paint badgeTxt = criarPaintTexto(COLOR_ON_PRIMARY_CONTAINER, 9f, true);
        canvas.drawText(truncar(sangue, 22), MARGIN_RIGHT - 128, y + 24, badgeTxt);

        int idade = idoso.calcularIdade();
        String textoIdadeNasc = (idade > 0 ? (idade + " anos • ") : "")
                + "Nasc: " + textoOuPadrao(idoso.getDataNascimento(), "-");
        canvas.drawText("Idade / Nascimento:", MARGIN_LEFT + 14, y + 50, label);
        canvas.drawText(textoIdadeNasc, MARGIN_LEFT + 112, y + 50, valor);

        canvas.drawText("CPF:", MARGIN_LEFT + 310, y + 50, label);
        canvas.drawText(textoOuPadrao(idoso.getCpf(), "Não informado"), MARGIN_LEFT + 340, y + 50, valor);

        canvas.drawText("Quarto / Leito:", MARGIN_LEFT + 14, y + 74, label);
        canvas.drawText(textoOuPadrao(idoso.getLeitoQuarto(), "Não informado"), MARGIN_LEFT + 92, y + 74, valor);

        return y + 108;
    }

    private static int desenharQuadroClinicoEssencial(Canvas canvas, Idoso idoso, int yTopo) {
        int y = desenharSubtituloSecao(canvas, "2. QUADRO CLÍNICO ESSENCIAL (ALERGIAS E RESTRIÇÕES)", yTopo);

        String alergias = textoOuPadrao(idoso.getAlergias(), "Nenhuma alergia conhecida ou relatada.");
        boolean temAlergia = !alergias.toLowerCase(Locale.ROOT).contains("nenhuma");

        Paint boxAlergia = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxAlergia.setColor(temAlergia ? COLOR_ALLERGY_BG : COLOR_SAFE_BG);
        canvas.drawRoundRect(new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 52), 8f, 8f, boxAlergia);

        Paint tituloAlergia = criarPaintTexto(temAlergia ? COLOR_ALLERGY_TEXT : COLOR_SAFE_TEXT, 9.2f, true);
        Paint valorAlergia = criarPaintTexto(temAlergia ? COLOR_ALLERGY_TEXT : COLOR_SAFE_TEXT, 9.8f, false);
        canvas.drawText("Alergias Conhecidas:", MARGIN_LEFT + 14, y + 19, tituloAlergia);
        canvas.drawText(truncar(alergias, 78), MARGIN_LEFT + 14, y + 38, valorAlergia);

        int yRestr = y + 60;
        String restricoes = textoOuPadrao(idoso.getRestricoesMedicas(), "Sem restrições médicas registradas.");
        Paint boxRestr = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxRestr.setColor(COLOR_CLINICAL_BG);
        canvas.drawRoundRect(new RectF(MARGIN_LEFT, yRestr, MARGIN_RIGHT, yRestr + 58), 8f, 8f, boxRestr);

        Paint tituloRestr = criarPaintTexto(COLOR_CLINICAL_TEXT, 9.2f, true);
        Paint valorRestr = criarPaintTexto(COLOR_CLINICAL_TEXT, 9.6f, false);
        canvas.drawText("Restrições Médicas, Diagnósticos e Cuidados:", MARGIN_LEFT + 14, yRestr + 18, tituloRestr);
        if (restricoes.length() > 80) {
            canvas.drawText(restricoes.substring(0, 80), MARGIN_LEFT + 14, yRestr + 34, valorRestr);
            canvas.drawText(truncar(restricoes.substring(80).trim(), 80), MARGIN_LEFT + 14, yRestr + 48, valorRestr);
        } else {
            canvas.drawText(restricoes, MARGIN_LEFT + 14, yRestr + 38, valorRestr);
        }

        return yRestr + 74;
    }

    private static int desenharContatoEmergenciaResumo(Canvas canvas, Idoso idoso, int yTopo) {
        int y = desenharSubtituloSecao(canvas, "3. CONTATO DE EMERGÊNCIA E FAMILIAR RESPONSÁVEL", yTopo);

        FichaIdosoPdfGenerator.DadosFamiliarExtraidos fam = FichaIdosoPdfGenerator.extrairDadosFamiliar(idoso);

        Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFill.setColor(COLOR_SURFACE_CARD);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(COLOR_BORDER);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);

        RectF box = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 54);
        canvas.drawRoundRect(box, 8f, 8f, cardFill);
        canvas.drawRoundRect(box, 8f, 8f, border);

        Paint label = criarPaintTexto(COLOR_TEXT_SECONDARY, 9.2f, true);
        Paint valor = criarPaintTexto(COLOR_TEXT_PRIMARY, 9.8f, false);
        Paint foneBold = criarPaintTexto(COLOR_PRIMARY, 10f, true);

        canvas.drawText("Responsável:", MARGIN_LEFT + 14, y + 21, label);
        canvas.drawText(
                truncar(fam.nomeFamiliar + " (" + fam.grauParentesco + ")", 46),
                MARGIN_LEFT + 82,
                y + 21,
                valor
        );

        canvas.drawText("Telefone de Emergência:", MARGIN_LEFT + 14, y + 41, label);
        canvas.drawText(truncar(fam.telefoneEmergencia, 42), MARGIN_LEFT + 136, y + 41, foneBold);

        return y + 70;
    }

    private static void desenharSinteseMedicamentosAtivos(
            Canvas canvas,
            List<Medicamento> medsAtivos,
            int totalCadastrados,
            int yTopo
    ) {
        String titulo = "4. MEDICAMENTOS ATIVOS EM USO (" + medsAtivos.size() + " ativos de " + totalCadastrados + ")";
        int y = desenharSubtituloSecao(canvas, titulo, yTopo);

        Paint headerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerFill.setColor(COLOR_PRIMARY_CONTAINER);
        canvas.drawRoundRect(new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 24), 6f, 6f, headerFill);

        Paint th = criarPaintTexto(COLOR_ON_PRIMARY_CONTAINER, 9f, true);
        canvas.drawText("Medicamento", MARGIN_LEFT + 10, y + 16, th);
        canvas.drawText("Dosagem / Tipo", MARGIN_LEFT + 195, y + 16, th);
        canvas.drawText("Horários Diários", MARGIN_LEFT + 340, y + 16, th);

        int rowY = y + 28;
        Paint tdBold = criarPaintTexto(COLOR_TEXT_PRIMARY, 9f, true);
        Paint td = criarPaintTexto(COLOR_TEXT_SECONDARY, 8.8f, false);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(COLOR_BORDER);
        line.setStrokeWidth(0.8f);

        if (medsAtivos.isEmpty()) {
            canvas.drawText(
                    "Nenhuma prescrição ativa registrada no prontuário até o momento.",
                    MARGIN_LEFT + 10,
                    rowY + 18,
                    td
            );
            return;
        }

        int limite = Math.min(MAX_MEDS_RESUMO, medsAtivos.size());
        for (int i = 0; i < limite; i++) {
            Medicamento med = medsAtivos.get(i);
            String nomeMed = truncar(textoOuPadrao(med.getNomeMedicamento(), "-"), 28);
            String doseTipo = truncar(
                    textoOuPadrao(med.getDosagem(), "-") + " (" + textoOuPadrao(med.getTipo(), "Dose") + ")",
                    24
            );
            String grade = truncar(
                    FichaIdosoPdfGenerator.calcularGradeHorarios(med.getHorarioInicial(), med.getFrequenciaHoras()),
                    28
            );

            canvas.drawText(nomeMed, MARGIN_LEFT + 10, rowY + 15, tdBold);
            canvas.drawText(doseTipo, MARGIN_LEFT + 195, rowY + 15, td);
            canvas.drawText(grade, MARGIN_LEFT + 340, rowY + 15, td);
            canvas.drawLine(MARGIN_LEFT, rowY + 22, MARGIN_RIGHT, rowY + 22, line);
            rowY += 23;
        }

        if (medsAtivos.size() > MAX_MEDS_RESUMO) {
            int ocultos = medsAtivos.size() - MAX_MEDS_RESUMO;
            canvas.drawText(
                    "+ " + ocultos + " prescrição(ões) adicional(is) disponível(is) na ficha completa.",
                    MARGIN_LEFT + 10,
                    rowY + 14,
                    td
            );
        }
    }

    private static void desenharRodapeOffline(Canvas canvas, Idoso idoso) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(COLOR_BORDER);
        line.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, 788, MARGIN_RIGHT, 788, line);

        Paint rodape = criarPaintTexto(COLOR_TEXT_SECONDARY, 8.5f, false);
        canvas.drawText(
                "Arquivo offline: " + sugerirNomeArquivoResumo(idoso) + " • Uso interno ILPI Vovô Lar",
                MARGIN_LEFT,
                806,
                rodape
        );
        canvas.drawText("Página 1 de 1", MARGIN_RIGHT - 58, 806, rodape);
    }

    private static int desenharSubtituloSecao(Canvas canvas, String titulo, int yTopo) {
        Paint p = criarPaintTexto(COLOR_PRIMARY, 9.8f, true);
        canvas.drawText(titulo, MARGIN_LEFT, yTopo + 12, p);
        return yTopo + 18;
    }

    private static Paint criarPaintTexto(int cor, float tamanho, boolean negrito) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(cor);
        p.setTextSize(tamanho);
        p.setTypeface(negrito ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
        return p;
    }

    private static File obterArquivoDestinoOffline(Context context, Idoso idoso) {
        File dir = obterDiretorioResumosOffline(context);
        return new File(dir, sugerirNomeArquivoResumo(idoso));
    }

    private static void limparResumosAntigosDoMesmoId(Context context, int idosoId, String nomeAtual) {
        if (idosoId <= 0) return;
        File[] arquivos = obterDiretorioResumosOffline(context).listFiles();
        if (arquivos == null) return;
        String prefixo = "Resumo_Prontuario_ID" + idosoId + "_";
        for (File f : arquivos) {
            if (f.isFile() && f.getName().startsWith(prefixo) && !f.getName().equals(nomeAtual)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static void espaharNoDiretorioDocumentosApp(Context context, File arquivoOffline) {
        File pastaDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (pastaDocs == null) return;
        if (!pastaDocs.exists() && !pastaDocs.mkdirs()) return;

        File copiaDocs = new File(pastaDocs, arquivoOffline.getName());
        try (InputStream in = new FileInputStream(arquivoOffline);
             OutputStream out = new FileOutputStream(copiaDocs)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "Espelhamento opcional em DIRECTORY_DOCUMENTS ignorado: " + e.getMessage());
        }
    }

    private static String textoOuPadrao(String texto, String padrao) {
        return (texto != null && !texto.trim().isEmpty()) ? texto.trim() : padrao;
    }

    private static String truncar(String texto, int max) {
        if (texto == null) return "";
        if (texto.length() <= max) return texto;
        return texto.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String sanitizarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            return "Residente";
        }
        String semAcentos = Normalizer.normalize(nome.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        String limpo = semAcentos.replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return limpo.isEmpty() ? "Residente" : limpo;
    }
}
