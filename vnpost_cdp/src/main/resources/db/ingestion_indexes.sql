-- ============================================================
-- CDP Ingestion Performance Indexes
-- Apply AFTER schema.sql and profile_identity_schema.sql
-- ============================================================

-- ============================================================
-- master_profiles — additional ingestion query indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_mp_identity_no       ON master_profiles(identity_no)       WHERE identity_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mp_phone             ON master_profiles(phone)             WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mp_email             ON master_profiles(email)             WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mp_status            ON master_profiles(status);
CREATE INDEX IF NOT EXISTS idx_mp_last_merged_at    ON master_profiles(last_merged_at);
CREATE INDEX IF NOT EXISTS idx_mp_synced_to_unomi   ON master_profiles(synced_to_unomi_at) WHERE synced_to_unomi_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mp_merged_into       ON master_profiles(merged_into_profile_id) WHERE merged_into_profile_id IS NOT NULL;

-- ============================================================
-- profile_source_records — ingestion pipeline queries
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_psr_identity_key     ON profile_source_records(identity_key)   WHERE identity_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_psr_processed_at     ON profile_source_records(processed_at)   WHERE processed_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_psr_source_event_id  ON profile_source_records(source_event_id) WHERE source_event_id IS NOT NULL;
-- Composite: pending records for reprocess jobs
CREATE INDEX IF NOT EXISTS idx_psr_status_received  ON profile_source_records(merge_status, received_at);
-- Composite: source system + customer for dedup check
CREATE INDEX IF NOT EXISTS idx_psr_src_customer     ON profile_source_records(source_system, source_customer_id);

-- ============================================================
-- profile_identity_links — matching queries
-- ============================================================
-- Composite: most common matching query — source system + customer ID
CREATE INDEX IF NOT EXISTS idx_pil_src_customer     ON profile_identity_links(source_system, source_customer_id) WHERE status = 1;
-- Composite: identity type + value (full lookup)
CREATE INDEX IF NOT EXISTS idx_pil_type_value       ON profile_identity_links(identity_type, identity_value)     WHERE status = 1;
-- For primary link lookups
CREATE INDEX IF NOT EXISTS idx_pil_master_primary   ON profile_identity_links(master_profile_id, is_primary)    WHERE is_primary = true;

-- ============================================================
-- profile_attribute_values — attribute history queries
-- ============================================================
-- Composite: profile + property + selected
CREATE INDEX IF NOT EXISTS idx_pav_profile_property ON profile_attribute_values(master_profile_id, property_name);
-- Composite: profile + source system
CREATE INDEX IF NOT EXISTS idx_pav_profile_source   ON profile_attribute_values(master_profile_id, source_system);
-- For "show only selected values" queries
CREATE INDEX IF NOT EXISTS idx_pav_selected_profile ON profile_attribute_values(master_profile_id, property_name, is_selected) WHERE is_selected = true;

-- ============================================================
-- profile_change_logs — audit trail queries
-- ============================================================
-- Composite: profile + changed_at (timeline view)
CREATE INDEX IF NOT EXISTS idx_pcl_profile_time     ON profile_change_logs(master_profile_id, changed_at DESC);
-- Composite: property + event type
CREATE INDEX IF NOT EXISTS idx_pcl_prop_event       ON profile_change_logs(property_name, event_type);

-- ============================================================
-- profile_merge_conflicts — admin review queries
-- ============================================================
-- Open conflicts by creation time (default admin list view)
CREATE INDEX IF NOT EXISTS idx_pmc_open_created     ON profile_merge_conflicts(resolution_status, created) WHERE resolution_status = 0;
-- Composite: profile + open status
CREATE INDEX IF NOT EXISTS idx_pmc_profile_open     ON profile_merge_conflicts(master_profile_id, resolution_status);

-- ============================================================
-- profile_merge_requests — admin merge management
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_pmrq_pending         ON profile_merge_requests(status, requested_at)     WHERE status = 0;
CREATE INDEX IF NOT EXISTS idx_pmrq_completed       ON profile_merge_requests(status, completed_at)     WHERE status = 3;

-- ============================================================
-- profile_merge_jobs — batch job monitoring
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_pmj_type_status      ON profile_merge_jobs(job_type, status);
CREATE INDEX IF NOT EXISTS idx_pmj_running          ON profile_merge_jobs(status, started_at)           WHERE status = 1;

-- ============================================================
-- profile_unomi_sync_logs — sync monitoring & retry
-- ============================================================
-- Failed syncs for retry jobs
CREATE INDEX IF NOT EXISTS idx_pusl_failed          ON profile_unomi_sync_logs(status, synced_at)       WHERE status = 2;
-- Composite: profile + sync type for sync history
CREATE INDEX IF NOT EXISTS idx_pusl_profile_type    ON profile_unomi_sync_logs(master_profile_id, sync_type);
-- Composite: profile code for Unomi-side lookups
CREATE INDEX IF NOT EXISTS idx_pusl_code_status     ON profile_unomi_sync_logs(profile_code, status);

-- ============================================================
-- customer_events — event search & Customer 360 timeline
-- ============================================================
-- Primary lookup: event tra cứu theo profile (Customer 360 timeline view)
CREATE INDEX IF NOT EXISTS idx_ce_profile_time       ON customer_events(master_profile_id, occurred_at DESC);
-- Filter: theo loại event
CREATE INDEX IF NOT EXISTS idx_ce_event_type         ON customer_events(event_type);
-- Filter: theo hệ thống nguồn
CREATE INDEX IF NOT EXISTS idx_ce_source_system      ON customer_events(source_system);
-- Filter: theo session
CREATE INDEX IF NOT EXISTS idx_ce_session_id         ON customer_events(session_id) WHERE session_id IS NOT NULL;
-- Filter: theo khoảng thời gian
CREATE INDEX IF NOT EXISTS idx_ce_occurred_at        ON customer_events(occurred_at DESC);
-- Unique event code lookup (tra cứu chi tiết 1 event)
CREATE INDEX IF NOT EXISTS idx_ce_event_code         ON customer_events(event_code);
-- Retry job: lấy các event PENDING hoặc FAILED chưa sync Unomi
CREATE INDEX IF NOT EXISTS idx_ce_sync_status        ON customer_events(sync_status) WHERE sync_status IN (0, 2);
