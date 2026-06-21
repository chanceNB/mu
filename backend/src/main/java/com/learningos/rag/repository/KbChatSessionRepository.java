package com.learningos.rag.repository;

import com.learningos.rag.domain.KbChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbChatSessionRepository extends JpaRepository<KbChatSession, String> {

    List<KbChatSession> findByLearnerIdOrderByUpdatedAtDesc(String learnerId);
}
