# Quy tắc Messaging (Kafka / RabbitMQ)

Áp dụng khi thêm, sửa producer, consumer, hoặc luồng event trong hệ thống.

## Khi nào dùng messaging

Dùng async messaging khi:
- xử lý nghiệp vụ không cần response ngay (email, notification)
- tách luồng tốn thời gian ra khỏi request chính
- giao tiếp giữa các service bounded context
- đảm bảo at-least-once delivery cho critical business event

Không dùng messaging khi:
- cần synchronous response ngay lập tức
- transaction cần rollback toàn bộ khi một bước fail
- payload quá lớn hoặc frequency quá cao cho queue

## Producer

### Idempotency

Mọi message gửi ra phải có idempotency key (message ID hoặc deduplication key) để:
- consumer có thể deduplicate nếu nhận trùng
- retry không gây duplicate processing

```java
// ví dụ header
.setHeader("idempotency-key", UUID.randomUUID().toString())
```

### Event schema

- Dùng schema registry (Confluent Schema Registry, AWS Glue, hoặc tương đương) nếu team đã dùng
- Nếu không dùng schema registry: schema phải có version field rõ ràng
- KHÔNG thay đổi field name của event đang dùng (breaking change cho consumer)

### Topic naming

```
{domain}.{entity}.{action}
{domain}.{entity}.v{version}.{action}    → e-commerce.order.v1.created
```

## Consumer

### Error handling

- `RetryableException`: retry với exponential backoff
- `NonRetryableException`: gửi sang Dead Letter Queue (DLQ) hoặc log và skip
- KHÔNG throw exception mà không phân loại — consumer sẽ stuck trong infinite retry
- Đặt max retry limit và DLQ destination

### Idempotent processing

Consumer phải xử lý idempotent, vì:
- at-least-once delivery có thể gửi lại message
- retry sau khi partial failure có thể gửi trùng

```java
// Pattern: kiểm tra trạng thái trước khi xử lý
if (orderRepository.existsByProcessedEventId(event.getId())) {
    return; // skip đã xử lý
}
// xử lý + lưu processed event ID
```

### Ordering

- Nếu cần ordering: chỉ dùng single partition cho topic đó
- Nếu dùng multiple partition: không đảm bảo ordering giữa các partition
- Đặt `max.poll.records` phù hợp để tránh rebalance liên tục

## Transactional Outbox

**Luôn dùng Transactional Outbox** khi cần đồng thời:
1. Ghi vào DB
2. Gửi message

Pattern đúng:
1. Ghi vào DB + outbox table trong cùng transaction
2. Một process riêng (poller hoặc CDC) đọc outbox → gửi message → đánh dấu sent

KHÔNG gửi message trực tiếp trong transaction — nếu transaction rollback, message đã gửi không thu hồi.

## Dead Letter Queue

- Mọi consumer phải có DLQ destination
- DLQ message phải giữ nguyên: original topic, original payload, exception detail
- Có monitoring cho DLQ backlog

## Checklist review

Trước khi chốt:
- message có idempotency key chưa
- consumer có idempotent processing chưa
- exception có được phân loại Retryable/NonRetryable chưa
- có DLQ destination chưa
- nếu cần consistency: đang dùng Transactional Outbox chưa
- topic naming có đúng convention không
- event schema change có tương thích ngược không