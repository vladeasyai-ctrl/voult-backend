package com.example.vault.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Component
public class DocumentTextExtractor {

    private static final int MAX_CHARS = 50_000;

    public boolean canExtract(String mimeType, String filename) {
        String effectiveName = effectiveFilename(filename, mimeType);
        return isPlainText(mimeType, effectiveName)
                || isWord(mimeType, effectiveName)
                || isExcel(mimeType, effectiveName);
    }

    public String extract(byte[] content, String mimeType, String filename) {
        if (content == null || content.length == 0) {
            return "";
        }

        String safeName = effectiveFilename(filename, mimeType);
        String lowerName = safeName.toLowerCase(Locale.ROOT);
        String resolvedMime = mimeType != null ? mimeType : "application/octet-stream";

        try {
            if (isPlainText(resolvedMime, lowerName)) {
                return limitLength(decodeText(content));
            }
            if (isWord(resolvedMime, lowerName) || isDocxZip(content)) {
                String wordText = extractWord(content, lowerName, resolvedMime);
                if (!wordText.isBlank()) {
                    return limitLength(wordText);
                }
            }
            if (isExcel(resolvedMime, lowerName)) {
                return limitLength(extractExcel(content, lowerName, resolvedMime));
            }
        } catch (Throwable e) {
            log.warn("Failed to extract text from {}: {}", safeName, e.toString(), e);
        }

        return "";
    }

    private String effectiveFilename(String filename, String mimeType) {
        if (filename != null && filename.contains(".")) {
            return filename;
        }
        String extension = extensionFromMime(mimeType);
        if (extension == null) {
            return filename != null && !filename.isBlank() ? filename : "file";
        }
        String base = filename != null && !filename.isBlank() ? filename : "file";
        return base + extension;
    }

    private String extensionFromMime(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String lowerMime = mimeType.toLowerCase(Locale.ROOT);
        if (lowerMime.contains("wordprocessingml")) {
            return ".docx";
        }
        if (lowerMime.contains("msword")) {
            return ".doc";
        }
        if (lowerMime.contains("spreadsheetml")) {
            return ".xlsx";
        }
        if (lowerMime.contains("ms-excel")) {
            return ".xls";
        }
        if (lowerMime.contains("csv")) {
            return ".csv";
        }
        if (lowerMime.startsWith("text/")) {
            return ".txt";
        }
        return null;
    }

    private boolean isDocxZip(byte[] content) {
        return content.length >= 4
                && content[0] == 'P'
                && content[1] == 'K'
                && content[2] == 3
                && content[3] == 4;
    }

    private String extractWord(byte[] content, String lowerName, String mimeType) throws IOException {
        boolean docx = lowerName.endsWith(".docx")
                || containsIgnoreCase(mimeType, "wordprocessingml")
                || isDocxZip(content);
        if (docx) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return normalizeWhitespace(extractor.getText());
            }
        }
        if (lowerName.endsWith(".doc") || containsIgnoreCase(mimeType, "msword")) {
            try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
                 WordExtractor extractor = new WordExtractor(document)) {
                return normalizeWhitespace(extractor.getText());
            }
        }
        return "";
    }

    private String extractExcel(byte[] content, String lowerName, String mimeType) throws IOException {
        if (lowerName.endsWith(".csv") || containsIgnoreCase(mimeType, "csv")) {
            return normalizeWhitespace(decodeText(content));
        }
        if (lowerName.endsWith(".xlsx") || containsIgnoreCase(mimeType, "spreadsheetml")) {
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
                return normalizeWhitespace(extractWorkbookText(workbook));
            }
        }
        if (lowerName.endsWith(".xls") || containsIgnoreCase(mimeType, "ms-excel")) {
            try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(content))) {
                return normalizeWhitespace(extractWorkbookText(workbook));
            }
        }
        return "";
    }

    private String extractWorkbookText(Workbook workbook) {
        StringBuilder builder = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("Sheet: ").append(sheet.getSheetName()).append('\n');
            for (Row row : sheet) {
                boolean wroteCell = false;
                for (Cell cell : row) {
                    String value = formatter.formatCellValue(cell).trim();
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (wroteCell) {
                        builder.append('\t');
                    }
                    builder.append(value);
                    wroteCell = true;
                }
                if (wroteCell) {
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String decodeText(byte[] content) {
        String utf8 = new String(content, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(content, Charset.forName("windows-1251"));
    }

    private boolean isPlainText(String mimeType, String lowerName) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/")) {
            return true;
        }
        return lowerName.endsWith(".txt")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".log")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".xml");
    }

    private boolean isWord(String mimeType, String lowerName) {
        if (mimeType != null) {
            String lowerMime = mimeType.toLowerCase(Locale.ROOT);
            if (lowerMime.contains("word") || lowerMime.contains("wordprocessing")) {
                return true;
            }
        }
        return lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".rtf");
    }

    private boolean isExcel(String mimeType, String lowerName) {
        if (mimeType != null) {
            String lowerMime = mimeType.toLowerCase(Locale.ROOT);
            if (lowerMime.contains("excel")
                    || lowerMime.contains("spreadsheet")
                    || lowerMime.contains("csv")) {
                return true;
            }
        }
        return lowerName.endsWith(".xls")
                || lowerName.endsWith(".xlsx")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".ods");
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }

    private String limitLength(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS);
    }
}
