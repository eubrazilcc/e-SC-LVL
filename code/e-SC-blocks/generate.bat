@echo off

call mvn archetype:generate ^
    -DarchetypeGroupId=com.connexience ^
    -DarchetypeArtifactId=workflow-library-binary-standalone ^
    -DarchetypeVersion=3.1-SNAPSHOT ^
    -DgroupId=eu.eubrazilcloudconnect.esc ^
    -DartifactId=OphidiaTerminal ^
    -Dversion=1.0 ^
