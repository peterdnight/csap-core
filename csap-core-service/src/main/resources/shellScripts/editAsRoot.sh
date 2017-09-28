#!/bin/bash
#
#
#

scriptDir=`dirname $0`
# params: tempLocation, targetLocation, targetUnixOwner, csapUserid

echo $4 uploaded $1, moving to $2 and chowning to $3

if [ $# -lt 4 ] ; then 
	echo == params: tempLocation, targetLocation, targetUnixOwner, csapUserid
	exit ;
fi ;

# source /home/ssadmin/.bashrc; native2ascii $1 $1
dos2unix $1

if  [ "$USER" == "root" ]; then
	chown -R ssadmin $1
	chgrp -R ssadmin $1
else 
	echo == running in non root mode
fi; 
	
chmod 755 $1
NOW=$(date +"%h-%d-%I-%M-%S")
backup="$2-$4-$NOW"

 if [ -e $2 ] ; then 
	\cp -f $2 $backup 
	
	if  [ "$USER" == "root" ]; then
		chown -R $3 $backup
		chgrp -R $3 $backup
	fi ; 
fi 

\cp -f $1 $2 

\rm -rf $1
# Changing root file permissions can get very dicey. we only do a chown
#chmod -R 755 $2 
#if [[ $2 == $STAGING* ]] ; then
	#	# Changing root file permissions can get very dicey. we only do on $STAGING Files
	#	echo Setting permissings to 755 on $2 
	#	chmod -R 755 $2 
#fi ;

if  [ "$USER" == "root" ]; then
	chown -R $3 $2
fi;

echo File saved, original was backed up to $backup


