# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# As dependências vêm primeiro para que só o pom invalidar o cache de download.
# Esta etapa só aquece o cache: se um plugin não resolver aqui, o package abaixo resolve.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Usuário sem privilégios; a pasta de uploads fica gravável para o modo storage=disk.
RUN addgroup -S planner && adduser -S planner -G planner \
    && mkdir -p /app/uploads && chown -R planner:planner /app

COPY --from=build --chown=planner:planner /build/target/*.jar /app/app.jar

USER planner

# Mesma porta padrão do application.yml; mude com SERVER_PORT.
ENV SERVER_PORT=8081
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT}"]
