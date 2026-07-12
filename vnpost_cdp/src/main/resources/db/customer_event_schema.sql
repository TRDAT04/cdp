-- ============================================================
-- Customer Events Schema
-- Apply AFTER schema.sql và profile_identity_schema.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS customer_events (
    id                  BIGSERIAL PRIMARY KEY,

    -- Unique identifier = messageId từ Kafka (UUID)
    event_code          VARCHAR(100) NOT NULL UNIQUE,

    -- Profile mapping (nullable: NULL khi event UNMATCHED chưa tìm được profile)
    master_profile_id   BIGINT,
    profile_code        VARCHAR(100),

    -- Event metadata
    event_type          VARCHAR(100) NOT NULL,
    session_id          VARCHAR(255),
    source_system       VARCHAR(100) NOT NULL,
    source_customer_id  VARCHAR(255) NOT NULL,
    occurred_at         TIMESTAMP,

    -- Payload linh hoạt (JSONB)
    properties          JSONB,
    source              JSONB,
    target              JSONB,

    -- Trạng thái sync sang Apache Unomi
    -- 0=PENDING, 1=SUCCESS, 2=FAILED, 3=UNMATCHED
    sync_status         SMALLINT NOT NULL DEFAULT 0,
    synced_to_unomi_at  TIMESTAMP,

    -- Audit fields (từ BaseEntity)
    created_by          VARCHAR(100),
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified            TIMESTAMP,
    modified_by         VARCHAR(100),

    CONSTRAINT fk_ce_master_profile
        FOREIGN KEY (master_profile_id)
        REFERENCES master_profiles(id)
);

COMMENT ON TABLE  customer_events                    IS 'Lưu toàn bộ hành vi, giao dịch, tương tác của khách hàng từ mọi source system';
COMMENT ON COLUMN customer_events.event_code         IS 'UUID = messageId từ Kafka, dùng để dedup';
COMMENT ON COLUMN customer_events.master_profile_id  IS 'NULL nếu event chưa match được profile (UNMATCHED)';
COMMENT ON COLUMN customer_events.profile_code       IS 'NULL nếu event chưa match được profile (UNMATCHED)';
COMMENT ON COLUMN customer_events.event_type         IS 'Loại sự kiện: purchase, login, view_product, search, ...';
COMMENT ON COLUMN customer_events.properties         IS 'Payload tùy ý theo từng event_type (JSONB)';
COMMENT ON COLUMN customer_events.source             IS 'Nguồn phát sinh event (hệ thống, kênh, thiết bị) (JSONB)';
COMMENT ON COLUMN customer_events.target             IS 'Đối tượng bị tác động bởi event (sản phẩm, đơn hàng, ...) (JSONB)';
COMMENT ON COLUMN customer_events.sync_status        IS '0=PENDING, 1=SUCCESS, 2=FAILED, 3=UNMATCHED';

////////////////////////
CREATE TABLE cdp_event_schemas (
    id BIGSERIAL PRIMARY KEY,

    event_type VARCHAR(100) NOT NULL,

    schema_version VARCHAR(50) NOT NULL,

    source_system VARCHAR(100),

    json_schema JSONB NOT NULL,

    description TEXT,

    created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    modified TIMESTAMP,

    created_by VARCHAR(100),

    modified_by VARCHAR(100),

    CONSTRAINT uq_event_schema UNIQUE(event_type, schema_version)
);