CREATE TABLE IF NOT EXISTS master_profiles (
    id BIGSERIAL PRIMARY KEY,

    profile_code VARCHAR(100) NOT NULL UNIQUE,

    full_name VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(255),
    gender VARCHAR(20),
    date_of_birth DATE,
    identity_no VARCHAR(100),

    customer_type VARCHAR(100),

    province_code VARCHAR(50),
    province_name VARCHAR(255),
    unit_code VARCHAR(50),
    unit_name VARCHAR(255),

    source_summary JSONB,

    last_merged_at TIMESTAMP,
    synced_to_unomi_at TIMESTAMP,

    merged_into_profile_id BIGINT,

    status SMALLINT NOT NULL DEFAULT 1,

    created_by VARCHAR(100),
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP,
    modified_by VARCHAR(100),

    CONSTRAINT fk_master_profiles_merged_into
        FOREIGN KEY (merged_into_profile_id)
        REFERENCES master_profiles(id)
);

CREATE INDEX IF NOT EXISTS idx_master_profiles_profile_code
    ON master_profiles(profile_code);

CREATE INDEX IF NOT EXISTS idx_master_profiles_phone
    ON master_profiles(phone);

CREATE INDEX IF NOT EXISTS idx_master_profiles_email
    ON master_profiles(email);

CREATE INDEX IF NOT EXISTS idx_master_profiles_identity_no
    ON master_profiles(identity_no);

CREATE INDEX IF NOT EXISTS idx_master_profiles_status
    ON master_profiles(status);
