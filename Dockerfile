FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY uploads ./uploads
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx240m", \
  "-Xms64m", \
  "-Xss256k", \
  "-XX:MaxMetaspaceSize=100m", \
  "-XX:ReservedCodeCacheSize=48m", \
  "-XX:+UseSerialGC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
