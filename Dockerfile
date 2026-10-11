# Build and run RailYatra using Java 17. Render can build this Dockerfile directly.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/classes ./classes
ENV PORT=10000
ENV RAILYATRA_PUBLIC=true
EXPOSE 10000
CMD ["java", "-cp", "classes", "railway.Main"]
