DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_missing//
DROP PROCEDURE IF EXISTS add_index_if_missing//

CREATE PROCEDURE add_column_if_missing(
    IN table_name_value varchar(128),
    IN column_name_value varchar(128),
    IN ddl_value text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_index_if_missing(
    IN table_name_value varchar(128),
    IN index_name_value varchar(128),
    IN ddl_value text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CREATE TABLE IF NOT EXISTS learning_conversation_context (
    id VARCHAR(80) NOT NULL,
    chat_session_id VARCHAR(120) NOT NULL,
    learner_id VARCHAR(255) NOT NULL,
    goal_id VARCHAR(255) NULL,
    latest_learning_path_id VARCHAR(120) NULL,
    latest_resource_task_id VARCHAR(120) NULL,
    latest_answer_id VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CALL add_index_if_missing(
    'learning_conversation_context',
    'idx_lcc_learner_session',
    'CREATE INDEX idx_lcc_learner_session ON learning_conversation_context (learner_id, chat_session_id)'
);

CALL add_column_if_missing(
    'kb_chat_session',
    'learner_id',
    'ALTER TABLE kb_chat_session ADD COLUMN learner_id VARCHAR(120) NULL'
);

CALL add_column_if_missing(
    'kb_chat_session',
    'title',
    'ALTER TABLE kb_chat_session ADD COLUMN title VARCHAR(200) NULL'
);

CALL add_column_if_missing(
    'kb_chat_session',
    'kb_ids_json',
    'ALTER TABLE kb_chat_session ADD COLUMN kb_ids_json VARCHAR(4000) NULL'
);

CALL add_column_if_missing(
    'kb_chat_session',
    'created_at',
    'ALTER TABLE kb_chat_session ADD COLUMN created_at DATETIME(6) NULL'
);

CALL add_column_if_missing(
    'kb_chat_session',
    'updated_at',
    'ALTER TABLE kb_chat_session ADD COLUMN updated_at DATETIME(6) NULL'
);

CALL add_index_if_missing(
    'kb_chat_session',
    'idx_kb_chat_session_learner_updated',
    'CREATE INDEX idx_kb_chat_session_learner_updated ON kb_chat_session (learner_id, updated_at)'
);

DROP PROCEDURE add_index_if_missing;
DROP PROCEDURE add_column_if_missing;
