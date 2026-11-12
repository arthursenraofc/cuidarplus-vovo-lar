package com.example.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.model.Idoso;
import com.example.model.Medicamento;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gerador nativo da Ficha / Prontuário Individual do Residente em formato PDF (Padrão A4)
 * para a ILPI Vovô Lar - Aplicativo CuidarPlus.
 *
 * Opera 100% offline (Local-First) utilizando exclusivamente android.graphics.pdf.PdfDocument,
 * Canvas, Paint e FileProvider, sem bibliotecas de terceiros ou serviços em nuvem.
 */
public final class FichaIdosoPdfGenerator {

    private static final String TAG = "FichaIdosoPdfGen";

    // Padrão ISO A4 em pontos tipográficos (72 DPI): 595 x 842 pixels
    public static final int A4_WIDTH = 595;
    public static final int A4_HEIGHT = 842;

    private static final int MARGIN_LEFT = 36;
    private static final int MARGIN_RIGHT = 559;
    private static final int MARGIN_TOP = 32;
    private static final int FOOTER_DIVIDER_Y = 790;

    private static final int MAX_MEDS_PRIMEIRA_PAGINA = 11;
    private static final int MAX_MEDS_PAGINA_CONTINUACAO = 22;

    // Paleta institucional de alto contraste para impressão e leitura digital
    private static final int COLOR_PRIMARY = Color.parseColor("#1565C0");
    private static final int COLOR_PRIMARY_DARK = Color.parseColor("#0D47A1");
    private static final int COLOR_ACCENT_SOFT = Color.parseColor("#E3F2FD");
    private static final int COLOR_CARD_BG = Color.parseColor("#F8FAFC");
    private static final int COLOR_ROW_ZEBRA = Color.parseColor("#F1F5F9");
    private static final int COLOR_BORDER = Color.parseColor("#CBD5E1");
    private static final int COLOR_TEXT_DARK = Color.parseColor("#1E293B");
    private static final int COLOR_TEXT_MEDIUM = Color.parseColor("#475569");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#64748B");
    private static final int COLOR_ALERT_RED = Color.parseColor("#B91C1C");

    private static final Set<String> GRAUS_PARENTESCO_CONHECIDOS = new HashSet<>(Arrays.asList(
            "filho", "filha", "neto", "neta", "sobrinho", "sobrinha",
            "irmão", "irma", "irmã", "irmao", "esposo", "esposa",
            "cônjuge", "conjuge", "genro", "nora", "curador", "curadora",
            "tutor", "tutora", "responsável", "responsavel", "primo", "prima"
    ));

    private FichaIdosoPdfGenerator() {
        // Classe utilitária estática
    }

    /**
     * Gera e salva no armazenamento privado do aplicativo o Prontuário Individual do Idoso em A4.
     *
     * @param context     Contexto Android
     * @param idoso       Entidade do residente recuperada do SQLite
     * @param prescricoes Lista de medicamentos vinculados ao residente
     * @return Objeto File apontando para o PDF salvo em DIRECTORY_DOCUMENTS
     * @throws IOException Caso ocorra falha de gravação em disco
     */
    public static File gerarProntuarioPdf(Context context, Idoso idoso, List<Medicamento> prescricoes)
            throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Contexto inválido para geração do PDF.");
        }
        if (idoso == null) {
            throw new IllegalArgumentException("Os dados do residente são obrigatórios para gerar o prontuário.");
        }

        List<Medicamento> listaMeds = prescricoes != null ? prescricoes : new ArrayList<>();
        int totalPaginas = calcularTotalPaginas(listaMeds.size());

        SimpleDateFormat sdfEmissao = new SimpleDateFormat(
                "dd/MM/yyyy 'às' HH:mm:ss",
                Locale.forLanguageTag("pt-BR")
        );
        String dataHoraEmissao = sdfEmissao.format(new Date());

        DadosFamiliarExtraidos dadosFamiliar = extrairDadosFamiliar(idoso);

        PdfDocument pdfDocument = new PdfDocument();
        try {
            int indiceMed = 0;

            for (int numeroPagina = 1; numeroPagina <= totalPaginas; numeroPagina++) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        A4_WIDTH, A4_HEIGHT, numeroPagina
                ).create();

                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // Cabeçalho Institucional em todas as páginas
                int yCursor = desenharCabecalhoInstitucional(canvas, dataHoraEmissao);

                if (numeroPagina == 1) {
                    // Bloco 1: Dados Pessoais e Clínicos
                    yCursor = desenharBlocoDadosPessoais(canvas, idoso, yCursor);

                    // Bloco 2: Contato do Responsável / Familiar
                    yCursor = desenharBlocoContatoFamiliar(canvas, dadosFamiliar, yCursor);

                    // Bloco 3: Tabela de Prescrições e Medicamentos em Uso
                    indiceMed = desenharBlocoTabelaPrescricoes(
                            canvas,
                            listaMeds,
                            indiceMed,
                            MAX_MEDS_PRIMEIRA_PAGINA,
                            yCursor,
                            false
                    );
                } else {
                    // Continuação da tabela em páginas subsequentes
                    indiceMed = desenharBlocoTabelaPrescricoes(
                            canvas,
                            listaMeds,
                            indiceMed,
                            MAX_MEDS_PAGINA_CONTINUACAO,
                            yCursor,
                            true
                    );
                }

                // Rodapé com Assinatura Técnica e Paginação ("Página X de Y")
                desenharRodapeInstitucional(canvas, numeroPagina, totalPaginas);

                pdfDocument.finishPage(page);
            }

            File arquivoDestino = criarArquivoPdfPrivado(context, idoso);
            try (FileOutputStream fos = new FileOutputStream(arquivoDestino)) {
                pdfDocument.writeTo(fos);
                fos.flush();
            }

            Log.i(TAG, "Prontuário PDF salvo com sucesso em: " + arquivoDestino.getAbsolutePath());
            return arquivoDestino;
        } finally {
            pdfDocument.close();
        }
    }

    // =========================================================================
    // BLOCOS DE DESENHO A4 (CANVAS & PAINT)
    // =========================================================================

    private static int desenharCabecalhoInstitucional(Canvas canvas, String dataHoraEmissao) {
        Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Faixa institucional superior
        paintFill.setColor(COLOR_PRIMARY_DARK);
        paintFill.setStyle(Paint.Style.FILL);
        RectF topBanner = new RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT, MARGIN_TOP + 52);
        canvas.drawRoundRect(topBanner, 6f, 6f, paintFill);

        // Título Oficial
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(13.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("ILPI Vovô Lar - Prontuário do Residente", MARGIN_LEFT + 14, MARGIN_TOP + 22, paintText);

        // Identificação do Sistema CuidarPlus e Data/Hora de Emissão
        paintText.setColor(COLOR_ACCENT_SOFT);
        paintText.setTextSize(8.8f);
        paintText.setTypeface(Typeface.DEFAULT);
        canvas.drawText(
                "Sistema CuidarPlus (Gestão Local-First)  •  Emissão: " + dataHoraEmissao,
                MARGIN_LEFT + 14,
                MARGIN_TOP + 40,
                paintText
        );

        paintLine.setColor(COLOR_BORDER);
        paintLine.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, MARGIN_TOP + 60, MARGIN_RIGHT, MARGIN_TOP + 60, paintLine);

        return MARGIN_TOP + 70;
    }

    private static int desenharBlocoDadosPessoais(Canvas canvas, Idoso idoso, int yInicial) {
        int y = desenharTituloBloco(canvas, "1. DADOS PESSOAIS E CLÍNICOS DO RESIDENTE", yInicial);

        Paint paintCard = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintValue = new Paint(Paint.ANTI_ALIAS_FLAG);

        int alturaCard = 148;
        RectF card = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + alturaCard);

        paintCard.setColor(COLOR_CARD_BG);
        paintCard.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(card, 6f, 6f, paintCard);

        paintBorder.setColor(COLOR_BORDER);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(1f);
        canvas.drawRoundRect(card, 6f, 6f, paintBorder);

        paintLabel.setColor(COLOR_TEXT_DARK);
        paintLabel.setTextSize(9.5f);
        paintLabel.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paintValue.setColor(COLOR_TEXT_MEDIUM);
        paintValue.setTextSize(9.5f);
        paintValue.setTypeface(Typeface.DEFAULT);

        // Linha 1: Nome Completo e Tipo Sanguíneo
        String nome = valorOuPadrao(idoso.getNome(), "Não informado");
        canvas.drawText("Nome Completo:", MARGIN_LEFT + 12, y + 19, paintLabel);
        paintValue.setColor(COLOR_PRIMARY_DARK);
        paintValue.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(truncarTexto(nome, 44), MARGIN_LEFT + 98, y + 19, paintValue);

        String sangue = valorOuPadrao(idoso.getTipoSanguineo(), "N/I");
        canvas.drawText("Tipo Sanguíneo:", MARGIN_LEFT + 380, y + 19, paintLabel);
        paintValue.setColor(COLOR_ALERT_RED);
        canvas.drawText(sangue, MARGIN_LEFT + 465, y + 19, paintValue);

        // Linha 2: Idade / Data de Nascimento e CPF
        paintValue.setColor(COLOR_TEXT_MEDIUM);
        paintValue.setTypeface(Typeface.DEFAULT);

        int idade = idoso.calcularIdade();
        String dataNasc = valorOuPadrao(idoso.getDataNascimento(), "-");
        String idadeENasc = (idade > 0 ? idade + " anos" : "Idade N/I") + " (" + dataNasc + ")";
        canvas.drawText("Idade / Nasc.:", MARGIN_LEFT + 12, y + 40, paintLabel);
        canvas.drawText(idadeENasc, MARGIN_LEFT + 98, y + 40, paintValue);

        String cpf = valorOuPadrao(idoso.getCpf(), "Não informado");
        canvas.drawText("CPF:", MARGIN_LEFT + 380, y + 40, paintLabel);
        canvas.drawText(truncarTexto(cpf, 18), MARGIN_LEFT + 410, y + 40, paintValue);

        // Linha 3: Leito / Quarto e Registro Interno
        String padraoLeito = idoso.getId() > 0
                ? String.format(Locale.US, "Ala Geriátrica - Leito %02d", idoso.getId())
                : "Ala Geriátrica - Leito Padrão";
        String leitoQuarto = valorOuPadrao(idoso.getLeitoQuarto(), padraoLeito);
        canvas.drawText("Leito / Quarto:", MARGIN_LEFT + 12, y + 61, paintLabel);
        canvas.drawText(truncarTexto(leitoQuarto, 42), MARGIN_LEFT + 98, y + 61, paintValue);

        String codProntuario = idoso.getId() > 0
                ? String.format(Locale.US, "#%04d", idoso.getId())
                : "Avulso";
        canvas.drawText("Prontuário:", MARGIN_LEFT + 380, y + 61, paintLabel);
        canvas.drawText(codProntuario, MARGIN_LEFT + 442, y + 61, paintValue);

        // Divisória interna clínica
        canvas.drawLine(MARGIN_LEFT + 12, y + 72, MARGIN_RIGHT - 12, y + 72, paintBorder);

        // Linha 4: Alergias Conhecidas
        paintLabel.setColor(COLOR_ALERT_RED);
        canvas.drawText("Alergias:", MARGIN_LEFT + 12, y + 90, paintLabel);
        paintValue.setColor(COLOR_TEXT_DARK);
        String alergias = valorOuPadrao(idoso.getAlergias(), "Nenhuma alergia medicamentosa ou alimentar relatada.");
        List<String> linhasAlergias = quebrarTexto(alergias, paintValue, 435f, 2);
        for (int i = 0; i < linhasAlergias.size(); i++) {
            canvas.drawText(linhasAlergias.get(i), MARGIN_LEFT + 70, y + 90 + (i * 12), paintValue);
        }

        // Linha 5: Observações Clínicas / Restrições Médicas
        paintLabel.setColor(COLOR_TEXT_DARK);
        canvas.drawText("Obs. Clínicas:", MARGIN_LEFT + 12, y + 120, paintLabel);
        paintValue.setColor(COLOR_TEXT_MEDIUM);
        String obsClinicas = valorOuPadrao(idoso.getRestricoesMedicas(), "Sem restrições clínicas adicionais registradas.");
        List<String> linhasObs = quebrarTexto(obsClinicas, paintValue, 415f, 2);
        for (int i = 0; i < linhasObs.size(); i++) {
            canvas.drawText(linhasObs.get(i), MARGIN_LEFT + 88, y + 120 + (i * 12), paintValue);
        }

        return y + alturaCard + 12;
    }

    private static int desenharBlocoContatoFamiliar(Canvas canvas, DadosFamiliarExtraidos familiar, int yInicial) {
        int y = desenharTituloBloco(canvas, "2. CONTATO DO RESPONSÁVEL / FAMILIAR", yInicial);

        Paint paintCard = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintValue = new Paint(Paint.ANTI_ALIAS_FLAG);

        int alturaCard = 54;
        RectF card = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + alturaCard);

        paintCard.setColor(COLOR_CARD_BG);
        paintCard.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(card, 6f, 6f, paintCard);

        paintBorder.setColor(COLOR_BORDER);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(1f);
        canvas.drawRoundRect(card, 6f, 6f, paintBorder);

        paintLabel.setColor(COLOR_TEXT_DARK);
        paintLabel.setTextSize(9.5f);
        paintLabel.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paintValue.setColor(COLOR_TEXT_MEDIUM);
        paintValue.setTextSize(9.5f);
        paintValue.setTypeface(Typeface.DEFAULT);

        // Linha 1: Nome do Familiar e Grau de Parentesco
        canvas.drawText("Nome do Familiar:", MARGIN_LEFT + 12, y + 21, paintLabel);
        canvas.drawText(truncarTexto(familiar.nomeFamiliar, 34), MARGIN_LEFT + 108, y + 21, paintValue);

        canvas.drawText("Grau de Parentesco:", MARGIN_LEFT + 320, y + 21, paintLabel);
        canvas.drawText(truncarTexto(familiar.grauParentesco, 18), MARGIN_LEFT + 428, y + 21, paintValue);

        // Linha 2: Telefone de Emergência
        canvas.drawText("Telefone de Emergência:", MARGIN_LEFT + 12, y + 42, paintLabel);
        paintValue.setColor(COLOR_PRIMARY_DARK);
        paintValue.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(truncarTexto(familiar.telefoneEmergencia, 45), MARGIN_LEFT + 138, y + 42, paintValue);

        return y + alturaCard + 12;
    }

    private static int desenharBlocoTabelaPrescricoes(
            Canvas canvas,
            List<Medicamento> listaMeds,
            int indiceInicial,
            int limiteLinhasPagina,
            int yInicial,
            boolean continuacao
    ) {
        String titulo = continuacao
                ? "3. PRESCRIÇÕES E MEDICAMENTOS EM USO (CONTINUAÇÃO)"
                : "3. TABELA DE PRESCRIÇÕES E MEDICAMENTOS EM USO";
        int y = desenharTituloBloco(canvas, titulo, yInicial);

        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintCellBold = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintCell = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Coordenadas X das 4 colunas da tabela (Largura útil total = 523pt)
        int colMedicamentoX = MARGIN_LEFT + 8;     // largura ~162pt
        int colDosagemX = MARGIN_LEFT + 174;       // largura ~106pt
        int colHorariosX = MARGIN_LEFT + 284;      // largura ~136pt
        int colFrequenciaX = MARGIN_LEFT + 424;    // largura ~95pt

        // Cabeçalho da Tabela
        int headerHeight = 22;
        paintBox.setColor(COLOR_PRIMARY);
        paintBox.setStyle(Paint.Style.FILL);
        RectF headerRect = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + headerHeight);
        canvas.drawRoundRect(headerRect, 4f, 4f, paintBox);

        paintHeader.setColor(Color.WHITE);
        paintHeader.setTextSize(9.2f);
        paintHeader.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        canvas.drawText("Medicamento", colMedicamentoX, y + 15, paintHeader);
        canvas.drawText("Dosagem", colDosagemX, y + 15, paintHeader);
        canvas.drawText("Horários", colHorariosX, y + 15, paintHeader);
        canvas.drawText("Frequência", colFrequenciaX, y + 15, paintHeader);

        y += headerHeight;

        paintBorder.setColor(COLOR_BORDER);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(0.7f);

        if (listaMeds.isEmpty()) {
            RectF emptyRect = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 34);
            canvas.drawRect(emptyRect, paintBorder);

            paintCell.setColor(COLOR_TEXT_MUTED);
            paintCell.setTextSize(9.5f);
            canvas.drawText(
                    "Nenhum medicamento ou prescrição vinculada a este residente no momento.",
                    MARGIN_LEFT + 72,
                    y + 21,
                    paintCell
            );
            return 0;
        }

        paintCellBold.setColor(COLOR_TEXT_DARK);
        paintCellBold.setTextSize(8.8f);
        paintCellBold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paintCell.setColor(COLOR_TEXT_MEDIUM);
        paintCell.setTextSize(8.6f);
        paintCell.setTypeface(Typeface.DEFAULT);

        int rowHeight = 23;
        int linhasDesenhadas = 0;
        int idx = indiceInicial;

        while (idx < listaMeds.size() && linhasDesenhadas < limiteLinhasPagina) {
            Medicamento med = listaMeds.get(idx);

            // Efeito zebra nas linhas pares visuais
            if (linhasDesenhadas % 2 == 1) {
                paintBox.setColor(COLOR_ROW_ZEBRA);
                paintBox.setStyle(Paint.Style.FILL);
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + rowHeight, paintBox);
            }

            // Borda da linha e divisórias verticais das colunas
            canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + rowHeight, paintBorder);
            canvas.drawLine(colDosagemX - 6, y, colDosagemX - 6, y + rowHeight, paintBorder);
            canvas.drawLine(colHorariosX - 6, y, colHorariosX - 6, y + rowHeight, paintBorder);
            canvas.drawLine(colFrequenciaX - 6, y, colFrequenciaX - 6, y + rowHeight, paintBorder);

            // Coluna 1: Medicamento + Forma Farmacêutica
            String nomeMed = valorOuPadrao(med.getNomeMedicamento(), "-");
            String tipoMed = med.getTipo();
            String medDescricao = (tipoMed != null && !tipoMed.isEmpty())
                    ? nomeMed + " (" + tipoMed + ")"
                    : nomeMed;
            canvas.drawText(truncarTexto(medDescricao, 28), colMedicamentoX, y + 15, paintCellBold);

            // Coluna 2: Dosagem
            String dosagem = valorOuPadrao(med.getDosagem(), "-");
            canvas.drawText(truncarTexto(dosagem, 19), colDosagemX, y + 15, paintCell);

            // Coluna 3: Horários diários calculados a partir do horário inicial e frequência
            String gradeHorarios = calcularGradeHorarios(med.getHorarioInicial(), med.getFrequenciaHoras());
            canvas.drawText(truncarTexto(gradeHorarios, 24), colHorariosX, y + 15, paintCell);

            // Coluna 4: Frequência (+ indicação caso suspenso)
            String freqTexto = formatarFrequencia(med.getFrequenciaHoras(), med.isAtivo());
            canvas.drawText(truncarTexto(freqTexto, 18), colFrequenciaX, y + 15, paintCell);

            y += rowHeight;
            idx++;
            linhasDesenhadas++;
        }

        return idx;
    }

    private static void desenharRodapeInstitucional(Canvas canvas, int paginaAtual, int totalPaginas) {
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Linhas de Assinatura Técnica
        int assinaturaY = 758;
        paintLine.setColor(COLOR_TEXT_MUTED);
        paintLine.setStrokeWidth(0.8f);

        canvas.drawLine(MARGIN_LEFT + 16, assinaturaY, MARGIN_LEFT + 236, assinaturaY, paintLine);
        canvas.drawLine(MARGIN_RIGHT - 236, assinaturaY, MARGIN_RIGHT - 16, assinaturaY, paintLine);

        paintText.setColor(COLOR_TEXT_MEDIUM);
        paintText.setTextSize(8.3f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(
                "Assinatura do Responsável Técnico / Enfermagem",
                MARGIN_LEFT + 22,
                assinaturaY + 13,
                paintText
        );
        canvas.drawText(
                "Direção / Administração — ILPI Vovô Lar",
                MARGIN_RIGHT - 214,
                assinaturaY + 13,
                paintText
        );

        // Barra divisória final e Paginação
        paintLine.setColor(COLOR_BORDER);
        paintLine.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, FOOTER_DIVIDER_Y, MARGIN_RIGHT, FOOTER_DIVIDER_Y, paintLine);

        paintText.setColor(COLOR_TEXT_MUTED);
        paintText.setTextSize(8f);
        paintText.setTypeface(Typeface.DEFAULT);
        canvas.drawText(
                "ILPI Vovô Lar • Prontuário Oficial Emitido Offline via CuidarPlus",
                MARGIN_LEFT,
                FOOTER_DIVIDER_Y + 15,
                paintText
        );

        String paginacao = "Página " + paginaAtual + " de " + totalPaginas;
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float larguraPag = paintText.measureText(paginacao);
        canvas.drawText(paginacao, MARGIN_RIGHT - larguraPag, FOOTER_DIVIDER_Y + 15, paintText);
    }

    private static int desenharTituloBloco(Canvas canvas, String titulo, int y) {
        Paint paintAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintTitle = new Paint(Paint.ANTI_ALIAS_FLAG);

        paintAccent.setColor(COLOR_PRIMARY);
        paintAccent.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_LEFT + 4, y + 14, paintAccent);

        paintTitle.setColor(COLOR_PRIMARY);
        paintTitle.setTextSize(10.2f);
        paintTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(titulo, MARGIN_LEFT + 10, y + 11, paintTitle);

        return y + 19;
    }

    // =========================================================================
    // ARMAZENAMENTO PRIVADO & EXPEDIÇÃO SEGURA (FILEPROVIDER)
    // =========================================================================

    /**
     * Cria o arquivo PDF dentro do diretório privado de documentos da aplicação:
     * context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).
     */
    private static File criarArquivoPdfPrivado(Context context, Idoso idoso) throws IOException {
        File pastaDocumentos = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (pastaDocumentos == null) {
            pastaDocumentos = new File(context.getFilesDir(), "Documents");
        }
        if (!pastaDocumentos.exists() && !pastaDocumentos.mkdirs()) {
            throw new IOException("Não foi possível criar o diretório de documentos: " + pastaDocumentos);
        }

        String nomeLimpo = sanitizarNomeArquivo(idoso.getNome());
        SimpleDateFormat sdfArquivo = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String nomeArquivo = "Prontuario_" + nomeLimpo + "_" + sdfArquivo.format(new Date()) + ".pdf";

        return new File(pastaDocumentos, nomeArquivo);
    }

    /**
     * Obtém a URI segura do arquivo PDF utilizando o FileProvider configurado no projeto.
     */
    public static Uri obterUriSeguraPdf(Context context, File arquivoPdf) {
        String authority = context.getPackageName() + ".fileprovider";
        return FileProvider.getUriForFile(context, authority, arquivoPdf);
    }

    /**
     * Abre o documento PDF gerado em um visualizador nativo via FileProvider e Intent.ACTION_VIEW
     * com a permissão temporária FLAG_GRANT_READ_URI_PERMISSION.
     */
    public static void abrirPdf(Context context, File arquivoPdf) {
        if (context == null || arquivoPdf == null || !arquivoPdf.exists()) {
            if (context != null) {
                Toast.makeText(context, "Arquivo PDF não encontrado no armazenamento.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try {
            Uri uriSegura = obterUriSeguraPdf(context, arquivoPdf);

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uriSegura, "application/pdf");
            viewIntent.setClipData(ClipData.newRawUri("Prontuário PDF", uriSegura));
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            if (!(context instanceof Activity)) {
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(viewIntent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Nenhum leitor de PDF instalado; abrindo seletor de compartilhamento.", e);
            Toast.makeText(
                    context,
                    "Nenhum leitor de PDF instalado. Selecione um aplicativo para compartilhar o documento.",
                    Toast.LENGTH_LONG
            ).show();
            compartilharPdf(context, arquivoPdf, null);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir prontuário PDF via FileProvider.", e);
            Toast.makeText(context, "Não foi possível abrir o arquivo PDF gerado.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Compartilha o documento PDF via WhatsApp, E-mail ou outros aplicativos compatíveis
     * utilizando Intent.ACTION_SEND e FileProvider com FLAG_GRANT_READ_URI_PERMISSION.
     */
    public static void compartilharPdf(Context context, File arquivoPdf, String nomeResidente) {
        if (context == null || arquivoPdf == null || !arquivoPdf.exists()) {
            if (context != null) {
                Toast.makeText(context, "Arquivo PDF não encontrado para compartilhamento.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try {
            Uri uriSegura = obterUriSeguraPdf(context, arquivoPdf);
            String identificacao = (nomeResidente != null && !nomeResidente.trim().isEmpty())
                    ? nomeResidente.trim()
                    : "Residente";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uriSegura);
            shareIntent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Prontuário do Residente - " + identificacao + " (ILPI Vovô Lar)"
            );
            shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Segue em anexo o Prontuário Individual de " + identificacao
                            + ", emitido pelo sistema CuidarPlus - ILPI Vovô Lar."
            );
            shareIntent.setClipData(ClipData.newRawUri("Prontuário PDF", uriSegura));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Compartilhar Prontuário em PDF");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar prontuário PDF.", e);
            Toast.makeText(context, "Não foi possível compartilhar o prontuário em PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // UTILITÁRIOS DE FORMATAÇÃO E CÁLCULO DE LAYOUT
    // =========================================================================

    public static int calcularTotalPaginas(int quantidadeMedicamentos) {
        if (quantidadeMedicamentos <= MAX_MEDS_PRIMEIRA_PAGINA) {
            return 1;
        }
        int restantes = quantidadeMedicamentos - MAX_MEDS_PRIMEIRA_PAGINA;
        return 1 + (int) Math.ceil((double) restantes / MAX_MEDS_PAGINA_CONTINUACAO);
    }

    /**
     * Gera a grade diária de horários de administração a partir do horário inicial e frequência.
     * Ex: horarioInicial="08:00", frequenciaHoras=8 -> "08:00, 16:00, 00:00"
     */
    public static String calcularGradeHorarios(String horarioInicial, int frequenciaHoras) {
        if (horarioInicial == null || horarioInicial.trim().isEmpty()) {
            return "-";
        }
        String limpo = horarioInicial.trim();
        if (frequenciaHoras <= 0 || frequenciaHoras >= 24) {
            return limpo;
        }

        try {
            String[] partes = limpo.split(":");
            if (partes.length == 2) {
                int horaBase = Integer.parseInt(partes[0].trim());
                int minutoBase = Integer.parseInt(partes[1].trim());
                int dosesDia = Math.min(6, 24 / frequenciaHoras);
                if (dosesDia <= 1) {
                    return limpo;
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < dosesDia; i++) {
                    if (i > 0) sb.append(", ");
                    int horaDose = (horaBase + (i * frequenciaHoras)) % 24;
                    sb.append(String.format(Locale.US, "%02d:%02d", horaDose, minutoBase));
                }
                return sb.toString();
            }
        } catch (NumberFormatException ignored) {
        }
        return limpo;
    }

    private static String formatarFrequencia(int frequenciaHoras, boolean ativo) {
        String base = frequenciaHoras > 0 ? ("A cada " + frequenciaHoras + "h") : "Uso contínuo";
        return ativo ? base : (base + " (Susp.)");
    }

    /**
     * Extrai Nome do Familiar, Grau de Parentesco e Telefone a partir dos campos do Idoso
     * ou por interpretação inteligente do campo contatoEmergencia (ex: "Filha Mariana - (51) 98765-4321").
     */
    static DadosFamiliarExtraidos extrairDadosFamiliar(Idoso idoso) {
        String nomeFam = idoso.getNomeFamiliar() != null ? idoso.getNomeFamiliar().trim() : "";
        String parentesco = idoso.getGrauParentesco() != null ? idoso.getGrauParentesco().trim() : "";
        String bruto = idoso.getContatoEmergencia() != null ? idoso.getContatoEmergencia().trim() : "";

        String telefone = bruto;

        if (!bruto.isEmpty()) {
            // Separa "Filha Mariana - (51) 98765-4321" caso haja separador " - "
            if (bruto.contains(" - ")) {
                String[] partes = bruto.split(" - ", 2);
                String prefixoTexto = partes[0].trim();
                telefone = partes[1].trim();

                if (nomeFam.isEmpty() && !prefixoTexto.isEmpty()) {
                    String[] palavras = prefixoTexto.split("\\s+", 2);
                    if (palavras.length == 2 && GRAUS_PARENTESCO_CONHECIDOS.contains(palavras[0].toLowerCase(Locale.ROOT))) {
                        if (parentesco.isEmpty()) {
                            parentesco = palavras[0];
                        }
                        nomeFam = palavras[1];
                    } else {
                        nomeFam = prefixoTexto;
                    }
                }
            }
        }

        if (nomeFam.isEmpty()) {
            nomeFam = bruto.isEmpty() ? "Não cadastrado" : "Familiar / Responsável";
        }
        if (parentesco.isEmpty()) {
            parentesco = bruto.isEmpty() ? "-" : "Responsável Legal";
        }
        if (telefone.isEmpty()) {
            telefone = "Nenhum telefone cadastrado";
        }

        return new DadosFamiliarExtraidos(nomeFam, parentesco, telefone);
    }

    private static List<String> quebrarTexto(String texto, Paint paint, float larguraMaxima, int maxLinhas) {
        List<String> linhas = new ArrayList<>();
        if (texto == null || texto.trim().isEmpty()) {
            linhas.add("-");
            return linhas;
        }

        String[] palavras = texto.trim().split("\\s+");
        StringBuilder linhaAtual = new StringBuilder();

        for (String palavra : palavras) {
            String tentativa = linhaAtual.length() == 0 ? palavra : linhaAtual + " " + palavra;
            if (paint.measureText(tentativa) <= larguraMaxima) {
                linhaAtual.setLength(0);
                linhaAtual.append(tentativa);
            } else {
                if (linhas.size() == maxLinhas - 1) {
                    linhas.add(truncarTexto(tentativa, 72));
                    return linhas;
                }
                if (linhaAtual.length() > 0) {
                    linhas.add(linhaAtual.toString());
                    linhaAtual.setLength(0);
                }
                linhaAtual.append(palavra);
            }
        }

        if (linhaAtual.length() > 0 && linhas.size() < maxLinhas) {
            linhas.add(linhaAtual.toString());
        }
        return linhas;
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return (valor != null && !valor.trim().isEmpty()) ? valor.trim() : padrao;
    }

    private static String truncarTexto(String texto, int limite) {
        if (texto == null) return "";
        if (texto.length() <= limite) return texto;
        return texto.substring(0, Math.max(0, limite - 3)) + "...";
    }

    private static String sanitizarNomeArquivo(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            return "Residente";
        }
        String semAcentos = Normalizer.normalize(nome.trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        String limpo = semAcentos.replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return limpo.isEmpty() ? "Residente" : limpo;
    }

    static final class DadosFamiliarExtraidos {
        final String nomeFamiliar;
        final String grauParentesco;
        final String telefoneEmergencia;

        DadosFamiliarExtraidos(String nomeFamiliar, String grauParentesco, String telefoneEmergencia) {
            this.nomeFamiliar = nomeFamiliar;
            this.grauParentesco = grauParentesco;
            this.telefoneEmergencia = telefoneEmergencia;
        }
    }
}
