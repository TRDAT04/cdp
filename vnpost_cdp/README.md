# vnpost_cdp

VNPost CDP Backend — Customer Data Platform backend built with Java 21 and Spring Boot 3.x.

## Introduction

`vnpost_cdp` is the CDP backend responsible for:
- Receiving profile/event data from multiple source systems
- Normalizing data before sending to Apache Unomi
- Integrating with Apache Unomi 3.0.0 via REST APIs
- Consuming Kafka events for profile processing
- Providing JWT-authenticated REST APIs for profile management

`profile_code` is the unique profile identifier in CDP and is used as `profileId/itemId` when sending data to Unomi.

---

## Technologies

| Technology | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.x |
| Maven | 3.8+ |
| PostgreSQL | 14+ |
| Apache Kafka | 3.x |
| Apache Unomi | 3.0.0 |
| Lombok | latest |

---

## How to Run

### Prerequisites

- Java 21
- Maven 3.8+
- PostgreSQL 14+
- Kafka 3.x

### Steps

```bash
# 1. Clone and enter the project
cd vnpost_cdp

# 2. Create the PostgreSQL database
createdb vnpost_cdp

# 3. Run the schemas in order
psql -d vnpost_cdp -f src/main/resources/db/schema.sql
psql -d vnpost_cdp -f src/main/resources/db/profile_identity_schema.sql
psql -d vnpost_cdp -f src/main/resources/db/ingestion_indexes.sql

# 4. Build
mvn clean package -DskipTests

# 5. Run
java -jar target/vnpost_cdp-1.0.0-SNAPSHOT.jar
```

Or with Maven directly:

```bash
mvn spring-boot:run
```

---

## PostgreSQL Configuration

Default values (can be overridden via environment variables):

| Property | Default | Env Variable |
|---|---|---|
| Host | localhost | DB_HOST |
| Port | 5432 | DB_PORT |
| Database | vnpost_cdp | DB_NAME |
| Username | postgres | DB_USERNAME |
| Password | postgres | DB_PASSWORD |

Example:

```bash
export DB_HOST=192.168.1.100
export DB_NAME=vnpost_cdp_prod
export DB_USERNAME=appuser
export DB_PASSWORD=secret
```

---

## Kafka Configuration

| Property | Default | Env Variable |
|---|---|---|
| Bootstrap Servers | 172.23.0.17:9092 | KAFKA_BOOTSTRAP_SERVERS |
| Group ID | vnpost-cdp-group | KAFKA_GROUP_ID |
| Profile Event Topic | cdp.profile.events | KAFKA_PROFILE_EVENT_TOPIC |

---

## Apache Unomi 3.0.0 Configuration

| Property | Default | Env Variable |
|---|---|---|
| Base URL | http://localhost:8181 | UNOMI_BASE_URL |
| Username | karaf | UNOMI_USERNAME |
| Password | karaf | UNOMI_PASSWORD |
| Connection Timeout (ms) | 5000 | UNOMI_CONNECTION_TIMEOUT_MS |
| Response Timeout (ms) | 15000 | UNOMI_RESPONSE_TIMEOUT_MS |

---

## JWT / Spring Security Configuration

| Property | Default | Env Variable |
|---|---|---|
| Issuer URI | (empty) | JWT_ISSUER_URI |
| JWK Set URI | (empty) | JWT_JWK_SET_URI |
| Client ID | vnpost-cdp | JWT_CLIENT_ID |

---

## API Reference

### Base URL

```
http://localhost:9001
```

### Public Endpoints (no auth required)

```
GET /actuator/health
GET /actuator/info
```

### Profile APIs (JWT required)

| Method | Path | Description |
|---|---|---|
| POST | /v1/admin/profiles | Create a profile |
| PUT | /v1/admin/profiles/{id} | Update a profile |
| GET | /v1/admin/profiles/{id} | Get profile by ID |
| GET | /v1/admin/profiles/code/{profileCode} | Get profile by code |
| POST | /v1/admin/profiles/{id}/sync-unomi | Sync profile to Unomi |

---

## Example API Calls

### Create a Profile

```bash
curl -X POST http://localhost:9001/v1/admin/profiles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "profileCode": "MP_20260626_000001",
    "fullName": "Nguyễn Văn A",
    "phone": "0988888888",
    "email": "a@gmail.com",
    "gender": "male",
    "identityNo": "0123456789",
    "customerType": "PERSONAL"
  }'
```

### Get a Profile by ID

```bash
curl -X GET http://localhost:9001/v1/admin/profiles/1 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Get a Profile by Code

```bash
curl -X GET http://localhost:9001/v1/admin/profiles/code/MP_20260626_000001 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Update a Profile

```bash
curl -X PUT http://localhost:9001/v1/admin/profiles/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "fullName": "Nguyễn Văn B",
    "phone": "0977777777"
  }'
```

### Sync Profile to Apache Unomi

```bash
curl -X POST http://localhost:9001/v1/admin/profiles/1/sync-unomi \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

---

## Kafka Profile Ingestion Flow

### Architecture

```
Test API (POST /v1/test/profile-ingestion/send)
    │
    ▼
ProfileIngestionProducer  ──► Kafka topic: cdp.profile.events
                                            │
                                            ▼
                               ProfileEventConsumer (manual ack)
                                            │
                                            ▼
                               ProfileIngestionServiceImpl
                                 │
                                 ├─ 1. Save ProfileSourceRecord (PENDING)
                                 ├─ 2. ProfileNormalizationService → NormalizedProfileData
                                 ├─ 3. ProfileMatchingService → List<MasterProfile> candidates
                                 ├─ 4. ProfileMergeDecisionService → MergeDecision
                                 └─ 5. ProfileMergeExecutorService
                                         ├─ CREATE_NEW_PROFILE → new MasterProfile + profileCode
                                         ├─ AUTO_MERGE        → update existing MasterProfile
                                         ├─ NEED_REVIEW       → ProfileMergeConflict (field-level)
                                         ├─ CONFLICT          → ProfileMergeConflict (PROFILE_MATCH)
                                         └─ REJECT            → sourceRecord status = REJECTED
                                                    │
                                                    ▼
                                         Unomi sync → ProfileUnomiSyncLog
```

### Merge Decision Rules

| Condition | Decision |
|---|---|
| Unknown source system | REJECT |
| No identity fields at all | REJECT |
| 0 candidates found | CREATE_NEW_PROFILE |
| > 1 candidates | CONFLICT |
| Already linked via sourceSystem+sourceCustomerId | AUTO_MERGE |
| identityNo exact match | AUTO_MERGE |
| Phone exact match | AUTO_MERGE |
| Email match, no CMS phone conflict | AUTO_MERGE |
| CMS sends different identityNo than CRM | NEED_REVIEW |
| CRM sends different identityNo than existing | NEED_REVIEW |
| CRM fallback (soft match) | AUTO_MERGE |
| CMS fallback (soft match) | NEED_REVIEW |

### Source Priority

CRM has priority over CMS for identity fields: `fullName`, `phone`, `email`, `identityNo`, `gender`, `dateOfBirth`, `customerType`, `provinceCode`, `provinceName`, `unitCode`, `unitName`.

CMS may only update: `interestedServices`, `lastVisitAt`, and blank identity fields CRM has not yet populated.

### profileCode Format

```
MP_yyyyMMdd_XXXXXXXX
```

Example: `MP_20260627_A1B2C3D4`

`profileCode` is also the `profileId` / `itemId` used in Apache Unomi — no separate Unomi ID is stored.

---

## Kafka Ingestion Test API

These endpoints simulate CRM/CMS data submission without needing the real systems. No JWT required for test endpoints (configured as `/v1/test/**` public).

### Send a custom ingestion event

```bash
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceCustomerId": "CRM-001",
    "eventType": "PROFILE_CREATED",
    "payload": {
      "fullName": "Nguyễn Văn A",
      "phone": "0988888888",
      "email": "a@example.com",
      "identityNo": "012345678901",
      "gender": "male",
      "dateOfBirth": "1990-05-15",
      "customerType": "PERSONAL",
      "provinceCode": "01",
      "provinceName": "Hà Nội"
    }
  }'
```

### Send a CRM sample (pre-filled)

```bash
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send-crm-sample \
  -H "Content-Type: application/json"
```

### Send a CMS sample (pre-filled)

```bash
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send-cms-sample \
  -H "Content-Type: application/json"
```

### Send a CRM update (AUTO_MERGE scenario)

```bash
# First, create the profile via CRM
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceCustomerId": "CRM-002",
    "eventType": "PROFILE_CREATED",
    "payload": {
      "fullName": "Trần Thị B",
      "phone": "0977777777",
      "identityNo": "098765432101"
    }
  }'

# Then update from same CRM source → triggers AUTO_MERGE
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceCustomerId": "CRM-002",
    "eventType": "PROFILE_UPDATED",
    "payload": {
      "fullName": "Trần Thị B",
      "phone": "0977777777",
      "email": "b@example.com",
      "identityNo": "098765432101",
      "customerType": "VIP"
    }
  }'
```

### Send a CMS event that conflicts with CRM (NEED_REVIEW scenario)

```bash
# CRM already has identityNo=012345678901
# CMS sends a different identityNo → NEED_REVIEW conflict
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CMS",
    "sourceCustomerId": "CMS-001",
    "eventType": "REGISTER_FORM",
    "payload": {
      "phone": "0988888888",
      "identityNo": "DIFFERENT_ID_999",
      "interestedServices": ["savings", "insurance"],
      "lastVisitAt": "2026-06-27T10:00:00"
    }
  }'
```

### Send two different source records that match the same person (CONFLICT scenario)

```bash
# Source 1 — create via CRM
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceCustomerId": "CRM-ALPHA",
    "eventType": "PROFILE_CREATED",
    "payload": { "phone": "0966666666", "fullName": "Lê Văn C" }
  }'

# Source 2 — same phone from CMS → second candidate doesn't exist yet,
# but if two CRM records share the same phone, they become CONFLICT
curl -X POST http://localhost:9001/v1/test/profile-ingestion/send \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem": "CRM",
    "sourceCustomerId": "CRM-BETA",
    "eventType": "PROFILE_CREATED",
    "payload": { "phone": "0966666666", "fullName": "Lê Văn C (duplicate)" }
  }'
```

---

---

## Profile Match Candidate Feature

This feature detects possible duplicate customer profiles from different source systems (CRM, CMS, PORTAL, MYVNPOST) and shows match suggestions to admin for review.

### New SQL Files

Run in order after the existing schemas:

```bash
psql -d vnpost_cdp -f src/main/resources/db/profile_match_schema.sql
```

### New Tables

- `profile_match_candidates` — pairs of profiles that may be the same real customer
- `profile_match_reasons` — per-field reasons explaining why two profiles matched

### Match Score Rules

| Criterion | Points |
|---|---|
| Identity number exact match | +50 |
| Phone exact match (normalized) | +35 |
| Email exact match (lowercase) | +30 |
| Full name exact match | +25 |
| Name similarity >= 90% | +20 |
| Name similarity >= 85% | +15 |
| Name similarity >= 75% | +10 |
| Date of birth match | +20 |
| Province code match | +10 |
| Unit code match | +5 |
| **Maximum** | **100** |

Match levels: `VERY_HIGH` (>=95), `HIGH` (>=85), `MEDIUM` (>=70), `LOW` (<70). Candidates are only created when score >= 70.

Identity conflict (when both sides differ on identityNo, or phone+email): the candidate is still created if score >= 70, but `autoMergeRecommended = false`.

### Admin Merge Flow

1. Admin reviews the PENDING candidate card showing both profiles, score, reasons, and source.
2. Admin clicks **Merge Customer** → `POST /v1/admin/profile-match-candidates/{id}/merge`.
3. System picks target profile (or admin specifies `targetMasterProfileId`). Target selection priority: CRM > MYVNPOST > PORTAL > CMS.
4. Source profile fields fill in blanks on target (no overwrite of existing values).
5. Identity links and attribute values are copied from source to target.
6. Source profile status is set to `MERGED`, `mergedIntoProfileId` = target id.
7. A `profile_merge_requests` record is created (status = COMPLETED).
8. A `profile_change_logs` record is written (eventType = `ADMIN_MERGE`).
9. Target profile is synced to Apache Unomi via WebClient. Result written to `profile_unomi_sync_logs`.
10. Candidate status is set to `MERGED`.

### Kafka Integration

After Kafka ingestion creates or auto-merges a profile, `detectAndCreateCandidatesForProfile(profileId)` is called automatically. Detection is non-blocking — any error during detection does not affect the ingestion result.

### Profile Match Candidate APIs

#### List pending candidates

```bash
curl -X GET http://localhost:9001/v1/admin/profile-match-candidates/pending \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Search with filters

```bash
curl -X GET "http://localhost:9001/v1/admin/profile-match-candidates?status=0&matchLevel=HIGH&minScore=85" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Get candidate detail

```bash
curl -X GET http://localhost:9001/v1/admin/profile-match-candidates/1 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### List by profile

```bash
curl -X GET http://localhost:9001/v1/admin/profile-match-candidates/by-profile/1 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### List by status

```bash
curl -X GET http://localhost:9001/v1/admin/profile-match-candidates/status/0 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Manually create candidate from two profiles

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "leftMasterProfileId": 1,
    "rightMasterProfileId": 2
  }'
```

#### Merge candidate (admin specifies target)

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates/1/merge \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "targetMasterProfileId": 1,
    "mergeReason": "Same phone number and similar name"
  }'
```

#### Merge candidate (auto target selection)

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates/1/merge \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "mergeReason": "Same phone number and similar name"
  }'
```

#### Ignore candidate

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates/1/ignore \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Reject candidate

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates/1/reject \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Manually trigger detection for a profile

```bash
curl -X POST http://localhost:9001/v1/admin/profile-match-candidates/detect/1 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Duplicate Prevention

- Same pair with status PENDING or MERGED → do not create again.
- Same pair with status IGNORED or REJECTED → only recreate if new score is >= 10 points higher. Old candidate is expired.

---

## Response Format

All API responses follow this format:

```json
{
  "success": true,
  "message": "Success",
  "data": {}
}
```

Error response:

```json
{
  "success": false,
  "message": "Profile code already exists: MP_20260626_000001",
  "data": null
}
```
