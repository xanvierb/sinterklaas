FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN install -d -o 10001 -g 10001 /app/data /app/uploads
COPY --from=build /build/target/sinterklaas-*.jar /app/sinterklaas.jar
ENV SPRING_PROFILES_ACTIVE=postgres \
    SINT_UPLOAD_DIR=/app/uploads
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sinterklaas.jar"]
