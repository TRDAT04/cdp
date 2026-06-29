-- ============================================================
-- Profile Match Candidate Schema
-- Apply AFTER profile_identity_schema.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS profile_match_candidates (
    id                       BIGSERIAL PRIMARY KEY,
    left_master_profile_id   BIGINT,
    right_master_profile_id  BIGINT,
    left_source_system       VARCHAR(100),
    left_source_customer_id  VARCHAR(255),
    right_source_system      VARCHAR(100),
    right_source_customer_id VARCHAR(255),
    left_snapshot            JSONB,
    right_snapshot           JSONB,
    match_score              NUMERIC(5,2) NOT NULL,
    match_level              VARCHAR(50),
    status                   SMALLINT NOT NULL DEFAULT 0,
    decision_by              VARCHAR(100),
    decision_at              TIMESTAMP,
    merge_request_id         BIGINT,
    created_by               VARCHAR(100),
    created                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified                 TIMESTAMP,
    modified_by              VARCHAR(100),
    CONSTRAINT fk_profile_match_candidates_left_profile
        FOREIGN KEY (left_master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_profile_match_candidates_right_profile
        FOREIGN KEY (right_master_profile_id) REFERENCES master_profiles(id),
    CONSTRAINT fk_profile_match_candidates_merge_request
        FOREIGN KEY (merge_request_id) REFERENCES profile_merge_requests(id),
    CONSTRAINT chk_profile_match_candidates_not_same_profile
        CHECK (
            left_master_profile_id IS NULL
            OR right_master_profile_id IS NULL
            OR left_master_profile_id <> right_master_profile_id
        )
);

COMMENT ON TABLE  profile_match_candidates IS 'Pairs of profiles that may represent the same real customer, pending admin review';
COMMENT ON COLUMN profile_match_candidates.left_snapshot  IS 'JSONB snapshot of left profile data at detection time';
COMMENT ON COLUMN profile_match_candidates.right_snapshot IS 'JSONB snapshot of right profile data at detection time';
COMMENT ON COLUMN profile_match_candidates.match_score    IS 'Computed match score 0-100';
COMMENT ON COLUMN profile_match_candidates.match_level    IS 'VERY_HIGH, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN profile_match_candidates.status         IS '0=PENDING, 1=MERGED, 2=IGNORED, 3=REJECTED, 4=EXPIRED';

CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_left_profile  ON profile_match_candidates(left_master_profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_right_profile ON profile_match_candidates(right_master_profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_status        ON profile_match_candidates(status);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_match_score   ON profile_match_candidates(match_score);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_match_level   ON profile_match_candidates(match_level);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_decision_at   ON profile_match_candidates(decision_at);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_left_source   ON profile_match_candidates(left_source_system, left_source_customer_id);
CREATE INDEX IF NOT EXISTS idx_profile_match_candidates_right_source  ON profile_match_candidates(right_source_system, right_source_customer_id);

CREATE TABLE IF NOT EXISTS profile_match_reasons (
    id                 BIGSERIAL PRIMARY KEY,
    match_candidate_id BIGINT NOT NULL,
    reason_type        VARCHAR(100) NOT NULL,
    reason_message     VARCHAR(500) NOT NULL,
    left_value         TEXT,
    right_value        TEXT,
    score              NUMERIC(5,2),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_match_reasons_candidate
        FOREIGN KEY (match_candidate_id) REFERENCES profile_match_candidates(id)
);

COMMENT ON TABLE  profile_match_reasons IS 'Detailed reasons explaining why two profiles are considered possible duplicates';
COMMENT ON COLUMN profile_match_reasons.reason_type  IS 'IDENTITY_NO_MATCH, PHONE_MATCH, EMAIL_MATCH, NAME_EXACT_MATCH, NAME_SIMILAR, DATE_OF_BIRTH_MATCH, PROVINCE_MATCH, UNIT_MATCH, SOURCE_CUSTOMER_LINK, BEHAVIOR_MATCH, IDENTITY_CONFLICT, PHONE_CONFLICT, EMAIL_CONFLICT';
COMMENT ON COLUMN profile_match_reasons.score        IS 'Score contribution from this reason (0 for conflict reasons)';

CREATE INDEX IF NOT EXISTS idx_profile_match_reasons_candidate   ON profile_match_reasons(match_candidate_id);
CREATE INDEX IF NOT EXISTS idx_profile_match_reasons_reason_type ON profile_match_reasons(reason_type);
