#!/bin/bash
#
#
#

scriptDir=`dirname $0`

if [ -z "$STAGING" ] ; then 
	echo Warning - Did not find STAGING env variable. Resourcing $HOME/.bashrc
	source $HOME/.bashrc
fi


echo;echo;echo;
echo ====================================================================
echo JobRunner invoked on $HOSTNAME              at `date "+%x %H:%M:%S %Nms"`
echo ====================================================================



#echo == Syntax:  optional wipe out runtime dir: -clean: $isClean
#echo == $1 $2 $3

# svcInstance=$2 ;
source dirVars.sh

showIfDebug Running $0 : dir is $scriptDir
showIfDebug param count: $#
showIfDebug params: $@

if [ ! -e "$csapJob" ] ; then
	printIt Did not find $csapJob
	exit;
fi;
printIt launching new background job: $csapJob , output is $csapLogDir/$outputFile 
set -x
nohup $csapJob 2>&1 > $csapLogDir/$outputFile &