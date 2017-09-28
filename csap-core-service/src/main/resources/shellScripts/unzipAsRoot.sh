#!/bin/bash
#
# This does both zip and unzips of files , Triggered by TransferManager.java
#

scriptDir=`dirname $0`

if [ $# == 2 ] ; then 
	echo ==
	echo == compressing $1 to $2
	
	tarBaseDir=$1
	tarTargetDir="."
	
	if [ -f $1 ] ; then  
		# hook for files
		tarBaseDir=`dirname $1`
		tarTargetDir=`basename $1`
	fi ;
	
	
	tar Pcvzf $2 --directory $tarBaseDir $tarTargetDir
	
	if  [ "$USER" == "root" ]; then
		chown ssadmin $2 ;
	fi ;
	
	
elif [ $# == 3 ] ; then 
	echo ==
	echo == decompressing $1 to $2
	echo == chowning to $3
	
	if [[ $2 == /home/ssadmin/staging/warDist/*.secondary ]] ; then
	 	echo == found secondary files as unzip target, Wiping previous files in $2
		\rm -rf $2/* 
	fi ;
	
	mkdir -p $2
	if [[ $1 == *.tgz ]] ; then 
		# tarBaseDir=`dirname $2`
		tar Pxvzf $1 --directory $2
	else 
		unzip -o $1 -d $2 
	fi ;
	
	if [[ $2 == /home/ssadmin/staging* ]] ; then
		# Changing root file permissions can get very dicey. we only do on $STAGING Files
		echo Setting permissings to 755 on $2 
		chmod -R 755 $2 
	fi ;
	

	if  [ "$USER" == "root" ]; then
		chown -R $3 $2 
		chgrp -R $3 $2
	else
		echo == Running as non root user
	fi ;
	
	echo == Removing transferred file $1
	\rm -rf $1
	
elif [ $# == 4 ] ; then 
	echo ==
	echo == copying $1 to $2 
	echo == chowning to $3
	
	mkdir -p $2
	\cp -f $1 $2 
	
	if [[ $2 == /home/ssadmin/staging* ]] ; then
		# Changing root file permissions can get very dicey. we only do on $STAGING Files
		echo Special feature for staging folder, performing chmod 755 on $2 
		chmod -R 755 $2 
	fi ;
	if  [ "$USER" == "root" ]; then
		chown -R $3 $2 
		chgrp -R $3 $2
	else
		echo == Running as non root user
	fi ;
	
	\rm -rf $1
else 
	
	echo == zip params: sourceLocation, zipLocation
	echo == unzip params: tempLocation, targetLocation, targetUnixOwner
	echo == copy params: tempLocation, targetLocation, targetUnixOwner , isCopy
	exit
	
fi;

