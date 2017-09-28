#!/bin/sh
#
#
#

scriptDir=`dirname $0`
echo ====================================================================
echo == Running $0 $*
echo == script dir is $scriptDir 
echo == param count: $#

isClean=`expr match "$* " '.*-clean' != 0`
echo == Syntax:  optional wipe out runtime dir: -clean: $isClean
echo == $1 $2 $3

processFilter="server.hostname=`hostname`";

echo 
echo ==== Killing All listed process, and their children
echo
echo processList is $processList
echo
parentPid=`ps -ef | grep $processFilter | grep -v -e grep -e $0 | awk '{ printf "%s ", $2 }'`
echo parent Pids are $parentPid

trim () {
    read -rd '' $1 <<<"${!1}"
}

trim parentPid

svcPids=`ps -ef | grep -e ${parentPid// / -e }  | grep -v -e grep -e $0 | awk '{ print $2 }'`
echo child Pids are $svcPid


echo ====================================================================
echo Running $0
echo  pid is $svcPids
echo ====================================================================
/bin/kill -9 $svcPids

if [ $isClean == "1" ]; then
	echo ====================================================================
	echo clean was passed in, doing a rm -rf $PROCESSING 
	echo ====================================================================
	if [ -e "$PROCESSING" ]; then
		rm -rf $PROCESSING ;
	fi ;
	mkdir $PROCESSING
fi ;