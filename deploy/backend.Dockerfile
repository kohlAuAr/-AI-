FROM eclipse-temurin:17-jre-jammy@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar app.jar
RUN groupadd -g 10001 campus && useradd -u 10001 -g campus -M campus
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
