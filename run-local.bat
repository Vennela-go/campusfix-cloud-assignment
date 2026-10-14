@echo off
if not exist target\campusfix.jar (
  echo JAR not found. Install Maven, then run: mvn clean package
  pause
  exit /b 1
)
java -jar target\campusfix.jar
