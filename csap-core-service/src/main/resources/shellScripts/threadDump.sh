#!/bin/sh
#
#
#

scriptDir=`dirname $0`
echo ====================================================================
echo == Running $0 $*
echo == script dir is $scriptDir 
echo == param count: $#


# svcInstance=$2 ;
source dirVars.sh


processList=`ps -ef | grep $svcInstance | grep -v -e grep -e $0 `
svcPid=`ps -ef | grep $svcInstance | grep -v -e grep -e $0   | awk '{ print $2 }'`

echo pid is $svcPid


echo ====================================================================
echo Running $0
echo  Sending kill -3 to trigger thread dump to $svcInstance
echo  pid is $svcPid
echo Look for output in startup
echo ====================================================================
/bin/kill -3 $svcPid
