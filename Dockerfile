FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests -pl cash-reporting-app -am package

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /src/cash-reporting-app/target/cash-reporting-app-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
