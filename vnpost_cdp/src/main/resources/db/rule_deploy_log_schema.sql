-- ============================================================
-- Rule Deploy Log — Audit trail mỗi lần deploy rule lên Unomi
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_deploy_logs (
    id              BIGSERIAL PRIMARY KEY,

    rule_id         VARCHAR(255),
    rule_name       VARCHAR(255),
    scope           VARCHAR(100),
    event_type      VARCHAR(100),

    payload_json    JSONB,

    status          VARCHAR(50)  NOT NULL,

    unomi_response  TEXT,

    error_message   TEXT,

    deployed_by     VARCHAR(100),
    deployed_at     TIMESTAMP,

   
    created_by      VARCHAR(100),
    created         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified        TIMESTAMP,
    modified_by     VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_rule_deploy_logs_rule_id
    ON rule_deploy_logs (rule_id);

CREATE INDEX IF NOT EXISTS idx_rule_deploy_logs_status
    ON rule_deploy_logs (status);

CREATE INDEX IF NOT EXISTS idx_rule_deploy_logs_deployed_at
    ON rule_deploy_logs (deployed_at DESC);
