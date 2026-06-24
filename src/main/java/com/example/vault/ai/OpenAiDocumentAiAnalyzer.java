package com.example.vault.ai;

import com.example.vault.asset.entity.Asset;
import com.example.vault.asset.repository.AssetRepository;
import com.example.vault.assistant.provider.AiChatCommand;
import com.example.vault.assistant.provider.OpenAiChatProvider;
import com.example.vault.assistant.service.AiSettingsService;
import com.example.vault.config.AiProperties;
import com.example.vault.exception.ApiException;
import com.example.vault.exception.ResourceNotFoundException;
import com.example.vault.importing.dto.ImportProposalDto;
import com.example.vault.node.dto.TreeNodeDto;
import com.example.vault.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "vault.ai.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiDocumentAiAnalyzer implements DocumentAiAnalyzer {

    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_PDF_BYTES = 50 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 12_000;

    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final AiSettingsService aiSettingsService;
    private final AiProperties aiProperties;
    private final OpenAiVisionClient visionClient;
    private final OpenAiChatProvider chatProvider;
    private final PdfDocumentExtractor pdfExtractor;
    private final DocumentTextExtractor documentTextExtractor;
    private final ObjectMapper objectMapper;

    @Override
    public ImportProposalDto analyze(UUID assetId, String originalFilename, List<TreeNodeDto> tree) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
        byte[] content = storageService.download(asset.getStorageKey());
        return analyze(content, asset.getMimeType(), originalFilename, tree);
    }

    @Override
    public ImportProposalDto analyze(
            byte[] content,
            String mimeType,
            String originalFilename,
            List<TreeNodeDto> tree
    ) {
        return analyze(content, mimeType, originalFilename, tree, null);
    }

    @Override
    public ImportProposalDto analyze(
            byte[] content,
            String mimeType,
            String originalFilename,
            List<TreeNodeDto> tree,
            UUID userId
    ) {
        String filename = originalFilename != null && !originalFilename.isBlank()
                ? originalFilename
                : "file";
        String treeJson = buildTreeJson(tree);
        AiSettingsService.ResolvedAiConfig config = aiSettingsService.resolveConfigForUser(userId);

        if (isImage(mimeType)) {
            return analyzeImageBytes(content, filename, mimeType, treeJson, config);
        }
        if (isPdf(mimeType, filename)) {
            return analyzePdfBytes(content, filename, mimeType, treeJson, config);
        }
        if (documentTextExtractor.canExtract(mimeType, filename)) {
            String extracted = documentTextExtractor.extract(content, mimeType, filename);
            if (extracted == null || extracted.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT_EXTRACTION_FAILED",
                        "Could not extract text from document: " + filename);
            }
            return analyzeExtractedText(filename, mimeType, treeJson, config, extracted, defaultTagFor(filename, mimeType));
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                "AI import supports photos, PDF, TXT, Word, and Excel. Got: " + mimeType);
    }

    private String defaultTagFor(String filename, String mimeType) {
        if (documentTextExtractor.canExtract(mimeType, filename)) {
            String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
            if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
                return "spreadsheet";
            }
            if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
                return "word";
            }
            return "text";
        }
        return "document";
    }

    @Override
    public boolean isEnabled() {
        return aiProperties.isEnabled();
    }

    private ImportProposalDto analyzeImageBytes(
            byte[] imageBytes,
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config
    ) {
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "Image exceeds 20 MB limit for AI analysis");
        }

        String userMessage = """
                Проанализируй фотографию и предложи место в архиве.
                filename: %s
                mimeType: %s
                sizeBytes: %d
                
                existingTree:
                %s
                """.formatted(filename, mimeType, imageBytes.length, treeJson);

        String json = visionClient.analyzeImage(
                config.baseUrl(),
                config.apiKey(),
                resolveVisionModel(config.model()),
                DocumentImportPrompts.IMAGE_SYSTEM_PROMPT,
                userMessage,
                mimeType,
                imageBytes
        );

        return parseProposal(json, "Фото", "photo");
    }

    private ImportProposalDto analyzePdfBytes(
            byte[] pdfBytes,
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config
    ) {
        if (pdfBytes.length > MAX_PDF_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "PDF exceeds 50 MB limit for AI analysis");
        }

        PdfDocumentExtractor.PdfExtractionResult extracted = pdfExtractor.extract(pdfBytes);

        if (extracted.hasTextLayer()) {
            log.debug("Analyzing PDF {} via text layer ({} pages)", filename, extracted.pageCount());
            return analyzePdfText(filename, mimeType, treeJson, config, extracted);
        }

        if (extracted.firstPagePng() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_EMPTY",
                    "PDF has no extractable text and no renderable pages");
        }

        log.debug("Analyzing PDF {} via vision fallback (scan)", filename);
        return analyzePdfScan(filename, mimeType, treeJson, config, extracted);
    }

    private ImportProposalDto analyzeImage(
            Asset asset,
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config
    ) {
        byte[] imageBytes = storageService.download(asset.getStorageKey());
        return analyzeImageBytes(imageBytes, filename, mimeType, treeJson, config);
    }

    private ImportProposalDto analyzePdf(
            Asset asset,
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config
    ) {
        byte[] pdfBytes = storageService.download(asset.getStorageKey());
        return analyzePdfBytes(pdfBytes, filename, mimeType, treeJson, config);
    }

    private ImportProposalDto analyzePdfText(
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config,
            PdfDocumentExtractor.PdfExtractionResult extracted
    ) {
        return analyzeExtractedText(
                filename,
                mimeType,
                treeJson,
                config,
                extracted.text(),
                "pdf",
                extracted.pageCount(),
                "PDF-документ"
        );
    }

    private ImportProposalDto analyzeExtractedText(
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config,
            String extractedText,
            String defaultTag
    ) {
        return analyzeExtractedText(filename, mimeType, treeJson, config, extractedText, defaultTag, null, null);
    }

    private ImportProposalDto analyzeExtractedText(
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config,
            String extractedText,
            String defaultTag,
            Integer pageCount,
            String defaultTitle
    ) {
        String textForPrompt = truncateText(extractedText);
        String docLabel = defaultTitle != null ? defaultTitle : "Документ";
        String userMessage = pageCount != null
                ? """
                Проанализируй документ и предложи место в архиве.
                filename: %s
                mimeType: %s
                pageCount: %d
                
                existingTree:
                %s
                
                extractedText:
                %s
                """.formatted(filename, mimeType, pageCount, treeJson, textForPrompt)
                : """
                Проанализируй документ и предложи место в архиве.
                filename: %s
                mimeType: %s
                
                existingTree:
                %s
                
                extractedText:
                %s
                """.formatted(filename, mimeType, treeJson, textForPrompt);

        String json = chatProvider.complete(new AiChatCommand(
                DocumentImportPrompts.TEXT_SYSTEM_PROMPT,
                userMessage,
                config.apiKey(),
                resolveTextModel(config.model()),
                config.baseUrl(),
                config.timeoutSeconds()
        ));

        return parseProposal(json, docLabel, defaultTag);
    }

    private ImportProposalDto analyzePdfScan(
            String filename,
            String mimeType,
            String treeJson,
            AiSettingsService.ResolvedAiConfig config,
            PdfDocumentExtractor.PdfExtractionResult extracted
    ) {
        if (extracted.firstPagePng().length > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "Rendered PDF page exceeds 20 MB limit for AI analysis");
        }

        String userMessage = """
                Проанализируй PDF-скан (первая страница) и предложи место в архиве.
                filename: %s
                mimeType: %s
                pageCount: %d
                
                existingTree:
                %s
                """.formatted(filename, mimeType, extracted.pageCount(), treeJson);

        String json = visionClient.analyzeImage(
                config.baseUrl(),
                config.apiKey(),
                resolveVisionModel(config.model()),
                DocumentImportPrompts.SCAN_SYSTEM_PROMPT,
                userMessage,
                "image/png",
                extracted.firstPagePng()
        );

        return parseProposal(json, "PDF-документ", "pdf");
    }

    private String truncateText(String text) {
        if (text == null || text.length() <= MAX_TEXT_CHARS) {
            return text != null ? text : "";
        }
        return text.substring(0, MAX_TEXT_CHARS) + "\n\n[... текст обрезан для анализа ...]";
    }

    private boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    private boolean isPdf(String mimeType, String filename) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String resolveVisionModel(String configuredModel) {
        if (configuredModel != null && configuredModel.contains("gpt-4o")) {
            return configuredModel;
        }
        return "gpt-4o-mini";
    }

    private String resolveTextModel(String configuredModel) {
        if (configuredModel != null && !configuredModel.isBlank()) {
            return configuredModel;
        }
        return "gpt-4o-mini";
    }

    private ImportProposalDto parseProposal(String json, String defaultTitle, String defaultTag) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String title = textOrDefault(root, "title", defaultTitle);
            String summary = textOrDefault(root, "summary", title);
            List<String> tags = readStringList(root.path("tags"));
            List<String> folderPath = readStringList(root.path("folderPath"));
            boolean createMissing = root.path("createMissingFolders").asBoolean(true);
            double confidence = root.path("confidence").asDouble(0.7);
            String ocrText = nullableText(root, "ocrText");
            String classificationLabel = nullableText(root, "classificationLabel");
            Double classificationConfidence = root.has("classificationConfidence")
                    && !root.path("classificationConfidence").isNull()
                    ? root.path("classificationConfidence").asDouble()
                    : null;

            if (folderPath.isEmpty()) {
                folderPath = List.of("Документы", "Неразобранное");
            }
            if (tags.isEmpty()) {
                tags = List.of(defaultTag);
            }

            return new ImportProposalDto(
                    title,
                    summary,
                    tags,
                    folderPath,
                    createMissing,
                    confidence,
                    ocrText,
                    classificationLabel,
                    classificationConfidence
            );
        } catch (Exception e) {
            log.error("Failed to parse AI proposal JSON", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                    "Failed to parse AI response: " + e.getMessage());
        }
    }

    private String buildTreeJson(List<TreeNodeDto> tree) {
        try {
            List<VaultNodeContext> nodes = new ArrayList<>();
            flatten(tree, nodes);
            return objectMapper.writeValueAsString(java.util.Map.of("nodes", nodes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize vault tree", e);
        }
    }

    private void flatten(List<TreeNodeDto> tree, List<VaultNodeContext> out) {
        for (TreeNodeDto node : tree) {
            out.add(new VaultNodeContext(
                    node.id(),
                    node.name(),
                    node.type().name(),
                    node.parentId(),
                    null
            ));
            flatten(node.children(), out);
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return List.copyOf(values);
    }

    private String textOrDefault(JsonNode root, String field, String fallback) {
        String value = nullableText(root, field);
        return value != null ? value : fallback;
    }

    private String nullableText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isBlank() ? null : text;
    }

    private String extractFilename(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }

    private record VaultNodeContext(
            UUID id,
            String name,
            String type,
            UUID parentId,
            UUID documentId
    ) {
    }
}
