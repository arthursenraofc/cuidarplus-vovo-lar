package com.example;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shadow de suporte para testes locais JVM (Robolectric), permitindo que o código
 * de produção em FichaIdosoPdfGenerator utilize 100% a API nativa android.graphics.pdf.PdfDocument
 * desenhando em um Canvas/Bitmap real durante a execução dos testes unitários.
 */
@Implements(PdfDocument.class)
public class ShadowNativePdfDocument {

    private final List<PdfDocument.PageInfo> finishedPages = new ArrayList<>();
    private PdfDocument.Page activePage;
    private boolean closed = false;

    @Implementation
    protected void __constructor__() {
        // Inicializa estado em memória sem depender da lib JNI de PDF ausente na JVM de teste
    }

    @Implementation
    public PdfDocument.Page startPage(PdfDocument.PageInfo pageInfo) {
        if (closed) {
            throw new IllegalStateException("document is closed!");
        }
        if (activePage != null) {
            throw new IllegalStateException("page is not finished!");
        }
        if (pageInfo == null) {
            throw new IllegalArgumentException("pageInfo cannot be null");
        }

        Bitmap pageBitmap = Bitmap.createBitmap(
                pageInfo.getPageWidth(),
                pageInfo.getPageHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(pageBitmap);

        try {
            Constructor<PdfDocument.Page> ctor = PdfDocument.Page.class.getDeclaredConstructor(
                    Canvas.class,
                    PdfDocument.PageInfo.class
            );
            ctor.setAccessible(true);
            activePage = ctor.newInstance(canvas, pageInfo);
            return activePage;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao instanciar PdfDocument.Page no ambiente Robolectric", e);
        }
    }

    @Implementation
    public void finishPage(PdfDocument.Page page) {
        if (closed) {
            throw new IllegalStateException("document is closed!");
        }
        if (page == null || page != activePage) {
            throw new IllegalStateException("invalid page");
        }
        finishedPages.add(page.getInfo());
        activePage = null;
    }

    @Implementation
    public List<PdfDocument.PageInfo> getPages() {
        return new ArrayList<>(finishedPages);
    }

    @Implementation
    public void writeTo(OutputStream out) throws IOException {
        if (closed) {
            throw new IllegalStateException("document is closed!");
        }
        if (activePage != null) {
            throw new IllegalStateException("Current page not finished!");
        }
        String minimalPdf = "%PDF-1.4\n"
                + "% CuidarPlus Native PdfDocument Pages=" + finishedPages.size() + "\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Count " + finishedPages.size() + " >> endobj\n"
                + "%%EOF\n";
        out.write(minimalPdf.getBytes(StandardCharsets.US_ASCII));
    }

    @Implementation
    public void close() {
        activePage = null;
        closed = true;
    }
}
