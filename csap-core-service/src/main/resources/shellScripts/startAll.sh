#!/bin/sh
#

# loads SERVERS var
source processNames.sh

curDir=`pwd`

# HACHAHACH HACK - copied into both rebuildAndDeploySvc AND startAll - copy changes into both
setupServer() {
	SERVER=$1 ;

	echo ====
	echo ==  Starting $SERVER
	echo ====
	echo
	echo 
	cd $SERVER; 
	svrDir=`pwd`

 	curDirName=`basename $svrDir` 
	 serverAndEnv=` echo "${curDirName%_*}"`
 	serviceName=` echo "${serverAndEnv%_*}"`
	 serviceEnv=` echo "${serverAndEnv#*_}"`
	 warName="$STAGING/warDist/$serviceName.war"
	startInstance.sh  $warName;
	cd $curDir
}


for server in $SERVERS
do
	echo
	echo Starting $server
	echo
	
	setupServer $server; 
done
