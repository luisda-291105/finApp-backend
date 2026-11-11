FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copiar primero el manifiesto permite reutilizar la caché de dependencias.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/fin_app_back-0.0.1-SNAPSHOT.jar /app/app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser /app/app.jar ./app.jar

USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
