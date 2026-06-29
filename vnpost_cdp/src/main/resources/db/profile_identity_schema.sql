-- ============================================================
-- CDP Profile Identity Schema
-- All 10 additional CDP profile tables
-- Apply AFTER the base schema.sql (master_profiles already exists)
-- ============================================================

-- ============================================================
-- 1. profile_source_systems
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_source_systems (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100)  NOT NULL UNIQUE,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    source_type VARCHAR(100),
    priority    INTEGER,
    status      SMALLINT      NOT NULL DEFAULT 1,
    created_by  VARCHAR(100),
    created     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified    TIMESTAMP,
    modified_by VARCHAR(100)
);

COMMENT ON TABLE  profile_source_systems              IS 'List of external systems that send profile data into CDP';
COMMENT ON COLUMN profile_source_systems.code         IS 'Unique short code for the source system, e.g. CRM, APP, CORE';
COMMENT ON COLUMN profile_source_systems.source_type  IS 'Category of source system, e.g. INTERNAL, EXTERNAL';
COMMENT ON COLUMN profile_source_systems.priority     IS 'Lower number = higher priority for merge rules';
COMMENT ON COLUMN profile_source_systems.status       IS '1=ACTIVE, 0=INACTIVE';

CREATE INDEX IF NOT EXISTS idx_pss_code     ON profile_source_systems(code);
CREATE INDEX IF NOT EXISTS idx_pss_status   ON profile_source_systems(status);

-- ============================================================
-- 2. profile_source_records
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_source_records (
    id                  BIGSERIAL PRIMARY KEY,
    source_system       VARCHAR(100),
    source_customer_id  VARCHAR(255),
    source_event_id     VARCHAR(255),
    master_profile_id   BIGINT,
    identity_key        VARCHAR(500),
    raw_payload         JSONB,
    normalized_payload  JSONB,
    received_at         TIMESTAMP,
    processed_at        TIMESTAMP,
    merge_status        SMALLINT      NOT NULL DEFAULT 0,
    error_message       TEXT,
    created_by          VARCHAR(100),
    created             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified            TIMESTAMP,
    modified_by         VARCHAR(100),
    CONSTRAINT fk_psr_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id)
);

COMMENT ON TABLE  profile_source_records                  IS 'Raw profile records received from source systems before normalization';
COMMENT ON COLUMN profile_source_records.source_system    IS 'Source system code, e.g. CRM, APP';
COMMENT ON COLUMN profile_source_records.identity_key     IS 'Composite identity key used for matching, e.g. phone:0988888888';
COMMENT ON COLUMN profile_source_records.raw_payload      IS 'Original JSON payload as received from source system';
COMMENT ON COLUMN profile_source_records.normalized_payload IS 'Normalized JSON payload after field mapping';
COMMENT ON COLUMN profile_source_records.merge_status     IS '0=PENDING, 1=MERGED, 2=CONFLICT, 3=REJECTED, 4=NEED_REVIEW, 5=ERROR';

CREATE INDEX IF NOT EXISTS idx_psr_master_profile_id  ON profile_source_records(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_psr_source_system      ON profile_source_records(source_system);
CREATE INDEX IF NOT EXISTS idx_psr_source_customer_id ON profile_source_records(source_customer_id);
CREATE INDEX IF NOT EXISTS idx_psr_merge_status       ON profile_source_records(merge_status);
CREATE INDEX IF NOT EXISTS idx_psr_received_at        ON profile_source_records(received_at);

-- ============================================================
-- 3. profile_identity_links
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_identity_links (
    id                  BIGSERIAL PRIMARY KEY,
    master_profile_id   BIGINT        NOT NULL,
    source_system       VARCHAR(100),
    source_customer_id  VARCHAR(255),
    identity_type       VARCHAR(100),
    identity_value      VARCHAR(500),
    confidence_score    NUMERIC(5,2),
    is_primary          BOOLEAN,
    status              SMALLINT      NOT NULL DEFAULT 1,
    linked_at           TIMESTAMP,
    linked_by           VARCHAR(100),
    created_by          VARCHAR(100),
    created             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified            TIMESTAMP,
    modified_by         VARCHAR(100),
    CONSTRAINT fk_pil_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id)
);

COMMENT ON TABLE  profile_identity_links                  IS 'Maps master profiles to source system customer IDs — answers which sources were merged';
COMMENT ON COLUMN profile_identity_links.identity_type    IS 'Type of identity, e.g. PHONE, EMAIL, IDENTITY_NO, LOYALTY_ID';
COMMENT ON COLUMN profile_identity_links.identity_value   IS 'The actual identity value';
COMMENT ON COLUMN profile_identity_links.confidence_score IS 'Confidence score of the link (0.00 - 100.00)';
COMMENT ON COLUMN profile_identity_links.is_primary       IS 'Whether this is the primary identity link for this type';
COMMENT ON COLUMN profile_identity_links.status           IS '1=ACTIVE, 2=INACTIVE, 3=MERGED, 4=DELETED';

CREATE INDEX IF NOT EXISTS idx_pil_master_profile_id  ON profile_identity_links(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pil_source_system      ON profile_identity_links(source_system);
CREATE INDEX IF NOT EXISTS idx_pil_source_customer_id ON profile_identity_links(source_customer_id);
CREATE INDEX IF NOT EXISTS idx_pil_identity_type      ON profile_identity_links(identity_type);
CREATE INDEX IF NOT EXISTS idx_pil_identity_value     ON profile_identity_links(identity_value);
CREATE INDEX IF NOT EXISTS idx_pil_status             ON profile_identity_links(status);

-- ============================================================
-- 4. profile_attribute_values
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_attribute_values (
    id                  BIGSERIAL PRIMARY KEY,
    master_profile_id   BIGINT        NOT NULL,
    source_record_id    BIGINT,
    source_system       VARCHAR(100),
    property_name       VARCHAR(200),
    property_value      TEXT,
    normalized_value    TEXT,
    confidence_score    NUMERIC(5,2),
    is_selected         BOOLEAN,
    received_at         TIMESTAMP,
    created_by          VARCHAR(100),
    created             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified            TIMESTAMP,
    modified_by         VARCHAR(100),
    CONSTRAINT fk_pav_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_pav_source_record
        FOREIGN KEY (source_record_id) REFERENCES profile_source_records(id)
);

COMMENT ON TABLE  profile_attribute_values               IS 'Attribute values per source system — answers which source provided each field value';
COMMENT ON COLUMN profile_attribute_values.property_name IS 'The profile field/attribute name, e.g. fullName, phone, email';
COMMENT ON COLUMN profile_attribute_values.is_selected   IS 'Whether this value is currently selected as the master profile value';

CREATE INDEX IF NOT EXISTS idx_pav_master_profile_id  ON profile_attribute_values(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pav_source_record_id   ON profile_attribute_values(source_record_id);
CREATE INDEX IF NOT EXISTS idx_pav_property_name      ON profile_attribute_values(property_name);
CREATE INDEX IF NOT EXISTS idx_pav_is_selected        ON profile_attribute_values(is_selected);

-- ============================================================
-- 5. profile_merge_rules
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_merge_rules (
    id              BIGSERIAL PRIMARY KEY,
    property_name   VARCHAR(200)  NOT NULL,
    source_system   VARCHAR(100),
    priority        INTEGER,
    merge_strategy  VARCHAR(100),
    allow_overwrite BOOLEAN,
    require_review  BOOLEAN,
    description     TEXT,
    status          SMALLINT      NOT NULL DEFAULT 1,
    created_by      VARCHAR(100),
    created         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified        TIMESTAMP,
    modified_by     VARCHAR(100)
);

COMMENT ON TABLE  profile_merge_rules                   IS 'Merge rules per property and source system — defines which source wins for each field';
COMMENT ON COLUMN profile_merge_rules.merge_strategy    IS 'SOURCE_PRIORITY, LATEST_UPDATE, HIGHEST_CONFIDENCE, APPEND_LIST, SUM, MAX, MIN, MANUAL_ONLY';
COMMENT ON COLUMN profile_merge_rules.allow_overwrite   IS 'Whether incoming value can overwrite existing value';
COMMENT ON COLUMN profile_merge_rules.require_review    IS 'Whether a conflict in this field requires manual admin review';
COMMENT ON COLUMN profile_merge_rules.status            IS '1=ACTIVE, 0=INACTIVE';

CREATE INDEX IF NOT EXISTS idx_pmru_property_name  ON profile_merge_rules(property_name);
CREATE INDEX IF NOT EXISTS idx_pmru_source_system  ON profile_merge_rules(source_system);
CREATE INDEX IF NOT EXISTS idx_pmru_status         ON profile_merge_rules(status);

-- ============================================================
-- 6. profile_change_logs
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_change_logs (
    id                  BIGSERIAL PRIMARY KEY,
    master_profile_id   BIGINT        NOT NULL,
    source_record_id    BIGINT,
    source_system       VARCHAR(100),
    event_type          VARCHAR(100),
    property_name       VARCHAR(200),
    old_value           TEXT,
    new_value           TEXT,
    selected_value      TEXT,
    old_source          VARCHAR(100),
    new_source          VARCHAR(100),
    merge_strategy      VARCHAR(100),
    reason              TEXT,
    changed_by          VARCHAR(100),
    changed_at          TIMESTAMP,
    CONSTRAINT fk_pcl_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_pcl_source_record
        FOREIGN KEY (source_record_id) REFERENCES profile_source_records(id)
);

COMMENT ON TABLE  profile_change_logs              IS 'Audit history for master profile field changes — answers what changed, from which source, and why';
COMMENT ON COLUMN profile_change_logs.event_type   IS 'AUTO_MERGE, MANUAL_UPDATE, ADMIN_MERGE, SPLIT_PROFILE, CONFLICT_RESOLVE, SYNC_UNOMI';
COMMENT ON COLUMN profile_change_logs.selected_value IS 'The value that was ultimately selected/applied to the master profile';

CREATE INDEX IF NOT EXISTS idx_pcl_master_profile_id  ON profile_change_logs(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pcl_source_record_id   ON profile_change_logs(source_record_id);
CREATE INDEX IF NOT EXISTS idx_pcl_property_name      ON profile_change_logs(property_name);
CREATE INDEX IF NOT EXISTS idx_pcl_changed_at         ON profile_change_logs(changed_at);
CREATE INDEX IF NOT EXISTS idx_pcl_event_type         ON profile_change_logs(event_type);

-- ============================================================
-- 7. profile_merge_conflicts
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_merge_conflicts (
    id                  BIGSERIAL PRIMARY KEY,
    master_profile_id   BIGINT        NOT NULL,
    source_record_id    BIGINT,
    property_name       VARCHAR(200),
    current_value       TEXT,
    incoming_value      TEXT,
    current_source      VARCHAR(100),
    incoming_source     VARCHAR(100),
    conflict_reason     TEXT,
    resolution_status   SMALLINT      NOT NULL DEFAULT 0,
    resolved_value      TEXT,
    resolved_by         VARCHAR(100),
    resolved_at         TIMESTAMP,
    created_by          VARCHAR(100),
    created             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified            TIMESTAMP,
    modified_by         VARCHAR(100),
    CONSTRAINT fk_pmc_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_pmc_source_record
        FOREIGN KEY (source_record_id) REFERENCES profile_source_records(id)
);

COMMENT ON TABLE  profile_merge_conflicts                   IS 'Conflicts requiring admin review when CDP cannot auto-merge an incoming value';
COMMENT ON COLUMN profile_merge_conflicts.resolution_status IS '0=OPEN, 1=RESOLVED, 2=REJECTED, 3=IGNORED';

CREATE INDEX IF NOT EXISTS idx_pmc_master_profile_id   ON profile_merge_conflicts(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pmc_resolution_status   ON profile_merge_conflicts(resolution_status);
CREATE INDEX IF NOT EXISTS idx_pmc_property_name       ON profile_merge_conflicts(property_name);
CREATE INDEX IF NOT EXISTS idx_pmc_created             ON profile_merge_conflicts(created);

-- ============================================================
-- 8. profile_merge_requests
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_merge_requests (
    id                       BIGSERIAL PRIMARY KEY,
    source_master_profile_id BIGINT        NOT NULL,
    target_master_profile_id BIGINT        NOT NULL,
    merge_reason             TEXT,
    selected_values          JSONB,
    status                   SMALLINT      NOT NULL DEFAULT 0,
    requested_by             VARCHAR(100),
    approved_by              VARCHAR(100),
    requested_at             TIMESTAMP,
    approved_at              TIMESTAMP,
    completed_at             TIMESTAMP,
    error_message            TEXT,
    created_by               VARCHAR(100),
    created                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified                 TIMESTAMP,
    modified_by              VARCHAR(100),
    CONSTRAINT fk_pmrq_source_profile
        FOREIGN KEY (source_master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_pmrq_target_profile
        FOREIGN KEY (target_master_profile_id) REFERENCES master_profiles(id)
);

COMMENT ON TABLE  profile_merge_requests                  IS 'Admin manual merge requests — answers which profiles were manually merged and by whom';
COMMENT ON COLUMN profile_merge_requests.selected_values  IS 'JSON: the field values selected for the merged profile';
COMMENT ON COLUMN profile_merge_requests.status           IS '0=PENDING, 1=APPROVED, 2=REJECTED, 3=COMPLETED, 4=FAILED';

CREATE INDEX IF NOT EXISTS idx_pmrq_source_profile_id  ON profile_merge_requests(source_master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pmrq_target_profile_id  ON profile_merge_requests(target_master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pmrq_status             ON profile_merge_requests(status);
CREATE INDEX IF NOT EXISTS idx_pmrq_requested_at       ON profile_merge_requests(requested_at);

-- ============================================================
-- 9. profile_merge_jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_merge_jobs (
    id               BIGSERIAL PRIMARY KEY,
    job_type         VARCHAR(100)  NOT NULL,
    source_system    VARCHAR(100),
    total_records    INTEGER,
    success_records  INTEGER,
    conflict_records INTEGER,
    failed_records   INTEGER,
    status           SMALLINT      NOT NULL DEFAULT 0,
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP,
    error_message    TEXT,
    created_by       VARCHAR(100),
    created          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified         TIMESTAMP,
    modified_by      VARCHAR(100)
);

COMMENT ON TABLE  profile_merge_jobs             IS 'Batch job execution logs for auto/manual merge, import, reprocess, and Unomi sync';
COMMENT ON COLUMN profile_merge_jobs.job_type    IS 'AUTO_MERGE, MANUAL_MERGE, BATCH_IMPORT, REPROCESS, SYNC_UNOMI';
COMMENT ON COLUMN profile_merge_jobs.status      IS '0=PENDING, 1=RUNNING, 2=SUCCESS, 3=FAILED, 4=PARTIAL_SUCCESS';

CREATE INDEX IF NOT EXISTS idx_pmj_job_type    ON profile_merge_jobs(job_type);
CREATE INDEX IF NOT EXISTS idx_pmj_status      ON profile_merge_jobs(status);
CREATE INDEX IF NOT EXISTS idx_pmj_started_at  ON profile_merge_jobs(started_at);

-- ============================================================
-- 10. profile_unomi_sync_logs
-- ============================================================
CREATE TABLE IF NOT EXISTS profile_unomi_sync_logs (
    id               BIGSERIAL PRIMARY KEY,
    master_profile_id BIGINT        NOT NULL,
    profile_code     VARCHAR(100)  NOT NULL,
    sync_type        VARCHAR(50),
    request_payload  JSONB,
    response_payload JSONB,
    status           SMALLINT      NOT NULL DEFAULT 0,
    error_message    TEXT,
    synced_at        TIMESTAMP,
    created_by       VARCHAR(100),
    CONSTRAINT fk_pusl_master_profile
        FOREIGN KEY (master_profile_id) REFERENCES master_profiles(id)
);

COMMENT ON TABLE  profile_unomi_sync_logs              IS 'Sync logs for pushing master profiles to Apache Unomi — answers was the profile synced successfully?';
COMMENT ON COLUMN profile_unomi_sync_logs.profile_code IS 'CDP profile code used as Unomi profileId/itemId. No separate unomi_profile_id is used.';
COMMENT ON COLUMN profile_unomi_sync_logs.sync_type    IS 'CREATE, UPDATE, MERGE, DELETE, RETRY';
COMMENT ON COLUMN profile_unomi_sync_logs.status       IS '0=PENDING, 1=SUCCESS, 2=FAILED, 3=RETRYING';

CREATE INDEX IF NOT EXISTS idx_pusl_master_profile_id  ON profile_unomi_sync_logs(master_profile_id);
CREATE INDEX IF NOT EXISTS idx_pusl_profile_code       ON profile_unomi_sync_logs(profile_code);
CREATE INDEX IF NOT EXISTS idx_pusl_status             ON profile_unomi_sync_logs(status);
CREATE INDEX IF NOT EXISTS idx_pusl_synced_at          ON profile_unomi_sync_logs(synced_at);
