FROM eclipse-temurin:25-jdk-alpine

WORKDIR /api

COPY build/libs/*.jar api.jar
COPY configurations/ configurations/
COPY geo/ geo/

EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "api.jar"]