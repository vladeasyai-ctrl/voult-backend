package com.example.vault.ai;

import com.example.vault.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class PdfDocumentExtractor {

    private static final int MIN_USABLE_TEXT_CHARS = 40;
    private static final int MAX_PAGES_FOR_TEXT = 20;
    private static final float RENDER_DPI = 144f;

    public PdfExtractionResult extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_ENCRYPTED",
                        "Password-protected PDFs are not supported");
            }

            int pageCount = document.getNumberOfPages();
            String text = extractText(document, pageCount);
            byte[] firstPagePng = pageCount > 0 ? renderFirstPage(document) : null;

            return new PdfExtractionResult(
                    text,
                    firstPagePng,
                    pageCount,
                    hasUsableText(text)
            );
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read PDF", e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_READ_FAILED",
                    "Failed to read PDF: " + e.getMessage());
        }
    }

    public boolean hasUsableText(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() >= MIN_USABLE_TEXT_CHARS;
    }

    private String extractText(PDDocument document, int pageCount) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(1);
        stripper.setEndPage(Math.min(pageCount, MAX_PAGES_FOR_TEXT));
        return stripper.getText(document).trim();
    }

    private byte[] renderFirstPage(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(0, RENDER_DPI, ImageType.RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    public record PdfExtractionResult(
            String text,
            byte[] firstPagePng,
            int pageCount,
            boolean hasTextLayer
    ) {
    }
}
