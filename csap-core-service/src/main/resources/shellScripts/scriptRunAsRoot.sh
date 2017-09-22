#!/bin/bash
#
#
#

scriptDir=`dirname $0`
echo ====================================================================
echo == Running $1 as $2

if [ $# -lt 2 ] ; then 
	echo == params: scriptname targetUnixOwner
	exit ;
fi ;
# ensuring script is correct linefeeds
dos2unix $1
chmod 755 $1

if  [ "$USER" == "root" ]; then
	chown -R ssadmin $1
	chgrp -R ssadmin $1
else
	echo == Running as non root user
fi ;

# token to strip header
echo _CSAP_OUTPUT_

# support for running scripts as other users
if [ $2 != "root" ] && [ "$USER" == "root" ]; then 
	sudo su - $2 -c $1 
else
	$1
fi ;
