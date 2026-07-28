# Hook: migration-safety

Mục tiêu:
Khi thay đổi DB, migration, entity hoặc query quan trọng, phải rà migration safety trước khi chốt.

## Bắt buộc kiểm tra
1. Migration strategy
- có đang sửa migration cũ không
- migration mới có tên rõ nghĩa không
- thay đổi có additive trước được không

2. Tương thích ngược
- code cũ có còn chạy được khi schema đang ở giữa rollout không
- client hoặc job cũ có bị ảnh hưởng không
- có cần deploy nhiều bước không
