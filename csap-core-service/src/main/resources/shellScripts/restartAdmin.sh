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
echo == 
echo == This script invokes first kills then restarts the CsAgent Jvm. It will infer CsAgent
echo == lifecycle from the host name.
echo == 
echo == 
echo ====================================================================

# make sure we are in a stable directory. Running this from processing can cause handles to go stale.

cd $STAGING;

if [ "$#" == "1" ] ; then
	
	# this will exit if host does not exist
	set -e
	cloneHost="$1"
	echo ==  cloneHost specified: $cloneHost , using wget http://$cloneHost:8011/CsAgent/os/getConfigZip
 	# scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -oBatchmode="yes" -p -r $cloneHost:staging/conf $HOME/staging
 	rm -rf getConfigZip*
 	wget http://$cloneHost:8011/CsAgent/os/getConfigZip
 	echo == blowing away previous configuration files
 	rm -rf $STAGING/conf
 	unzip -o -d $STAGING/conf getConfigZip
fi ;

# set -x verbose
if [ $# == 0 ] ; then 
	echo;echo; echo ==
	echo == WARNING: you should aways specify a host to clone configuration from. This host may be out of sync with cluster which can lead to problems
	echo ==
fi
killInstance.sh -d

