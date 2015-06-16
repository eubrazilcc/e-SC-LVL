@echo off

call mvn archetype:generate ^
    -DarchetypeGroupId=com.connexience ^
    -DarchetypeArtifactId=workflow-block-java ^
    -DarchetypeVersion=3.1-SNAPSHOT ^
    -DgroupId=eu.eubrazilcloudconnect.esc ^
    -DartifactId=BlastN ^
    -Dversion=1.0-SNAPSHOT ^
