#!/bin/bash
#
#
#

scriptDir=`dirname $0`
echo ====================================================================
echo == Running $0 : 
echo == script dir is $scriptDir
echo == param count: $#
echo == $1 $2 $3
echo == using `pwd` to extract settings: contextRoot_env_HttpPort
echo ====================================================================

source dirVars.sh


if [ "$serviceName" == "GLOBAL" ] ; then
	echo ==
	echo == hook for CsAgent - doing FUll clean of caches.
	echo ==
	set -x 
	rm -rf $STAGING/temp
	rm -rf $STAGING/mavenRepo/*
	rm -rf $STAGING/build/*
	
else
	echo ==
	echo == purging primary caches for $serviceName, purge CsAgent to do ALL caches
	echo == 
	set -x 
	rm -rf $STAGING/mavenRepo/com/
	rm -rf $STAGING/build/$serviceName*
fi

set +x 
if [[ $serviceName == *httpd* ]] ; then
	echo ==
	echo == service name contains httpd, removing $STAGING/httpdConf 
	echo == == restart CsAgent on httpd servers to trigger httpd config generation
	echo ==
	set -x 
	rm -rf $STAGING/httpdConf
fi