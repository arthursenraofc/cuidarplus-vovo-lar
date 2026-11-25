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

import com.example.model.DoseAlertaDTO;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.model.PassagemTurno;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
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
    private static final int COLOR_PRIMARY = Color.parseColor("#5B4397"); // Roxo M3 CuidarPlus
    private static final int COLOR_PRIMARY_DARK = Color.parseColor("#432C7A");
    private static final int COLOR_PRIMARY_LIGHT = Color.parseColor("#EDE7F6");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#1D1B20");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#49454F");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#726D78");
    private static final int COLOR_DIVIDER = Color.parseColor("#DDD7E4");
    private static final int COLOR_CARD_BG = Color.parseColor("#F7F2FA");
    private static final int COLOR_ROW_ALT = Color.parseColor("#F9F7FC");

    // Cores de Status
    private static final int COLOR_STATUS_NORMAL = Color.parseColor("#186A3B");     // Verde
    private static final int COLOR_STATUS_CRITICO = Color.parseColor("#B3261E");    // Vermelho
    private static final int COLOR_STATUS_ALERTA = Color.parseColor("#B06000");     // Âmbar/Alerta

    /**
     * 1. Gera o Relatório Geral de Estoque de Medicamentos da instituição em formato PDF,
     * respeitando o limite mínimo configurável no {@link EstoqueAlertaManager} e ordenando
     * os itens críticos primeiro para ação rápida da equipe.
     */
    public static File gerarRelatorioEstoque(Context context, List<Medicamento> listaMedicamentos) throws IOException {
        PdfDocument document = new PdfDocument();

        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(context);
        List<Medicamento> meds = listaMedicamentos != null ? new ArrayList<>(listaMedicamentos) : new ArrayList<>();
        meds.sort(Comparator.comparingInt((Medicamento m) -> prioridadeEstoque(context, m, limiteMinimo))
                .thenComparing(m -> m.getNomeMedicamento() != null ? m.getNomeMedicamento() : "", String.CASE_INSENSITIVE_ORDER));

        int totalCriticos = 0;
        int totalAtencao = 0;
        int totalNormais = 0;
        for (Medicamento m : meds) {
            String st = determinarStatusEstoque(context, m);
            if ("Estoque Crítico".equals(st)) {
                totalCriticos++;
            } else if ("Atenção / Baixo".equals(st)) {
                totalAtencao++;
            } else {
                totalNormais++;
            }
        }

        int itensPrimeiraPagina = 17;
        int itensDemaisPaginas = 21;
        int totalPaginas = 1;
        if (meds.size() > itensPrimeiraPagina) {
            int restantes = meds.size() - itensPrimeiraPagina;
            totalPaginas += (int) Math.ceil((double) restantes / itensDemaisPaginas);
        }

        Paint paintHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);

        int indiceItem = 0;

        for (int pag = 1; pag <= totalPaginas; pag++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pag).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            desenharCabecalhoOficial(canvas, "RELATÓRIO GERAL DE ESTOQUE DE MEDICAMENTOS", dataEmissao, pag, totalPaginas);

            int yAtual = 114;

            if (pag == 1) {
                paintBox.setColor(COLOR_CARD_BG);
                paintBox.setStyle(Paint.Style.FILL);
                RectF cardResumo = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 54);
                canvas.drawRoundRect(cardResumo, 8, 8, paintBox);

                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStyle(Paint.Style.STROKE);
                paintLine.setStrokeWidth(1f);
                canvas.drawRoundRect(cardResumo, 8, 8, paintLine);

                desenharKpiMiniCard(canvas, MARGIN_LEFT + 10, yAtual + 8, 118, "TOTAL DE ITENS",
                        String.valueOf(meds.size()), COLOR_PRIMARY);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 136, yAtual + 8, 118, "ESTOQUE CRÍTICO",
                        String.valueOf(totalCriticos), COLOR_STATUS_CRITICO);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 262, yAtual + 8, 118, "EM ATENÇÃO",
                        String.valueOf(totalAtencao), COLOR_STATUS_ALERTA);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 388, yAtual + 8, 117, "MÍN. CONFIGURADO",
                        limiteMinimo + " un. (OK: " + totalNormais + ")", COLOR_STATUS_NORMAL);

                yAtual += 66;
            }

            // Cabeçalho da Tabela (515pt total)
            paintBox.setColor(COLOR_PRIMARY);
            paintBox.setStyle(Paint.Style.FILL);
            RectF rectTabHeader = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 24);
            canvas.drawRoundRect(rectTabHeader, 4, 4, paintBox);

            paintHeader.setColor(Color.WHITE);
            paintHeader.setTextSize(9.5f);
            paintHeader.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            canvas.drawText("Medicamento", MARGIN_LEFT + 8, yAtual + 16, paintHeader);
            canvas.drawText("Dosagem", MARGIN_LEFT + 138, yAtual + 16, paintHeader);
            canvas.drawText("Estoque Atual", MARGIN_LEFT + 224, yAtual + 16, paintHeader);
            canvas.drawText("Status", MARGIN_LEFT + 312, yAtual + 16, paintHeader);
            canvas.drawText("Residente / Escopo", MARGIN_LEFT + 408, yAtual + 16, paintHeader);

            yAtual += 26;

            int limitePagina = (pag == 1) ? itensPrimeiraPagina : itensDemaisPaginas;
            int contadorLinhaNaPagina = 0;
            while (indiceItem < meds.size() && contadorLinhaNaPagina < limitePagina) {
                Medicamento med = meds.get(indiceItem);
                int alturaLinha = 26;

                if (contadorLinhaNaPagina % 2 == 1) {
                    paintBox.setColor(COLOR_ROW_ALT);
                    paintBox.setStyle(Paint.Style.FILL);
                    canvas.drawRect(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + alturaLinha, paintBox);
                }

                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStrokeWidth(0.5f);
                canvas.drawLine(MARGIN_LEFT, yAtual + alturaLinha, MARGIN_RIGHT, yAtual + alturaLinha, paintLine);

                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTextSize(9f);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(truncarTexto(med.getNomeMedicamento(), 22), MARGIN_LEFT + 8, yAtual + 17, paintText);

                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setColor(COLOR_TEXT_SECONDARY);
                String dosagem = med.getDosagem() != null ? med.getDosagem() : "-";
                canvas.drawText(truncarTexto(dosagem, 14), MARGIN_LEFT + 138, yAtual + 17, paintText);

                String estoque = (med.getEstoque() != null && !med.getEstoque().trim().isEmpty())
                        ? med.getEstoque() : "Não informado";
                canvas.drawText(truncarTexto(estoque, 15), MARGIN_LEFT + 224, yAtual + 17, paintText);

                String status = determinarStatusEstoque(context, med);
                int corStatus = COLOR_STATUS_NORMAL;
                if ("Estoque Crítico".equals(status)) {
                    corStatus = COLOR_STATUS_CRITICO;
                } else if ("Atenção / Baixo".equals(status)) {
                    corStatus = COLOR_STATUS_ALERTA;
                }

                paintBox.setColor(corStatus);
                paintBox.setStyle(Paint.Style.FILL);
                paintBox.setAlpha(28);
                RectF badge = new RectF(MARGIN_LEFT + 308, yAtual + 4, MARGIN_LEFT + 402, yAtual + 21);
                canvas.drawRoundRect(badge, 4, 4, paintBox);
                paintBox.setAlpha(255);

                paintText.setColor(corStatus);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paintText.setTextSize(8.2f);
                canvas.drawText(status, MARGIN_LEFT + 313, yAtual + 16, paintText);

                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTypeface(Typeface.DEFAULT);
                paintText.setTextSize(9f);
                String responsavel = med.getNomeIdoso() != null && !med.getNomeIdoso().trim().isEmpty()
                        ? med.getNomeIdoso() : "Geral / Instituição";
                canvas.drawText(truncarTexto(responsavel, 19), MARGIN_LEFT + 408, yAtual + 17, paintText);

                yAtual += alturaLinha;
                indiceItem++;
                contadorLinhaNaPagina++;
            }

            if (meds.isEmpty()) {
                paintText.setColor(COLOR_TEXT_MUTED);
                paintText.setTextSize(11f);
                paintText.setTypeface(Typeface.DEFAULT);
                canvas.drawText("Nenhum medicamento registrado no estoque.", MARGIN_LEFT + 140, yAtual + 30, paintText);
            }

            desenharRodapeOficial(canvas, pag, totalPaginas);
            document.finishPage(page);
        }

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
     * 1b. Gera o Relatório de Administração de Doses do Dia (Atrasadas, Pendentes e Concluídas).
     */
    public static File gerarRelatorioAdministracaoDoses(Context context, List<DoseAlertaDTO> dosesLista) throws IOException {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        List<DoseAlertaDTO> doses = dosesLista != null ? new ArrayList<>(dosesLista) : new ArrayList<>();
        int atrasadas = 0;
        int noHorario = 0;
        int concluidas = 0;
        for (DoseAlertaDTO d : doses) {
            if ("ATRASADO".equalsIgnoreCase(d.getStatusPontualidade())) {
                atrasadas++;
            } else if ("CONCLUIDO".equalsIgnoreCase(d.getStatusPontualidade()) || d.isAdministradoHoje()) {
                concluidas++;
            } else {
                noHorario++;
            }
        }
        int adesaoPct = doses.isEmpty() ? 100 : Math.round((concluidas * 100f) / doses.size());

        int itensPrimeiraPagina = 17;
        int itensDemaisPaginas = 21;
        int totalPaginas = 1;
        if (doses.size() > itensPrimeiraPagina) {
            totalPaginas += (int) Math.ceil((double) (doses.size() - itensPrimeiraPagina) / itensDemaisPaginas);
        }

        Paint paintHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);

        int indice = 0;
        for (int pag = 1; pag <= totalPaginas; pag++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pag).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            desenharCabecalhoOficial(canvas, "RELATÓRIO DE ADMINISTRAÇÃO DE DOSES DO DIA", dataEmissao, pag, totalPaginas);
            int yAtual = 114;

            if (pag == 1) {
                paintBox.setColor(COLOR_CARD_BG);
                paintBox.setStyle(Paint.Style.FILL);
                RectF cardResumo = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 54);
                canvas.drawRoundRect(cardResumo, 8, 8, paintBox);
                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStyle(Paint.Style.STROKE);
                paintLine.setStrokeWidth(1f);
                canvas.drawRoundRect(cardResumo, 8, 8, paintLine);

                desenharKpiMiniCard(canvas, MARGIN_LEFT + 10, yAtual + 8, 118, "DOSES DO DIA",
                        String.valueOf(doses.size()), COLOR_PRIMARY);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 136, yAtual + 8, 118, "ATRASADAS",
                        String.valueOf(atrasadas), COLOR_STATUS_CRITICO);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 262, yAtual + 8, 118, "NO HORÁRIO",
                        String.valueOf(noHorario), COLOR_STATUS_ALERTA);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 388, yAtual + 8, 117, "CONCLUÍDAS",
                        concluidas + " (" + adesaoPct + "%)", COLOR_STATUS_NORMAL);
                yAtual += 66;
            }

            paintBox.setColor(COLOR_PRIMARY);
            paintBox.setStyle(Paint.Style.FILL);
            RectF rectTabHeader = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 24);
            canvas.drawRoundRect(rectTabHeader, 4, 4, paintBox);

            paintHeader.setColor(Color.WHITE);
            paintHeader.setTextSize(9.5f);
            paintHeader.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            canvas.drawText("Residente", MARGIN_LEFT + 8, yAtual + 16, paintHeader);
            canvas.drawText("Medicamento", MARGIN_LEFT + 150, yAtual + 16, paintHeader);
            canvas.drawText("Dosagem", MARGIN_LEFT + 282, yAtual + 16, paintHeader);
            canvas.drawText("Horário", MARGIN_LEFT + 362, yAtual + 16, paintHeader);
            canvas.drawText("Status", MARGIN_LEFT + 422, yAtual + 16, paintHeader);
            yAtual += 26;

            int limitePag = (pag == 1) ? itensPrimeiraPagina : itensDemaisPaginas;
            int linhaPag = 0;
            while (indice < doses.size() && linhaPag < limitePag) {
                DoseAlertaDTO dose = doses.get(indice);
                int alturaLinha = 26;

                if (linhaPag % 2 == 1) {
                    paintBox.setColor(COLOR_ROW_ALT);
                    paintBox.setStyle(Paint.Style.FILL);
                    canvas.drawRect(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + alturaLinha, paintBox);
                }
                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStrokeWidth(0.5f);
                canvas.drawLine(MARGIN_LEFT, yAtual + alturaLinha, MARGIN_RIGHT, yAtual + alturaLinha, paintLine);

                paintText.setColor(COLOR_TEXT_PRIMARY);
                paintText.setTextSize(9f);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(truncarTexto(dose.getNomeIdoso(), 24), MARGIN_LEFT + 8, yAtual + 17, paintText);

                paintText.setTypeface(Typeface.DEFAULT);
                canvas.drawText(truncarTexto(dose.getNomeMedicamento(), 22), MARGIN_LEFT + 150, yAtual + 17, paintText);

                paintText.setColor(COLOR_TEXT_SECONDARY);
                canvas.drawText(truncarTexto(dose.getDosagem(), 13), MARGIN_LEFT + 282, yAtual + 17, paintText);
                canvas.drawText(dose.getHorarioPrevisto() != null ? dose.getHorarioPrevisto() : "--:--",
                        MARGIN_LEFT + 362, yAtual + 17, paintText);

                String st = dose.getStatusPontualidade() != null ? dose.getStatusPontualidade() : "NO_HORARIO";
                String labelSt;
                int corSt;
                if ("ATRASADO".equalsIgnoreCase(st)) {
                    labelSt = "ATRASADO";
                    corSt = COLOR_STATUS_CRITICO;
                } else if ("CONCLUIDO".equalsIgnoreCase(st) || dose.isAdministradoHoje()) {
                    labelSt = "CONCLUÍDO";
                    corSt = COLOR_STATUS_NORMAL;
                } else {
                    labelSt = "NO HORÁRIO";
                    corSt = COLOR_STATUS_ALERTA;
                }

                paintBox.setColor(corSt);
                paintBox.setStyle(Paint.Style.FILL);
                paintBox.setAlpha(28);
                RectF badge = new RectF(MARGIN_LEFT + 418, yAtual + 4, MARGIN_RIGHT - 6, yAtual + 21);
                canvas.drawRoundRect(badge, 4, 4, paintBox);
                paintBox.setAlpha(255);

                paintText.setColor(corSt);
                paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paintText.setTextSize(8.2f);
                canvas.drawText(labelSt, MARGIN_LEFT + 425, yAtual + 16, paintText);

                yAtual += alturaLinha;
                indice++;
                linhaPag++;
            }

            if (doses.isEmpty()) {
                paintText.setColor(COLOR_TEXT_MUTED);
                paintText.setTextSize(11f);
                paintText.setTypeface(Typeface.DEFAULT);
                canvas.drawText("Nenhuma prescrição ativa para administração hoje.", MARGIN_LEFT + 120, yAtual + 30, paintText);
            }

            desenharRodapeOficial(canvas, pag, totalPaginas);
            document.finishPage(page);
        }

        File arquivoPdf = criarArquivoDestino(context, "relatorio_doses_dia");
        try (FileOutputStream fos = new FileOutputStream(arquivoPdf)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }
        return arquivoPdf;
    }

    /**
     * 1c. Gera o Relatório de Passagem de Turno e Observações da Equipe em PDF offline.
     */
    public static File gerarRelatorioPassagemTurno(Context context, List<PassagemTurno> passagensLista) throws IOException {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        List<PassagemTurno> passagens = passagensLista != null ? passagensLista : new ArrayList<>();
        int pendentes = 0;
        int urgentes = 0;
        for (PassagemTurno pt : passagens) {
            if (!pt.isConferido()) {
                pendentes++;
                if (PassagemTurno.PRIORIDADE_URGENTE.equalsIgnoreCase(pt.getPrioridade())) {
                    urgentes++;
                }
            }
        }

        int itensPorPagina = 6;
        int totalPaginas = Math.max(1, (int) Math.ceil((double) Math.max(1, passagens.size()) / itensPorPagina));

        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        int idx = 0;
        for (int pag = 1; pag <= totalPaginas; pag++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pag).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            desenharCabecalhoOficial(canvas, "RELATÓRIO DE PASSAGEM DE TURNO E OBSERVAÇÕES", dataEmissao, pag, totalPaginas);
            int yAtual = 114;

            if (pag == 1) {
                paintBox.setColor(COLOR_CARD_BG);
                paintBox.setStyle(Paint.Style.FILL);
                RectF cardResumo = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + 54);
                canvas.drawRoundRect(cardResumo, 8, 8, paintBox);
                paintLine.setColor(COLOR_DIVIDER);
                paintLine.setStyle(Paint.Style.STROKE);
                paintLine.setStrokeWidth(1f);
                canvas.drawRoundRect(cardResumo, 8, 8, paintLine);

                desenharKpiMiniCard(canvas, MARGIN_LEFT + 10, yAtual + 8, 160, "TOTAL DE REGISTROS",
                        String.valueOf(passagens.size()), COLOR_PRIMARY);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 178, yAtual + 8, 160, "AGUARDANDO CONFERÊNCIA",
                        String.valueOf(pendentes), COLOR_STATUS_ALERTA);
                desenharKpiMiniCard(canvas, MARGIN_LEFT + 346, yAtual + 8, 159, "URGENTES ATIVOS",
                        String.valueOf(urgentes), COLOR_STATUS_CRITICO);
                yAtual += 68;
            }

            if (passagens.isEmpty()) {
                paintText.setColor(COLOR_TEXT_MUTED);
                paintText.setTextSize(11f);
                canvas.drawText("Nenhum registro de passagem de turno encontrado.", MARGIN_LEFT + 120, yAtual + 30, paintText);
            } else {
                int countPag = 0;
                while (idx < passagens.size() && countPag < itensPorPagina && yAtual + 92 < MARGIN_BOTTOM - 26) {
                    PassagemTurno pt = passagens.get(idx);
                    int alturaCard = 92;

                    paintBox.setColor(Color.WHITE);
                    paintBox.setStyle(Paint.Style.FILL);
                    RectF card = new RectF(MARGIN_LEFT, yAtual, MARGIN_RIGHT, yAtual + alturaCard);
                    canvas.drawRoundRect(card, 6, 6, paintBox);

                    int corBorda = PassagemTurno.PRIORIDADE_URGENTE.equalsIgnoreCase(pt.getPrioridade()) && !pt.isConferido()
                            ? COLOR_STATUS_CRITICO : COLOR_DIVIDER;
                    paintLine.setColor(corBorda);
                    paintLine.setStyle(Paint.Style.STROKE);
                    paintLine.setStrokeWidth(1f);
                    canvas.drawRoundRect(card, 6, 6, paintLine);

                    paintText.setTextSize(9.5f);
                    paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    paintText.setColor(COLOR_PRIMARY_DARK);
                    canvas.drawText("Turno: " + pt.getTurno() + "  |  " + truncarTexto(pt.getNomeIdoso(), 36),
                            MARGIN_LEFT + 10, yAtual + 18, paintText);

                    paintText.setTextSize(8.5f);
                    int corPrio = PassagemTurno.PRIORIDADE_URGENTE.equalsIgnoreCase(pt.getPrioridade())
                            ? COLOR_STATUS_CRITICO
                            : (PassagemTurno.PRIORIDADE_ATENCAO.equalsIgnoreCase(pt.getPrioridade()) ? COLOR_STATUS_ALERTA : COLOR_STATUS_NORMAL);
                    paintText.setColor(corPrio);
                    String prioTxt = pt.getPrioridade() + " • " + pt.getDataHoraRegistro();
                    float wPrio = paintText.measureText(prioTxt);
                    canvas.drawText(prioTxt, MARGIN_RIGHT - 10 - wPrio, yAtual + 18, paintText);

                    paintText.setTypeface(Typeface.DEFAULT);
                    paintText.setColor(COLOR_TEXT_SECONDARY);
                    canvas.drawText("Origem: " + truncarTexto(pt.getProfissionalOrigem(), 42),
                            MARGIN_LEFT + 10, yAtual + 34, paintText);

                    paintText.setColor(COLOR_TEXT_PRIMARY);
                    canvas.drawText("Obs: " + truncarTexto(pt.getObservacoes(), 78),
                            MARGIN_LEFT + 10, yAtual + 50, paintText);

                    String linhaExtra = !pt.getPendenciasProximoTurno().isEmpty()
                            ? "Pendência: " + pt.getPendenciasProximoTurno()
                            : (!pt.getIntercorrencias().isEmpty() ? "Intercorrências: " + pt.getIntercorrencias() : "Sem pendências registradas");
                    paintText.setColor(COLOR_STATUS_ALERTA);
                    canvas.drawText(truncarTexto(linhaExtra, 82), MARGIN_LEFT + 10, yAtual + 65, paintText);

                    paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    if (pt.isConferido()) {
                        paintText.setColor(COLOR_STATUS_NORMAL);
                        canvas.drawText("Conferido por " + pt.getProfissionalDestino() + " em " + pt.getDataHoraConferencia(),
                                MARGIN_LEFT + 10, yAtual + 81, paintText);
                    } else {
                        paintText.setColor(COLOR_STATUS_CRITICO);
                        canvas.drawText("Aguardando confirmação de recebimento do próximo turno",
                                MARGIN_LEFT + 10, yAtual + 81, paintText);
                    }

                    yAtual += alturaCard + 10;
                    idx++;
                    countPag++;
                }
            }

            desenharRodapeOficial(canvas, pag, totalPaginas);
            document.finishPage(page);
        }

        File arquivoPdf = criarArquivoDestino(context, "relatorio_passagem_turno");
        try (FileOutputStream fos = new FileOutputStream(arquivoPdf)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }
        return arquivoPdf;
    }

    /**
     * 1d. Gera o Relatório Gerencial Consolidado da ILPI Vovô Lar (Dashboard + Estoque + Doses + Passagem de Turno).
     */
    public static File gerarRelatorioGerencialIlpi(
            Context context,
            List<Idoso> idososLista,
            List<Medicamento> medsLista,
            List<DoseAlertaDTO> dosesLista,
            List<PassagemTurno> passagensLista
    ) throws IOException {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault());
        String dataEmissao = sdfDataHora.format(new Date());

        List<Idoso> idosos = idososLista != null ? idososLista : new ArrayList<>();
        List<Medicamento> meds = medsLista != null ? medsLista : new ArrayList<>();
        List<DoseAlertaDTO> doses = dosesLista != null ? dosesLista : new ArrayList<>();
        List<PassagemTurno> passagens = passagensLista != null ? passagensLista : new ArrayList<>();

        int capacidade = DashboardManager.getCapacidadeTotalVagas(context);
        DashboardManager.ResumoVagas vagas = DashboardManager.calcularResumoVagas(idosos.size(), capacidade);
        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(context);
        EstoqueAlertaManager.ResumoAlertaEstoque resumoEstoque = EstoqueAlertaManager.avaliarMedicamentos(meds, limiteMinimo);

        int somaIdades = 0;
        int comIdadeValida = 0;
        int comAlergias = 0;
        for (Idoso i : idosos) {
            int idade = i.calcularIdade();
            if (idade > 0) {
                somaIdades += idade;
                comIdadeValida++;
            }
            if (i.getAlergias() != null && !i.getAlergias().trim().isEmpty()
                    && !i.getAlergias().toLowerCase().contains("nenhuma")) {
                comAlergias++;
            }
        }
        int mediaIdade = comIdadeValida > 0 ? Math.round((float) somaIdades / comIdadeValida) : 0;
        List<DashboardManager.AniversarianteMes> anivs =
                DashboardManager.filtrarAniversariantesDoMes(idosos, Calendar.getInstance());

        int dosesAtrasadas = 0;
        int dosesConcluidas = 0;
        for (DoseAlertaDTO d : doses) {
            if ("ATRASADO".equalsIgnoreCase(d.getStatusPontualidade())) {
                dosesAtrasadas++;
            } else if ("CONCLUIDO".equalsIgnoreCase(d.getStatusPontualidade()) || d.isAdministradoHoje()) {
                dosesConcluidas++;
            }
        }

        int passagensPendentes = 0;
        for (PassagemTurno pt : passagens) {
            if (!pt.isConferido()) passagensPendentes++;
        }

        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        desenharCabecalhoOficial(canvas, "RELATÓRIO GERENCIAL CONSOLIDADO DA INSTITUIÇÃO", dataEmissao, 1, 1);

        int y = 114;

        // Seção 1: Ocupação e Perfil Clínico dos Residentes
        desenharTituloSecao(canvas, "1. OCUPAÇÃO DE LEITOS E PERFIL DOS RESIDENTES", y);
        y += 24;

        desenharKpiMiniCard(canvas, MARGIN_LEFT, y, 123, "RESIDENTES", String.valueOf(idosos.size()), COLOR_PRIMARY);
        desenharKpiMiniCard(canvas, MARGIN_LEFT + 130, y, 123, "OCUPAÇÃO VAGAS",
                vagas.getPercentualOcupacao() + "% (" + vagas.getVagasOcupadas() + "/" + vagas.getCapacidadeTotal() + ")", COLOR_PRIMARY_DARK);
        desenharKpiMiniCard(canvas, MARGIN_LEFT + 260, y, 123, "MÉDIA DE IDADE",
                mediaIdade > 0 ? mediaIdade + " anos" : "-", COLOR_TEXT_PRIMARY);
        desenharKpiMiniCard(canvas, MARGIN_LEFT + 390, y, 125, "COM ALERGIAS / ANIV.",
                comAlergias + " alerg. • " + anivs.size() + " aniv.", COLOR_STATUS_ALERTA);
        y += 54;

        // Seção 2: Controle de Doses do Dia
        desenharTituloSecao(canvas, "2. ADMINISTRAÇÃO DE MEDICAMENTOS E PONTUALIDADE", y);
        y += 24;

        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        RectF boxDoses = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 84);
        canvas.drawRoundRect(boxDoses, 6, 6, paintBox);
        paintLine.setColor(COLOR_DIVIDER);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(1f);
        canvas.drawRoundRect(boxDoses, 6, 6, paintLine);

        paintText.setTextSize(9.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(dosesAtrasadas > 0 ? COLOR_STATUS_CRITICO : COLOR_STATUS_NORMAL);
        canvas.drawText("Doses Programadas Hoje: " + doses.size()
                        + "   |   Atrasadas: " + dosesAtrasadas
                        + "   |   Concluídas: " + dosesConcluidas,
                MARGIN_LEFT + 12, y + 20, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.8f);
        int linhaDoseY = y + 38;
        int exibidasDose = 0;
        for (DoseAlertaDTO d : doses) {
            if ("ATRASADO".equalsIgnoreCase(d.getStatusPontualidade()) && exibidasDose < 3) {
                canvas.drawText("• [ATRASADO " + d.getHorarioPrevisto() + "] "
                                + truncarTexto(d.getNomeIdoso(), 26) + " — "
                                + truncarTexto(d.getNomeMedicamento() + " (" + d.getDosagem() + ")", 36),
                        MARGIN_LEFT + 14, linhaDoseY, paintText);
                linhaDoseY += 15;
                exibidasDose++;
            }
        }
        if (exibidasDose == 0) {
            canvas.drawText("Nenhuma dose em atraso no momento. Equipe com administração em dia.",
                    MARGIN_LEFT + 14, y + 44, paintText);
        }
        y += 98;

        // Seção 3: Monitoramento de Estoque Crítico
        desenharTituloSecao(canvas, "3. MONITORAMENTO DE ESTOQUE CRÍTICO (MÍNIMO: " + limiteMinimo + " UN.)", y);
        y += 24;

        RectF boxEst = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 92);
        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(boxEst, 6, 6, paintBox);
        canvas.drawRoundRect(boxEst, 6, 6, paintLine);

        paintText.setTextSize(9.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(resumoEstoque.possuiAlertaCritico() ? COLOR_STATUS_CRITICO : COLOR_STATUS_NORMAL);
        canvas.drawText("Itens em Inventário: " + meds.size()
                        + "   |   Abaixo do Mínimo: " + resumoEstoque.getQuantidadeAbaixoDoLimite(),
                MARGIN_LEFT + 12, y + 20, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.8f);
        int linhaEstY = y + 38;
        List<Medicamento> criticos = resumoEstoque.getMedicamentosAbaixoDoLimite();
        if (criticos.isEmpty()) {
            canvas.drawText("Todos os medicamentos cadastrados estão acima do limite mínimo configurado.",
                    MARGIN_LEFT + 14, y + 44, paintText);
        } else {
            for (int i = 0; i < Math.min(3, criticos.size()); i++) {
                Medicamento m = criticos.get(i);
                canvas.drawText("• " + truncarTexto(m.getNomeMedicamento(), 26)
                                + " (" + truncarTexto(m.getEstoque(), 16) + ") — Residente: "
                                + truncarTexto(m.getNomeIdoso() != null ? m.getNomeIdoso() : "Geral", 24),
                        MARGIN_LEFT + 14, linhaEstY, paintText);
                linhaEstY += 15;
            }
        }
        y += 106;

        // Seção 4: Passagem de Turno
        desenharTituloSecao(canvas, "4. PASSAGEM DE TURNO E PENDÊNCIAS DA EQUIPE", y);
        y += 24;

        RectF boxPt = new RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 96);
        paintBox.setColor(COLOR_CARD_BG);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(boxPt, 6, 6, paintBox);
        canvas.drawRoundRect(boxPt, 6, 6, paintLine);

        paintText.setTextSize(9.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paintText.setColor(passagensPendentes > 0 ? COLOR_STATUS_ALERTA : COLOR_STATUS_NORMAL);
        canvas.drawText("Registros de Turno: " + passagens.size()
                        + "   |   Aguardando Conferência: " + passagensPendentes,
                MARGIN_LEFT + 12, y + 20, paintText);

        paintText.setTypeface(Typeface.DEFAULT);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.8f);
        int linhaPtY = y + 38;
        if (passagens.isEmpty()) {
            canvas.drawText("Nenhum registro de passagem de turno encontrado.", MARGIN_LEFT + 14, y + 44, paintText);
        } else {
            for (int i = 0; i < Math.min(3, passagens.size()); i++) {
                PassagemTurno pt = passagens.get(i);
                String statusPt = pt.isConferido() ? "Conferido" : "PENDENTE";
                canvas.drawText("• [" + pt.getTurno() + " • " + statusPt + "] "
                                + truncarTexto(pt.getNomeIdoso(), 20) + ": "
                                + truncarTexto(pt.getObservacoes(), 44),
                        MARGIN_LEFT + 14, linhaPtY, paintText);
                linhaPtY += 16;
            }
        }

        // Assinaturas
        int yAssin = Math.max(y + 135, 725);
        paintLine.setColor(COLOR_TEXT_MUTED);
        paintLine.setStrokeWidth(0.8f);
        canvas.drawLine(MARGIN_LEFT + 20, yAssin, MARGIN_LEFT + 220, yAssin, paintLine);
        canvas.drawLine(MARGIN_LEFT + 280, yAssin, MARGIN_RIGHT - 20, yAssin, paintLine);
        paintText.setColor(COLOR_TEXT_SECONDARY);
        paintText.setTextSize(8.5f);
        canvas.drawText("Coordenação Geral / ILPI Vovô Lar", MARGIN_LEFT + 40, yAssin + 14, paintText);
        canvas.drawText("Responsável Técnico de Enfermagem", MARGIN_LEFT + 305, yAssin + 14, paintText);

        desenharRodapeOficial(canvas, 1, 1);
        document.finishPage(page);

        File arquivoPdf = criarArquivoDestino(context, "relatorio_gerencial_ilpi");
        try (FileOutputStream fos = new FileOutputStream(arquivoPdf)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }
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

    private static void desenharKpiMiniCard(
            Canvas canvas,
            int x,
            int y,
            int largura,
            String rotulo,
            String valor,
            int corDestaque
    ) {
        Paint paintBox = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);

        RectF rect = new RectF(x, y, x + largura, y + 38);
        paintBox.setColor(Color.WHITE);
        paintBox.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, 6, 6, paintBox);

        paintLine.setColor(COLOR_DIVIDER);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(0.8f);
        canvas.drawRoundRect(rect, 6, 6, paintLine);

        paintText.setColor(COLOR_TEXT_MUTED);
        paintText.setTextSize(7.2f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(rotulo, x + 8, y + 13, paintText);

        paintText.setColor(corDestaque);
        paintText.setTextSize(10.5f);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(truncarTexto(valor, 20), x + 8, y + 29, paintText);
    }

    private static int prioridadeEstoque(Context context, Medicamento med, int limiteMinimo) {
        if (EstoqueAlertaManager.estaAbaixoDoLimiteMinimo(med, limiteMinimo)) {
            return 0;
        }
        String st = determinarStatusEstoque(context, med);
        if ("Atenção / Baixo".equals(st)) {
            return 1;
        }
        return 2;
    }

    /**
     * Calcula o status de estoque respeitando o limite mínimo configurável no EstoqueAlertaManager.
     */
    private static String determinarStatusEstoque(Context context, Medicamento med) {
        if (med == null) return "Normal";
        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(context);
        if (EstoqueAlertaManager.estaAbaixoDoLimiteMinimo(med, limiteMinimo)) {
            return "Estoque Crítico";
        }
        int qtd = EstoqueAlertaManager.extrairQuantidadeNumerica(med.getEstoque());
        if (qtd <= limiteMinimo + 5) {
            return "Atenção / Baixo";
        }
        String estoque = med.getEstoque() != null ? med.getEstoque().toLowerCase(Locale.getDefault()) : "";
        if (estoque.contains("pouco") || estoque.contains("restam") || estoque.contains("atenção")) {
            return "Atenção / Baixo";
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
