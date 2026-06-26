@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem Gradle startup script for Windows
@rem ##########################################################################
set APP_BASE_NAME=%~n0
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set JAVACMD=java
if defined JAVA_HOME set JAVACMD=%JAVA_HOME%\bin\java.exe
"%JAVACMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
