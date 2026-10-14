@echo off
mvn clean package
if errorlevel 1 exit /b 1
python scripts\smoke_test.py target\campusfix.jar
