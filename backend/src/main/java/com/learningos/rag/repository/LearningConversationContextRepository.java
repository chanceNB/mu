package com.learningos.rag.repository;

import com.learningos.rag.domain.LearningConversationContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningConversationContextRepository extends JpaRepository<LearningConversationContext, String> {

    Optional<LearningConversationContext> findByLearnerIdAndChatSessionId(String learnerId, String chatSessionId);
}
