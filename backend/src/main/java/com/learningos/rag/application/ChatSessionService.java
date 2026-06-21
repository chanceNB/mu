package com.learningos.rag.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.common.api.ErrorCode;
import com.learningos.common.exception.ApiException;
import com.learningos.rag.api.dto.ChatSessionDtos.ChatSessionResponse;
import com.learningos.rag.domain.KbChatSession;
import com.learningos.rag.domain.LearningConversationContext;
import com.learningos.rag.repository.KbChatSessionRepository;
import com.learningos.rag.repository.LearningConversationContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatSessionService {

    private final KbChatSessionRepository chatSessionRepository;
    private final LearningConversationContextRepository conversationContextRepository;
    private final ObjectMapper objectMapper;

    public ChatSessionService(
            KbChatSessionRepository chatSessionRepository,
            LearningConversationContextRepository conversationContextRepository,
            ObjectMapper objectMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.conversationContextRepository = conversationContextRepository;
        this.objectMapper = objectMapper;
    }

    public ChatSessionResponse create(String learnerId, String title, List<String> kbIds, String goalId) {
        KbChatSession session = new KbChatSession();
        session.setId("session_" + UUID.randomUUID());
        session.setLearnerId(learnerId);
        session.setTitle(normalizeTitle(title));
        session.setKbIdsJson(writeKbIds(kbIds));
        KbChatSession savedSession = chatSessionRepository.save(session);

        LearningConversationContext context = new LearningConversationContext();
        context.setId("ctx_" + UUID.randomUUID());
        context.setChatSessionId(savedSession.getId());
        context.setLearnerId(learnerId);
        context.setGoalId(blankToNull(goalId));
        conversationContextRepository.save(context);

        return toResponse(savedSession, context.getGoalId());
    }

    public List<ChatSessionResponse> list(String learnerId) {
        return chatSessionRepository.findByLearnerIdOrderByUpdatedAtDesc(learnerId).stream()
                .map(session -> toResponse(session, findGoalId(learnerId, session.getId())))
                .toList();
    }

    public ChatSessionResponse get(String currentUserId, boolean currentUserAdmin, String sessionId) {
        KbChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Chat session not found"));
        if (!currentUserAdmin && session.getLearnerId() != null && !currentUserId.equals(session.getLearnerId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chat session access denied");
        }
        return toResponse(session, findGoalId(session.getLearnerId(), sessionId));
    }

    private ChatSessionResponse toResponse(KbChatSession session, String goalId) {
        return new ChatSessionResponse(
                session.getId(),
                session.getLearnerId(),
                session.getTitle(),
                readKbIds(session.getKbIdsJson()),
                goalId,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private String findGoalId(String learnerId, String sessionId) {
        if (learnerId == null) {
            return null;
        }
        return conversationContextRepository.findByLearnerIdAndChatSessionId(learnerId, sessionId)
                .map(LearningConversationContext::getGoalId)
                .orElse(null);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新建对话";
        }
        return title.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String writeKbIds(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(kbIds);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize chat session knowledge bases");
        }
    }

    private List<String> readKbIds(String kbIdsJson) {
        if (kbIdsJson == null || kbIdsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(kbIdsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
