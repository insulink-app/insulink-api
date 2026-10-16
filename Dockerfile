FROM eclipse-temurin:25-jdk

WORKDIR /api

# Headless Claude Code for the meal photo estimate (docs/MEAL_PHOTO_ESTIMATE.md).
# It signs in through CLAUDE_CODE_OAUTH_TOKEN, passed in by docker compose.
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
  && curl -fsSL https://claude.ai/install.sh | bash \
  && rm -rf /var/lib/apt/lists/*
ENV PATH="/root/.local/bin:${PATH}" DISABLE_AUTOUPDATER=1

COPY build/libs/*.jar api.jar
COPY configurations/ configurations/
COPY geo/ geo/

EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "api.jar"]