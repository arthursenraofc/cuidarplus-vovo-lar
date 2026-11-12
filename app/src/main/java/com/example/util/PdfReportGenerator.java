package com.example.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utilitário nativo para Geração e Compartilhamento de Relatórios em PDF 100% Offline
 * para a ILPI Vovô Lar - Sistema CuidarPlus.
 *
 * Utiliza exclusivamente a API nativa android.graphics.pdf.PdfDocument, Canvas e Paint,
 * garantindo conformidade com a arquitetura Local-First (zero dependências externas ou na nuvem).
 */
public class PdfReportGenerator {

    private static final String TAG = "PdfReportGenerator";

    // Dimensões padrão A4 em pontos (72 DPI): 595 x 842 pt
    public static final int PAGE_WIDTH = 595;
    public static final int PAGE_HEIGHT = 842;

    // Margens da página
    public static final int MARGIN_LEFT = 40;
    public static final int MARGIN_RIGHT = 555;
    public static final int MARGIN_TOP = 40;
    public static final int MARGIN_BOTTOM = 800;
    public static final int CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT; // 515 pt

    // Paleta de Cores Institucionais
    private static final int COLOR_PRIMARY = Color.parseColor("#1565C0"); // Azul Institucional
    private static final int COLOR_PRIMARY_DARK = Color.parseColor("#0D47A1");
    private static final int COLOR_PRIMARY_LIGHT = Color.parseColor("#E3F2FD");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#212121");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#616161");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#9E9E9E");
    private static final int COLOR_DIVIDER = Color.parseColor("#E0E0E0");
    private static final int COLOR_CARD_BG = Color.parseColor("#F8F9FA");
    private static final int COLOR_ROW_ALT = Color.parseColor("#F5F9FD");

    // Cores de Status
    private static final int COLOR_STATUS_NORMAL = Color.parseColor("#2E7D32");     // Verde
    private static final int COLOR_STATUS_CRITICO = Color.parseColor("#C62828");    // Vermelho
    private static final int COLOR_STATUS_ALERTA = Color.parseColor("#EF6C00");     // Laranja/Alerta

    /**
     * 1. Gera o Relatório Geral de Estoque de Medicamentos da instituição em formato PDF.
     *
     * @param context Contexto da aplicação
     * @param listaMedicamentos Lista com todos os medicamentos cadastrados
     * @return Arquivo PDF gerado no armazenamento seguro do dispositivo
     * @throws IOException Se houver falha de escrita no armazenamento
     */
    public static File gerarRelatorioEstoque(Context context, List<Medicamento> listaMedicamentos) throws IOException {
        PdfDocument document = new PdfDocument();

        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        List<Medicamento> meds = listaMedicamentos != null ? listaMedicamentos : new ArrayList<>();
        int itensPorPagina = 18;
        int totalPaginas = Math.max(1, (int) Math.ceil((double) Math.max(1, meds.size()) / itensPorPagina));

        // Configuração de Pincéis (Paint)
        Paint paintHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintSub = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);

        int indiceItem = 0;

        for (int pag = 1; pag <= totalPaginas; pag++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pag).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // 1. Cabeçalho Oficial
            desenharCabecalhoOficial(canvas, "RELATÓRIO GERAL DE ESTOQUE DE MEDICAMENTOS", dataEmissao, pag, totalPaginas);

            int yAtual = 115;

            // 2. Resumo Informativo (Apenas na página 1)
            if (pag == 1) {
                int totalMeds = meds.size();
                int totalCriticos = 0;
                for (Medicamento m : meds) {
                    if (determinarStatusEstoque(m).equals("Estoque Crítico")) {
                        totalCriticos++;
                    }
                }

                paintBox.setColor(COLOR_CARD_BG);
                paintBox.setStyle(Paint.Style.FILL);
                RectF cardResumo = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 32);
                canvas.drawRoundRect(cardResumo, 6, 6, paintBox);

                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStyle(Paint.Style.STROKE);
                paintLine.setStrokeWidth(1f);
                canvas.drawRoundRect(cardResumo, 6, 6, paintLine);

                paintText.setTextSize(10.5f);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paintText.setColor(COLOR_TEXT_PRIMARY);
                canvas.drawText("Resumo do Estoque:", MARGIN_LEFT + 12, yAtual + 20, paintText);

                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setColor(COLOR_TEXT_SECONDARY);
                String txtResumo = "Total de Itens: " + totalMeds + "  |  Estoque Crítico / Reposição: " + totalCriticos;
                canvas.drawText(txtResumo, MARGIN_LEFT + 130, yAtual + 20, paintText);

                yAtual += 44;
            }

            // 3. Tabela de Medicamentos
            // Larguras das colunas (Total: 515pt):
            // 1. Medicamento: 145pt (x: 40 -> 185)
            // 2. Dosagem: 95pt (x: 185 -> 280)
            // 3. Estoque Atual: 85pt (x: 280 -> 365)
            // 4. Status: 100pt (x: 365 -> 465)
            // 5. Responsável/Idoso: 50pt (x: 465 -> 555)

            // Cabeçalho da Tabela
            paintBox.setColor(COLOR_PRIMARY);
            paintBox.setStyle(Paint.Style.FILL);
            RectF rectTabHeader = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 22);
            canvas.drawRoundRect(rectTabHeader, 4, 4, paintBox);

            paintHeader.setColor(Color.WHITE);
            paintHeader.setTextSize(9.5f);
            paintHeader.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            canvas.drawText("Medicamento", MARGIN_LEFT + 8, yAtual + 15, paintHeader);
            canvas.drawText("Dosagem", MARGIN_LEFT + 148, yAtual + 15, paintHeader);
            canvas.drawText("Estoque Atual", MARGIN_LEFT + 242, yAtual + 15, paintHeader);
            canvas.drawText("Status", MARGIN_LEFT + 328, yAtual + 15, paintHeader);
            canvas.drawText("Responsável / Idoso", MARGIN_LEFT + 418, yAtual + 15, paintHeader);

            yAtual += 24;

            // Linhas da Tabela
            int contadorLinhaNaPagina = 0;
            while (indiceItem < meds.size() && contadorLinhaNaPagina < itensPorPagina) {
                Medicamento med = meds.get(indiceItem);
                int alturaLinha = 24;

                // Fundo zebra alternado
                if (contadorLinhaNaPagina % 2 == 1) {
                    paintBox.setColor(COLOR_ROW_ALT);
                    paintBox.setStyle(Paint.Style.FILL);
                    canvas.drawRect(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + alturaLinha, paintBox);
                }

                // Divisória inferior
                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStrokeWidth(0.5f);
                canvas.drawLine(MARGIN_LEFT, yAtual + alturaLinha, MARGIN_RIGHT, yAtual + alturaLinha, paintLine);

                // Coluna 1: Nome do Medicamento
                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTextSize(9f);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                String nomeMed = truncarTexto(med.getNomeMedicamento(), 24);
                canvas.drawText(nomeMed, MARGIN_LEFT + 8, yAtual + 16, paintText);

                // Coluna 2: Dosagem
                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setColor(COLOR_TEXT_SECONDARY);
                String dosagem = truncarTexto(med.getDosagem() != null ? med.getDosagem() : "-", 16);
                canvas.drawText(dosagem, MARGIN_LEFT + 148, yAtual + 16, paintText);

                // Coluna 3: Estoque Atual
                String estoque = (med.getEstoque() != null && !med.getEstoque().trim().isEmpty())
                        ? med.getEstoque() : "Não informado";
                canvas.drawText(truncarTexto(estoque, 14), MARGIN_LEFT + 242, yAtual + 16, paintText);

                // Coluna 4: Status com badge colorido
                String status = determinarStatusEstoque(med);
                int corStatus = COLOR_STATUS_NORMAL;
                if (status.equals("Estoque Crítico")) {
                    corStatus = COLOR_STATUS_CRITICO;
                } else if (status.equals("Vencimento Próximo") || status.equals("Atenção / Baixo")) {
                    corStatus = COLOR_STATUS_ALERTA;
                }

                // Tag/Badge background
                paintBox.setColor(corStatus);
                paintBox.setStyle(Paint.Style.FILL);
                paintBox.setAlpha(28); // Leve transparência de fundo
                RectF badge = new RectF(MARGIN_LEFT + 325, yAtual + 4, MARGIN_LEFT + 410, yAtual + 20);
                canvas.drawRoundRect(badge, 4, 4, paintBox);
                paintBox.setAlpha(255);

                paintText.setColor(corStatus);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paintText.setTextSize(8.5f);
                canvas.drawText(status, MARGIN_LEFT + 330, yAtual + 15, paintText);

                // Coluna 5: Responsável / Residente
                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setTextSize(9f);
                String responsavel = med.getNomeIdoso() != null && !med.getNomeIdoso().trim().isEmpty()
                        ? med.getNomeIdoso() : "Geral / Sem vínculo";
                canvas.drawText(truncarTexto(responsavel, 18), MARGIN_LEFT + 418, yAtual + 16, paintText);

                yAtual += alturaLinha;
                indiceItem++;
                contadorLinhaNaPagina++;
            }

            // Se a lista estiver totalmente vazia
            if (meds.isEmpty()) {
                paintText.setColor(COLOR_TEXT_MUTED);
                paintText.setTextSize(11f);
                canvas.drawText("Nenhum medicamento registrado no estoque.", MARGIN_LEFT + 140, yAtual + 30, paintText);
            }

            // 4. Rodapé Oficial
            desenharRodapeOficial(canvas, pag, totalPaginas);

            document.finishPage(page);
        }

        // Salva o PDF no armazenamento seguro privado
        File arquivoPdf = criarArquivoDestino(context, "relatorio_estoque");
        try (FileOutputStream fos = new FileOutputStream(arquivoPdf)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }

        Log.i(TAG, "Relatório de Estoque gerado com sucesso em: " + arquivoPdf.getAbsolutePath());
        return arquivoPdf;
    }

    /**
     * 2. Gera a Ficha / Prontuário Individual do Idoso em formato PDF.
     *
     * @param context Contexto da aplicação
     * @param idoso Objeto do residente com dados cadastrais e de saúde
     * @param prescricoes Lista de medicamentos prescritos para este idoso
     * @return Arquivo PDF gerado no diretório seguro do aplicativo
     * @throws IOException Se houver falha de escrita no armazenamento
     */
    public static File gerarFichaIdoso(Context context, Idoso idoso, List<Medicamento> prescricoes) throws IOException {
        return FichaIdosoPdfGenerator.gerarProntuarioPdf(context, idoso, prescricoes);
    }

    @SuppressWarnings("unused")
    private static File gerarFichaIdosoLegado(Context context, Idoso idoso, List<Medicamento> prescricoes) throws IOException {
        if (idoso == null) {
            throw new IllegalArgumentException("O idoso não pode ser nulo para geração da ficha.");
        }

        PdfDocument document = new PdfDocument();

        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        List<Medicamento> listaMeds = prescricoes != null ? prescricoes : new ArrayList<>();
        int totalPaginas = listaMeds.size() > 10 ? 2 : 1;

        Paint paintTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // 1. Cabeçalho Oficial
        desenharCabecalhoOficial(canvas, "PRONTUÁRIO CLÍNICO E FICHA INDIVIDUAL DO RESIDENTE", dataEmissao, 1, totalPaginas);

        int yAtual = 110;

        // ==============================================================
        // SEÇÃO 1: DADOS PESSOAIS DO RESIDENTE
        // ==============================================================
        desenharTituloSecao(canvas, "1. DADOS PESSOAIS DO RESIDENTE", yAtual);
        yAtual += 26;

        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        RectF cardDados = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 76);
        canvas.drawRoundRect(cardDados, 6, 6, paintBox);

        paintLine.setColor(COLOR_DIVIDER);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(1f);
        canvas.drawRoundRect(cardDados, 6, 6, paintLine);

        // Linha 1: Nome Completo e Idade
        paintText.setTextSize(10f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Nome:", MARGIN_LEFT + 14, yAtual + 20, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_PRIMARY_DARK);
        paintText.setTextSize(11f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(idoso.getNome() != null ? idoso.getNome() : "-", MARGIN_LEFT + 55, yAtual + 20, paintText);

        paintText.setTextSize(10f);
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Idade:", MARGIN_LEFT + 340, yAtual + 20, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        int idade = idoso.calcularIdade();
        String idadeTxt = idade > 0 ? (idade + " anos") : "Não informada";
        canvas.drawText(idadeTxt, MARGIN_LEFT + 380, yAtual + 20, paintText);

        // Linha 2: Data de Nascimento e Tipo Sanguíneo
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Nascimento:", MARGIN_LEFT + 14, yAtual + 42, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        String dataNasc = idoso.getDataNascimento() != null && !idoso.getDataNascimento().isEmpty()
                ? idoso.getDataNascimento() : "-";
        canvas.drawText(dataNasc, MARGIN_LEFT + 86, yAtual + 42, paintText);

        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Tipo Sanguíneo:", MARGIN_LEFT + 220, yAtual + 42, paintText);

        String sangue = idoso.getTipoSanguineo() != null && !idoso.getTipoSanguineo().isEmpty()
                ? idoso.getTipoSanguineo() : "Não informado";
        paintText.setColor(COLOR_PRIMARY);
        canvas.drawText(sangue, MARGIN_LEFT + 310, yAtual + 42, paintText);

        // Linha 3: Quarto / Acomodação
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Acomodação / Leito:", MARGIN_LEFT + 14, yAtual + 64, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        canvas.drawText("Ala Geriátrica - Quarto Individual / Leito Padrão", MARGIN_LEFT + 130, yAtual + 64, paintText);

        yAtual += 90;

        // ==============================================================
        // SEÇÃO 2: CONTATO DO FAMILIAR / EMERGÊNCIA
        // ==============================================================
        desenharTituloSecao(canvas, "2. CONTATO DO FAMILIAR / RESPONSÁVEL DE EMERGÊNCIA", yAtual);
        yAtual += 26;

        RectF cardContato = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 44);
        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardContato, 6, 6, paintBox);
        canvas.drawRoundRect(cardContato, 6, 6, paintLine);

        paintText.setTextSize(10f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Responsável / Telefone:", MARGIN_LEFT + 14, yAtual + 27, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        String contato = idoso.getContatoEmergencia() != null && !idoso.getContatoEmergencia().trim().isEmpty()
                ? idoso.getContatoEmergencia() : "Nenhum contato cadastrado";
        canvas.drawText(contato, MARGIN_LEFT + 155, yAtual + 27, paintText);

        yAtual += 58;

        // ==============================================================
        // SEÇÃO 3: HISTÓRICO E OBSERVAÇÕES DE SAÚDE
        // ==============================================================
        desenharTituloSecao(canvas, "3. HISTÓRICO CLÍNICO, ALERGIAS E CUIDADOS ESPECIAIS", yAtual);
        yAtual += 26;

        RectF cardSaude = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 88);
        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardSaude, 6, 6, paintBox);
        canvas.drawRoundRect(cardSaude, 6, 6, paintLine);

        // Alergias
        paintText.setTextSize(10f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_STATUS_CRITICO);
        canvas.drawText("Alergias Conhecidas:", MARGIN_LEFT + 14, yAtual + 22, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_PRIMARY);
        String alergias = idoso.getAlergias() != null && !idoso.getAlergias().trim().isEmpty()
                ? idoso.getAlergias() : "Nenhuma alergia relatada";
        canvas.drawText(truncarTexto(alergias, 65), MARGIN_LEFT + 140, yAtual + 22, paintText);

        // Restrições Médicas e Cuidados
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(COLOR_TEXT_PRIMARY);
        canvas.drawText("Restrições / Cuidados:", MARGIN_LEFT + 14, yAtual + 48, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        String restricoes = idoso.getRestricoesMedicas() != null && !idoso.getRestricoesMedicas().trim().isEmpty()
                ? idoso.getRestricoesMedicas() : "Sem restrições médicas registradas";
        canvas.drawText(truncarTexto(restricoes, 62), MARGIN_LEFT + 140, yAtual + 48, paintText);

        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        paintText.setColor(COLOR_TEXT_MUTED);
        paintText.setTextSize(8.5f);
        canvas.drawText("* Ficha conferida pelo serviço de enfermagem da ILPI Vovô Lar.", MARGIN_LEFT + 14, yAtual + 76, paintText);

        yAtual += 102;

        // ==============================================================
        // SEÇÃO 4: TABELA DE MEDICAMENTOS EM USO
        // ==============================================================
        desenharTituloSecao(canvas, "4. TABELA DE MEDICAMENTOS EM USO / PRESCRIÇÃO ATIVA", yAtual);
        yAtual += 26;

        // Cabeçalho da Tabela de Medicamentos
        paintBox.setColor(COLOR_PRIMARY);
        paintBox.setStyle(Paint.Style.FILL);
        RectF tabHead = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 22);
        canvas.drawRoundRect(tabHead, 4, 4, paintBox);

        paintTitle.setColor(Color.WHITE);
        paintTitle.setTextSize(9.5f);
        paintTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        canvas.drawText("Medicamento", MARGIN_LEFT + 8, yAtual + 15, paintTitle);
        canvas.drawText("Dosagem / Forma", MARGIN_LEFT + 160, yAtual + 15, paintTitle);
        canvas.drawText("Horário Inicial", MARGIN_LEFT + 280, yAtual + 15, paintTitle);
        canvas.drawText("Intervalo", MARGIN_LEFT + 370, yAtual + 15, paintTitle);
        canvas.drawText("Status", MARGIN_LEFT + 445, yAtual + 15, paintTitle);

        yAtual += 24;

        if (listaMeds.isEmpty()) {
            paintText.setColor(COLOR_TEXT_MUTED);
            paintText.setTextSize(10f);
            paintText.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Nenhuma medicação ativa prescrita no momento.", MARGIN_LEFT + 130, yAtual + 24, paintText);
            yAtual += 36;
        } else {
            int maxMeds = Math.min(listaMeds.size(), 12);
            for (int i = 0; i < maxMeds; i++) {
                Medicamento med = listaMeds.get(i);
                int alturaLinha = 24;

                if (i % 2 == 1) {
                    paintBox.setColor(COLOR_ROW_ALT);
                    paintBox.setStyle(Paint.Style.FILL);
                    canvas.drawRect(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + alturaLinha, paintBox);
                }

                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStrokeWidth(0.5f);
                canvas.drawLine(MARGIN_LEFT, yAtual + alturaLinha, MARGIN_RIGHT, yAtual + alturaLinha, paintLine);

                // Col 1: Medicamento
                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paintText.setTextSize(9f);
                canvas.drawText(truncarTexto(med.getNomeMedicamento(), 26), MARGIN_LEFT + 8, yAtual + 16, paintText);

                // Col 2: Dosagem
                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setColor(COLOR_TEXT_SECONDARY);
                String dosagemStr = (med.getDosagem() != null ? med.getDosagem() : "-")
                        + " (" + (med.getTipo() != null ? med.getTipo() : "Comp.") + ")";
                canvas.drawText(truncarTexto(dosagemStr, 22), MARGIN_LEFT + 160, yAtual + 16, paintText);

                // Col 3: Horário Inicial
                String horario = med.getHorarioInicial() != null ? med.getHorarioInicial() : "-";
                canvas.drawText(horario, MARGIN_LEFT + 280, yAtual + 16, paintText);

                // Col 4: Frequência
                String freq = "A cada " + med.getFrequenciaHoras() + "h";
                canvas.drawText(freq, MARGIN_LEFT + 370, yAtual + 16, paintText);

                // Col 5: Status
                String statusAtivo = med.isAtivo() ? "Ativo" : "Suspenso";
                int corSt = med.isAtivo() ? COLOR_STATUS_NORMAL : COLOR_STATUS_CRITICO;
                paintText.setColor(corSt);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(statusAtivo, MARGIN_LEFT + 445, yAtual + 16, paintText);

                yAtual += alturaLinha;
            }
        }

        // Bloco de Assinaturas no final do Prontuário
        yAtual = Math.max(yAtual + 30, 710);
        paintLine.setColor(COLOR_TEXT_MUTED);
        paintLine.setStrokeWidth(0.8f);

        canvas.drawLine(MARGIN_LEFT + 20, yAtual, MARGIN_LEFT + 220, yAtual, paintLine);
        canvas.drawLine(MARGIN_LEFT + 280, yAtual, MARGIN_RIGHT - 20, yAtual, paintLine);

        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.5f);
        paintText.setTypeface(Typeface.DEFAULT);
        canvas.drawText("Enfermeiro(a) Responsável / COREN", MARGIN_LEFT + 38, yAtual + 14, paintText);
        canvas.drawText("Médico(a) Assistente / CRM", MARGIN_LEFT + 315, yAtual + 14, paintText);

        // Rodapé Oficial
        desenharRodapeOficial(canvas, 1, totalPaginas);

        document.finishPage(page);

        // Salva o PDF no armazenamento seguro do app
        File arquivoPdf = criarArquivoDestino(context, "prontuario_idoso_" + idoso.getId());
        try (FileOutputStream fos = new FileOutputStream(arquivoPdf)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }

        Log.i(TAG, "Prontuário gerado com sucesso em: " + arquivoPdf.getAbsolutePath());
        return arquivoPdf;
    }

    /**
     * 2. Método Utilitário para abrir ou compartilhar o arquivo PDF gerado
     * utilizando FileProvider e Intent.ACTION_VIEW com fallback para Intent.ACTION_SEND.
     *
     * @param context Contexto da Activity
     * @param filePdf Arquivo .pdf local
     */
    public static void abrirOuCompartilharPdf(Context context, File filePdf) {
        if (context == null) return;

        if (filePdf == null || !filePdf.exists()) {
            Toast.makeText(context, "Erro: Arquivo PDF não encontrado.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String authority = context.getPackageName() + ".fileprovider";
            Uri uri = FileProvider.getUriForFile(context, authority, filePdf);

            // Intent principal: Visualizar PDF
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            // Intent secundária opcional: Compartilhar diretamente
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Relatório CuidarPlus - ILPI Vovô Lar");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Seletor universal nativo do Android
            Intent chooserIntent = Intent.createChooser(viewIntent, "Abrir ou Exportar Relatório PDF");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{shareIntent});
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(chooserIntent);

        } catch (Exception e) {
            Log.e(TAG, "Falha ao abrir ou compartilhar PDF via FileProvider", e);
            // Fallback: Tentativa direta com ACTION_SEND
            try {
                String authority = context.getPackageName() + ".fileprovider";
                Uri uri = FileProvider.getUriForFile(context, authority, filePdf);
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("application/pdf");
                sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(sendIntent, "Enviar Relatório PDF"));
            } catch (Exception ex) {
                Toast.makeText(context, "Nenhum aplicativo encontrado para abrir arquivos PDF.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE DESENHO (CANVAS / PAINT)
    // =========================================================================

    private static void desenharCabecalhoOficial(Canvas canvas, String tituloDoc, String dataEmissao, int pagina, int totalPaginas) {
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Barra superior azul institucional
        paintBox.setColor(COLOR_PRIMARY_DARK);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT, MARGIN_TOP + 5, paintBox);

        // Nome da Instituição
        paintText.setColor(COLOR_PRIMARY_DARK);
        paintText.setTextSize(14f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("ILPI Vovô Lar - Sistema CuidarPlus", MARGIN_LEFT, MARGIN_TOP + 24, paintText);

        // Subtítulo do Documento
        paintText.setColor(COLOR_TEXT_PRIMARY);
        paintText.setTextSize(10.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(tituloDoc, MARGIN_LEFT, MARGIN_TOP + 40, paintText);

        // Data / Hora de Emissão
        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.5f);
        paintText.setTypeface(Typeface.DEFAULT);
        canvas.drawText("Emissão: " + dataEmissao + "  |  100% Offline (Local-First)", MARGIN_LEFT, MARGIN_TOP + 54, paintText);

        // Linha divisória fina do cabeçalho
        paintLine.setColor(COLOR_DIVIDER);
        paintLine.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, MARGIN_TOP + 62, MARGIN_RIGHT, MARGIN_TOP + 62, paintLine);
    }

    private static void desenharTituloSecao(Canvas canvas, String tituloSecao, int y) {
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBar = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Pequena barra vertical de destaque
        paintBar.setColor(COLOR_PRIMARY);
        paintBar.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_LEFT + 4, y + 16, paintBar);

        // Texto do Título da Seção
        paintText.setColor(COLOR_PRIMARY);
        paintText.setTextSize(11f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(tituloSecao, MARGIN_LEFT + 12, y + 13, paintText);
    }

    private static void desenharRodapeOficial(Canvas canvas, int pagina, int totalPaginas) {
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Linha divisória superior do rodapé
        paintLine.setColor(COLOR_DIVIDER);
        paintLine.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_LEFT, MARGIN_BOTTOM - 20, MARGIN_RIGHT, MARGIN_BOTTOM - 20, paintLine);

        // Informação institucional no rodapé
        paintText.setColor(COLOR_TEXT_MUTED);
        paintText.setTextSize(8f);
        paintText.setTypeface(Typeface.DEFAULT);
        canvas.drawText("ILPI Vovô Lar • Gestão Integrada de Cuidados e Prontuários • CuidarPlus", MARGIN_LEFT, MARGIN_BOTTOM - 6, paintText);

        // Numeração de Páginas: "Página X de Y"
        String numPagina = "Página " + pagina + " de " + totalPaginas;
        float larguraTexto = paintText.measureText(numPagina);
        canvas.drawText(numPagina, MARGIN_RIGHT - larguraTexto, MARGIN_BOTTOM - 6, paintText);
    }

    /**
     * Calcula o status de estoque baseado no texto ou quantidade.
     */
    private static String determinarStatusEstoque(Medicamento med) {
        if (med == null) return "Normal";
        String estoque = med.getEstoque() != null ? med.getEstoque().trim() : "";

        if (estoque.isEmpty()) {
            return "Estoque Crítico";
        }

        // Tenta extrair o primeiro número
        try {
            String apenasDigitos = estoque.replaceAll("[^0-9]", "");
            if (!apenasDigitos.isEmpty()) {
                int qtd = Integer.parseInt(apenasDigitos);
                if (qtd <= 5) {
                    return "Estoque Crítico";
                } else if (qtd <= 15) {
                    return "Vencimento Próximo";
                } else {
                    return "Normal";
                }
            }
        } catch (Exception ignored) {
        }

        if (estoque.toLowerCase().contains("crític") || estoque.toLowerCase().contains("acabando") || estoque.toLowerCase().contains("zero")) {
            return "Estoque Crítico";
        }
        if (estoque.toLowerCase().contains("pouco") || estoque.toLowerCase().contains("restam") || estoque.toLowerCase().contains("atenção")) {
            return "Vencimento Próximo";
        }

        return "Normal";
    }

    private static String truncarTexto(String texto, int maxChars) {
        if (texto == null) return "";
        if (texto.length() <= maxChars) return texto;
        return texto.substring(0, maxChars - 2) + "..";
    }

    private static File criarArquivoDestino(Context context, String prefixo) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String nomeArquivo = prefixo + "_" + System.currentTimeMillis() + ".pdf";
        return new File(dir, nomeArquivo);
    }
}
