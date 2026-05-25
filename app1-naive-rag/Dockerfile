FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
ARG CACHEBUST=2
COPY pom.xml .
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/naive-rag-1.0.0-SNAPSHOT.jar app.jar
COPY src/main/resources/render-startup.sh render-startup.sh
RUN chmod +x render-startup.sh
EXPOSE 8080
ENTRYPOINT ["sh", "render-startup.sh"]
