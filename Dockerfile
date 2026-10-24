# syntax=docker/dockerfile:1

# build.gradle 의 Java toolchain 버전과 맞춘다 (Railway 에서는 서비스 변수 JAVA_VERSION 으로 바꿀 수 있다)
ARG JAVA_VERSION=21

# ---- 빌드: Gradle wrapper 로 실행 jar 를 만든다 (테스트는 CI·로컬에서 돌린다)
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# ---- 실행: JRE 만 담은 이미지
FROM eclipse-temurin:${JAVA_VERSION}-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
# 컨테이너 메모리 한도에 맞춰 힙 크기를 정한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Railway 는 PORT 환경변수를 넣어 준다 (application.properties 의 server.port=${PORT:8080})
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
