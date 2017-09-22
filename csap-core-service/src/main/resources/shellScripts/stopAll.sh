#!/bin/sh
#
#
#


# loads SERVERS var
source processNames.sh

curDir=`pwd`


for server in $SERVERS
do
	echo
	echo Stopping $server
	echo
	
	cd $server; 
	killInstance.sh ;
	cd $curDir ;
done
