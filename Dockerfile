FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Resolve dependencies (ignoring errors due to spring-boot BOM bug)
RUN chmod +x mvnw && ./mvnw dependency:go-offline || true

COPY src ./src
# Build the application
# chmod defensivo: en un checkout desde Windows el bit de ejecucion puede perderse
# y la etapa de build fallaria con 'permission denied'.
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
