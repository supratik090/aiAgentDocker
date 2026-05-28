# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
ARG MODULE_PATH="backend"
RUN if [ -x ./mvnw ]; then MVN="./mvnw"; else MVN="mvn"; fi \
    && if [ "$MODULE_PATH" = "." ]; then $MVN -DskipTests package; else $MVN -pl "$MODULE_PATH" -am -DskipTests package; fi

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG MODULE_PATH="backend"
COPY --from=build /workspace/${MODULE_PATH}/target/*.jar /app/app.jar
EXPOSE 9040
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
