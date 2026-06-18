package com.example.vault.module;

import com.example.vault.common.event.DocumentCreatedEvent;
import com.example.vault.module.classification.DocumentClassifier;
import com.example.vault.module.ocr.OcrProcessor;
import com.example.vault.module.search.FullTextSearchIndexer;
import com.example.vault.module.telegram.TelegramNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates optional future modules via domain events.
 * No-op when module implementations are not registered.
 */
@Slf4j
@Component
public class ModuleEventOrchestrator {

    private final List<OcrProcessor> ocrProcessors;
    private final List<DocumentClassifier> classifiers;
    private final List<FullTextSearchIndexer> indexers;
    private final List<TelegramNotifier> notifiers;

    public ModuleEventOrchestrator(
            List<OcrProcessor> ocrProcessors,
            List<DocumentClassifier> classifiers,
            List<FullTextSearchIndexer> indexers,
            List<TelegramNotifier> notifiers
    ) {
        this.ocrProcessors = ocrProcessors;
        this.classifiers = classifiers;
        this.indexers = indexers;
        this.notifiers = notifiers;
    }

    @Async
    @EventListener
    public void onDocumentCreated(DocumentCreatedEvent event) {
        ocrProcessors.stream().filter(OcrProcessor::isEnabled).forEach(processor ->
                processor.processDocument(event.documentId(), event.assetId()));

        classifiers.stream().filter(DocumentClassifier::isEnabled).forEach(classifier ->
                classifier.classifyDocument(event.documentId(), event.assetId()));

        indexers.stream().filter(FullTextSearchIndexer::isEnabled).forEach(indexer ->
                indexer.indexDocument(event.documentId(), event.title(), null));

        notifiers.stream().filter(TelegramNotifier::isEnabled).forEach(notifier ->
                notifier.onDocumentCreated(event));

        log.debug("Processed DocumentCreatedEvent for document {}", event.documentId());
    }
}
