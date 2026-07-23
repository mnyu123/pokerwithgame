# 1단계: 빌드 환경 구성
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
# 윈도우 환경에서 생성된 gradlew 파일의 실행 권한 부여 및 빌드
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 환경 구성 (가벼운 JRE 사용)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 빌드된 jar 파일을 app.jar로 복사
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080

# 메모리 256MB 제한 옵션을 적용하여 서버 실행
ENTRYPOINT ["java", "-Xms64m", "-Xmx128m", "-XX:MaxRAM=256m", "-Xss512k", "-jar", "app.jar"]