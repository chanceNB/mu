package com.learningos.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "learning_conversation_context")
public class LearningConversationContext {

    @Id
    private String id;

    @Column(nullable = false, length = 120)
    private String chatSessionId;

    @Column(nullable = false)
    private String learnerId;

    private String goalId;

    @Column(length = 120)
    private String latestLearningPathId;

    @Column(length = 120)
    private String latestResourceTaskId;

    @Column(length = 120)
    private String latestAnswerId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getChatSessionId() {
        return chatSessionId;
    }

    public void setChatSessionId(String chatSessionId) {
        this.chatSessionId = chatSessionId;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public void setLearnerId(String learnerId) {
        this.learnerId = learnerId;
    }

    public String getGoalId() {
        return goalId;
    }

    public void setGoalId(String goalId) {
        this.goalId = goalId;
    }

    public String getLatestLearningPathId() {
        return latestLearningPathId;
    }

    public void setLatestLearningPathId(String latestLearningPathId) {
        this.latestLearningPathId = latestLearningPathId;
    }

    public String getLatestResourceTaskId() {
        return latestResourceTaskId;
    }

    public void setLatestResourceTaskId(String latestResourceTaskId) {
        this.latestResourceTaskId = latestResourceTaskId;
    }

    public String getLatestAnswerId() {
        return latestAnswerId;
    }

    public void setLatestAnswerId(String latestAnswerId) {
        this.latestAnswerId = latestAnswerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
