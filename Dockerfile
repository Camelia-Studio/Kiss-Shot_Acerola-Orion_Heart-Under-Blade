FROM eclipse-temurin:25-jammy AS build
WORKDIR /src
COPY . .

RUN ./gradlew clean shadowJar
FROM eclipse-temurin:25-jammy AS runner
RUN mkdir -p /app
WORKDIR /app
COPY --from=build /src/build/libs/kiss-shot-acerola.jar /app/kiss-shot-acerola.jar

CMD ["java", "-jar", "--enable-native-access=ALL-UNNAMED", "kiss-shot-acerola.jar"]