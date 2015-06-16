@echo off

call mvn -o archetype:generate ^
	-DarchetypeGroupId=com.connexience ^
	-DarchetypeArtifactId=workflow-library ^
	-DarchetypeVersion=3.1-SNAPSHOT ^
	-DgroupId=eu.eubrazilcloudconnect.esc ^
	-DartifactId=ClustalW ^
	-Dversion=2.1
