#!/bin/bash 


echo == checking if we need to extract wrapper commands

if [ ! -e $runDir/scripts/consoleCommands.sh ] ; then
	echo ==
	echo == Did not find a wrapper...extracting $STAGING/warDist/$serviceName.zip to $runDir
	echo ==
	
	echo = hook for versioning in wrappers, deleting $runDir/version
	\rm -rf  $runDir/version
	/usr/bin/unzip -o -qq $STAGING/warDist/$serviceName.zip -d $runDir
	find $runDir/scripts/* -name "*.*" -exec native2ascii '{}' '{}' \;
	chmod -R 755 $runDir
fi ;