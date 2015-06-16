@echo off

rem # This must be absolute path to the java keystore file
set TRUSTSTORE_PATH=d:\cala\projects\EUBrazilCC\code\keys\eubcc.jks
echo Your keystore is: %TRUSTSTORE_PATH%

rem # This is the password that protects the keystore.
set /P TRUSTSTORE_PASSWORD=Please provide keystore password:
rem # If you want to store password inline, just use the following command instead:
rem # set TRUSTSTORE_PASSWORD=???

set MAVEN_OPTS=-Djavax.net.ssl.trustStore=%TRUSTSTORE_PATH% -Djavax.net.ssl.trustStorePassword=%TRUSTSTORE_PASSWORD%
