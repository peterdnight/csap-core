#!/bin/bash
#
#
#

function printIt() { echo; echo; echo =========; echo == $* ; echo =========; }


scriptDir=`dirname $0`
printIt "Running $0 $*"
printIt "script dir is $scriptDir  param count: $#"


function killPattern() {

	killFilter=$1;
	printIt "checking process list for running $killFilter"
	ps -ef | grep $killFilter | grep -v grep| grep -v scriptRunAsRoot | grep -v killStaging | grep -v DcsapProcessId

	# first kill any scripts issued from UI, running under ssadmin
	processList=`ps -ef | grep $killFilter | grep -v grep| grep -v scriptRunAsRoot | grep -v killStaging| grep -v DcsapProcessId`

	if [ "$processList" != "" ] ; then

		printIt "Killing ssadmin $STAGING/scripts"

		#svcPid=`ps -ef | grep /staging/bin | grep -v grep | grep -v killStaging | awk '{ print $2 }'`
		parentPid=`ps -ef | grep $killFilter | grep -v grep | grep -v scriptRunAsRoot | grep -v killStaging | grep -v DcsapProcessId | awk '{ print $2 }'`

		svcPid="none"
		#printIt "parentPid is $parentPid"
		if [ "$parentPid" != "" ] ; then 
			# -w to force words, and -e to specify matching pids to ensure pid subsets do not match
			# eg. pid 612 and 13612 would both match unless this is added
			# since only spaces are replaced, the first entry is explicity added
			pidFilterWithRegExpFilter=" -we "`echo $parentPid | sed 's/ / -we /g'`
			svcPid=`ps -ef | grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 | awk '{ print $2 }'`
			printIt "Processes to be killed with children added using $pidFilterWithRegExpFilter"
			ps -ef | grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 
		fi ;

		printIt "children pids of $parentPid will be killed: $svcPid"

		/bin/kill -9 $svcPid
		if [  "$CSAP_NO_ROOT" == "" ] ; then 
			printIt Triggering a root kill in case process was run as root
			sleep 2
			/usr/bin/sudo /bin/kill -9  $svcPid
		fi ;
	else 
		printIt "No processes found"
	fi ;
	sleep 2
}

killPattern "/staging/scripts"
killPattern "csapDeployOp"
#killPattern "ajpSecret"

