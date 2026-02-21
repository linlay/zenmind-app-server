FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE 8080
ENV AUTH_DB_PATH=/data/auth.db
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
