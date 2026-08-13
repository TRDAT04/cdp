# ── Stage 1: extract layered JAR ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /build
COPY target/vnpcdp-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 2: runtime image ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
EXPOSE 9000

RUN apk add --no-cache curl

# Non-root user — không chạy container bằng root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app

# Copy các layer theo thứ tự ít thay đổi → hay thay đổi để tận dụng Docker cache
# dependencies và spring-boot-loader hầu như không đổi → cache lâu dài
# application layer (code của mình) thay đổi thường xuyên nhất → copy cuối
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# JVM options — override từ ngoài bằng env var JAVA_OPTS
ENV JAVA_OPTS="\
  -Xms256m \
  -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Duser.timezone=Asia/Ho_Chi_Minh \
  -Dfile.encoding=UTF-8 \
"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
