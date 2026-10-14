FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 campusfix
COPY target/campusfix.jar /app/campusfix.jar
USER 10001
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/campusfix.jar"]
