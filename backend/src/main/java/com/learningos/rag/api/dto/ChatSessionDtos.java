package com.learningos.rag.api.dto;

import java.time.Instant;
import java.util.List;

public final class ChatSessionDtos {

    private ChatSessionDtos() {
    }

    public record CreateChatSessionRequest(
            String title,
            List<String> kbIds,
            String goalId
    ) {
    }

    public record ChatSessionResponse(
            String id,
            String learnerId,
            String title,
            List<String> kbIds,
            String goalId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
