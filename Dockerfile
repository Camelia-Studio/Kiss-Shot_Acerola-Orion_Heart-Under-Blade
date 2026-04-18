FROM eclipse-temurin:25-jammy AS build
WORKDIR /src
COPY . .

RUN ./gradlew clean shadowJar
FROM eclipse-temurin:25-jammy AS runner
RUN mkdir -p /app
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libssl3 \
    libgcc-s1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/build/libs/kiss-shot-acerola.jar /app/kiss-shot-acerola.jar

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "kiss-shot-acerola.jar"]