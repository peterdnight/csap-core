#!/bin/bash 


printIt "checking for CSAP package API"


if [ "$skipApiExtract" == "" ] && [ ! -e $csapWorkingDir/scripts/consoleCommands.sh ] &&  [ ! -e $csapWorkingDir/csapApi.sh ] ; then
	
	printIt "Did not find api, extracting $STAGING/warDist/$serviceName.zip to $csapWorkingDir"
	
	printLine "Versioning support: deleting $csapWorkingDir/version"
	\rm -rf  $csapWorkingDir/version
	/usr/bin/unzip -o -qq $STAGING/warDist/$serviceName.zip -d $csapWorkingDir
	find $csapWorkingDir/scripts/* -name "*.*" -exec native2ascii '{}' '{}' \;
	chmod -R 755 $csapWorkingDir
fi ;

if [ -e $csapWorkingDir/csapApi.sh ] ; then 
	printIt "Loading: $csapWorkingDir/csapApi.sh" ;
	source $csapWorkingDir/csapApi.sh ;
	apiFound="true";
	
elif [ -e $csapWorkingDir/scripts/consoleCommands.sh ] ; then 
	printIt "Legacy api in use: $csapWorkingDir/scripts/consoleCommands.sh,  switch to csapApi.sh" ;
	source $csapWorkingDir/scripts/consoleCommands.sh ;
	apiFound="true";
		
else 
	printIt "Warning: did not find $csapWorkingDir/csapApi.sh"
	apiFound="false";
fi;