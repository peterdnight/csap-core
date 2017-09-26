#!/bin/bash

scriptDir=`dirname $0`
scriptName=`basename $0`



echo == loading $scriptDir/installFunctions.sh
source $scriptDir/installFunctions.sh

 
prompt Starting install

if [ "$targetFs" == "0" ] ; then
      
	printIt Running core installer first Host Setup Factory: $factory
	coreInstall
else 
	printIt Running a targeted install. Note operator must update kernel settings.
	# echo == Click enter to continue
	#read progress
	targetInstall $origParams
fi ;

if [ "$oracleFs" != "0" ] ; then
      
	printIt  oracleFs: $oracleFs
      
	prompt Install Oracle
	oracleInstall  $origParams

	
fi ;

if [ "$mqFs" != "0" ] ; then
      
	printIt Setting up fs and user for activemq:  mqFs: $mqFs

	prompt Install MQ
	mqInstall $origParams
	
fi ;


if [ "$csapFs" != "0" ] ; then
      
	printIt Installing csap: csapFs: $csapFs

	prompt Install ssadmin
	ssadminInstall $origParams
	
fi ;


