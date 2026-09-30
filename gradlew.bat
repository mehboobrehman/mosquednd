@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set GRADLE_HOME=%APP_HOME%\gradle-home

if not exist "%GRADLE_HOME%" mkdir "%GRADLE_HOME%"

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if exist "%CLASSPATH%" (
    "C:\Users\mehboob.rehman\tools\jdk17.0.19_10\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) else (
    echo Gradle wrapper jar not found. Using system Gradle if available...
    where gradle >nul 2>&1
    if %errorlevel% == 0 (
        gradle %*
    ) else (
        echo ERROR: Gradle not found. Please install Gradle or download gradle-wrapper.jar
        echo Download from: https://github.com/gradle/gradle/releases/download/v8.2.0/gradle-8.2-wrapper.jar
        echo and place it in: %APP_HOME%\gradle\wrapper\gradle-wrapper.jar
        exit /b 1
    )
)
