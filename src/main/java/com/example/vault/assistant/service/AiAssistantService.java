package com.example.vault.assistant.service;

import com.example.vault.assistant.dto.AiChatResponseDto;
import com.example.vault.assistant.dto.AiPlanDto;
import com.example.vault.assistant.dto.AiPlanDto.AiPlanActionDto;
import com.example.vault.assistant.dto.AiPlanResponseDto;
import com.example.vault.assistant.provider.AiChatCommand;
import com.example.vault.assistant.provider.AiProviderRegistry;
import com.example.vault.config.AiProperties;
import com.example.vault.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private static final String SYSTEM_PROMPT = """
            Ты AI-ассистент личного архива документов (Personal Document Vault).
            Пользователь управляет деревом папок и документов командами на русском или английском.
            
            Текущее дерево передано в JSON как nodes: [{ id, name, type (FOLDER|DOCUMENT), parentId, documentId }].
            Всегда используй nodeId из контекста, когда это возможно.
            
            Ответь ТОЛЬКО JSON объектом:
            {
              "reply": "краткое описание того, что будет сделано (на русском)",
              "actions": [
                {
                  "type": "CREATE_FOLDER | DELETE_FOLDER | MOVE_NODE | RENAME_NODE | DELETE_DOCUMENT",
                  "name": "имя папки или документа для поиска",
                  "newName": "новое имя для RENAME_NODE",
                  "nodeId": "uuid узла",
                  "documentId": "uuid документа",
                  "parentNodeId": "uuid родительской папки (null для корня)",
                  "targetParentNodeId": "uuid новой родительской папки для MOVE_NODE",
                  "folderPath": ["сегменты", "пути"],
                  "targetFolderPath": ["куда", "переместить"]
                }
              ]
            }
            
            Правила:
            - CREATE_FOLDER: создай папку. name обязателен. parentNodeId=null для корневой папки.
            - DELETE_FOLDER: удали папку и ВСЁ содержимое рекурсивно. Укажи nodeId или name.
            - MOVE_NODE: перемести папку или документ. nodeId + targetParentNodeId или targetFolderPath.
            - RENAME_NODE: nodeId + newName.
            - DELETE_DOCUMENT: documentId или nodeId или name (title).
            - Если команда неясна — верни пустой actions и объясни в reply.
            - Не выдумывай nodeId — бери только из контекста.
            """;

    private final AiSettingsService aiSettingsService;
    private final AiProviderRegistry providerRegistry;
    private final VaultContextBuilder vaultContextBuilder;
    private final VaultActionExecutor actionExecutor;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiPlanResponseDto plan(String userMessage) {
        AiPlanDto aiPlan = requestPlan(userMessage);
        List<AiPlanActionDto> actions = aiPlan.actions() != null ? aiPlan.actions() : List.of();
        return new AiPlanResponseDto(
                aiPlan.reply() != null ? aiPlan.reply() : "Готово",
                actions
        );
    }

    public AiChatResponseDto execute(List<AiPlanActionDto> actions) {
        if (actions == null || actions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_ACTIONS", "No actions to execute");
        }

        VaultActionExecutor.ActionExecutionResult result = actionExecutor.executeAll(actions);
        List<String> executed = new ArrayList<>(result.executed());
        boolean success = result.errors().isEmpty() || !executed.isEmpty();

        return new AiChatResponseDto(
                success ? "Выполнено" : "Есть ошибки",
                executed,
                result.errors(),
                success
        );
    }

    private AiPlanDto requestPlan(String userMessage) {
        if (!aiProperties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "AI is disabled");
        }

        AiSettingsService.ResolvedAiConfig config = aiSettingsService.resolveConfig();
        String vaultContext = vaultContextBuilder.buildContextJson();
        String enrichedUserMessage = """
                Контекст vault:
                %s
                
                Команда пользователя:
                %s
                """.formatted(vaultContext, userMessage);

        var provider = providerRegistry.requireProvider(config.provider());
        String rawJson = provider.complete(new AiChatCommand(
                SYSTEM_PROMPT,
                enrichedUserMessage,
                config.apiKey(),
                config.model(),
                config.baseUrl(),
                config.timeoutSeconds()
        ));

        return parsePlan(rawJson);
    }

    private AiPlanDto parsePlan(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, AiPlanDto.class);
        } catch (Exception e) {
            log.error("Failed to parse AI plan: {}", rawJson, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                    "AI returned invalid JSON plan");
        }
    }
}
