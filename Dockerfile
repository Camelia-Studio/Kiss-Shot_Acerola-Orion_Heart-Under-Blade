FROM eclipse-temurin:21-alpine AS pre-build
ENV JAVA_HOME=/opt/jdk/jdk-21.0.1+12
ENV PATH=$JAVA_HOME/bin:$PATH

ADD https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_x64_alpine-linux_hotspot_21.0.1_12.tar.gz /opt/jdk/
RUN tar -xzvf /opt/jdk/OpenJDK21U-jdk_x64_alpine-linux_hotspot_21.0.1_12.tar.gz -C /opt/jdk/
RUN ["jlink", "--compress=2", \
     "--module-path", "/opt/jdk/jdk-21.0.1+12/jmods/", \
     "--add-modules", "java.base,java.logging,java.naming,java.desktop,jdk.unsupported", \
     "--no-header-files", "--no-man-pages", \
     "--output", "/kiss-runtime"]

FROM eclipse-temurin:21-alpine AS build
WORKDIR /src
COPY . .

RUN ./gradlew clean shadowJar

FROM alpine:latest AS runner

COPY --from=pre-build  /kiss-runtime /opt/jdk
ENV PATH=$PATH:/opt/jdk/bin

RUN mkdir -p /app
WORKDIR /app
COPY --from=build /src/build/libs/kiss-shot-acerola.jar /app/kiss-shot-acerola.jar

CMD ["java", "-jar", "kiss-shot-acerola.jar"]