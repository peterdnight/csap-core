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
echo Kill invoked on $HOSTNAME  using $USER            at `date "+%x %H:%M:%S %Nms"`
echo ====================================================================



#echo == Syntax:  optional wipe out runtime dir: -clean: $isClean
#echo == $1 $2 $3

# svcInstance=$2 ;
source dirVars.sh

showIfDebug Running $0 : dir is $scriptDir
showIfDebug param count: $#
showIfDebug params: $@

isClean=`expr match "$svcClean" 'clean' != 0`
isSuperClean=`expr match "$svcClean" 'super' != 0`
isSpawn=`expr match "$svcSpawn" 'yes' != 0`

# loadServices -find $svcInstance
if [ "$serviceName" == "CsAgent" ] && [ $isSpawn == "0" ] ; then
	# The "-q" option to grep suppresses output.
	printIt "$serviceName matchs CsAgent so running in background since admin process kills itself "
	printIt == look for content in $PROCESSING/CsAgentKillNohup.txt file. Wait a few minutes and reload the browser.


	  
	  # First spawn a background process to do CsAgent
	 nohup $scriptDir/killInstance.sh $args -spawn >$PROCESSING/CsAgentKillNohup.txt &
	  
	  # Now output a flag to have CsAgent take this instance down.
	printIt "CsAgent will wait 3 seconds to allow UI to restart."
	 echo Flag to exit admin loop in CsAgent ServiceController.java XXXYYYZZZ_AdminController ;
	  # We re-run the script in nohup mode because CsAgent is gonna kill the current one
	  exit ;
fi ;


if [ "$serviceName" == "CsAgent" ] ; then
	printIt "Sleeping to give CsAgent a chance to updated logs"
	sleep 3 
	printIt "hook for CsAgent - doing a killall top as csagent launches in JVM"
	pkill -9 -f top\ -b
fi ;

# ef will pull in other processes
# processList=`ps -ef | grep service.name=$serviceName | grep service.httpport=$csapHttpPort`
# 
# processList=`ps -u $USER -f| grep service.name=$serviceName | grep service.httpport=$csapHttpPort`


showIfDebug searching ps output for csapProcessId=$svcInstance

csapAllProcessFilter="csapProcessId"
csapProcessFilter="csapProcessId=$svcInstance"

processList=`ps -u $USER -f | grep $csapProcessFilter | grep -v grep`

if [ "$processList" != "" ] ; then
	# prefer use of csapAllProcessFilter added to java processes
	parentPid=`ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 | awk '{ print $2 }'`
else
	#echo == using legacy matcher
	#csapAllProcessFilter="service.name"
	#csapProcessFilter="service.name=$serviceName"
	printIt "Did not find processes using filter '$csapProcessFilter'.  Using csapPids '$csapPids'"
	parentPid="$csapPids"
fi;


showIfDebug  "killInstance.sh\t:"  processList matching  $serviceName is $processList



showIfDebug  "killInstance.sh :" parent Pids are $parentPid

svcPid=""
# some processes, including csagent, spawn some os processes. They need to be killed as well.
if [[ "$parentPid" != ""  && "$parentPid" != "noMatches" ]] ; then 
	# -w to force words, and -e to specify matching pids to ensure pid subsets do not match
	# eg. pid 612 and 13612 would both match unless this is added
	# since only spaces are replaced, the first entry is explicity added
	pidFilterWithRegExpFilter=" -we "`echo $parentPid | sed 's/ / -we /g'`
	svcPid=`ps  -u $USER -o pid,ppid | grep $pidFilterWithRegExpFilter  | awk '{ print $1 }'`
		
	printIt "Processes to be killed with children added using $pidFilterWithRegExpFilter, svcPid is $svcPid"
	ps -u $USER -f | grep $pidFilterWithRegExpFilter | grep -v grep
	echo;echo;
fi ;

showIfDebug  "killInstance.sh\t:" child Pids are $svcPid

# 

if [ "$csapServer" == "wrapper" ] ; then
	showIfDebug  "killInstance.sh\t:  == Found a wrapper..."

	svcPid="$csapPids"

	#
	# source checkForWrapperExtract.sh
	#svcPid=`ps -u $USER -f| grep  "$serviceName" | grep -v -e grep -e $0 -e "$STAGING"  | awk '{ print $2 }'`
	# pids will use csap Assigned

	skipApiExtract="true" ;
	source checkForWrapperExtract.sh
	skipApiExtract="" ;
	
	if [ "$apiFound" == "true" ] ; then 
		printIt "Invoking csapApi kill"
		killWrapper
	else 
		printIt "csapApi not found - skipping kill"
	fi;
fi ;
	

if [ $isSuperClean == "1"  ] ; then
	echo == superclean specified, killing everything
	#svcPid=`ps -u $USER -f| grep service.name | grep -v -e grep -e $0 | awk '{ print $2 }'`
	parentPid=`ps -u $USER -f| grep -e $csapAllProcessFilter -e httpd | grep -v -e grep -e $0 | awk '{ print $2 }'`
	echo  "killInstance.sh\t:" parent Pids are $parentPid
	
	if [ "$parentPid" != "" ] ; then 
		
		pidFilterWithRegExpFilter=" -we "`echo $parentPid | sed 's/ / -we /g'`
		svcPid=`ps -u $USER -f| grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0 | awk '{ print $2 }'`

		printIt "Processes to be killed with children added using $pidFilterWithRegExpFilter, svcPid is $svcPid"
		ps -u $USER -f| grep $pidFilterWithRegExpFilter  | grep -v -e grep -e $0
	fi ;

fi ;


if [ "$serverRuntime" == "SpringBoot" ] ; then
	showIfDebug  "killInstance.sh\t:" == Spring Boot...

	source SpringBootWrapper.sh
	killBoot
fi ;

if [[ "$svcPid" != ""  && "$svcPid" != "noMatches" ]] ; then

	printIt  "sending kill -9 to $csapName using pids: $svcPid"
	
	#echo `date`  $svcInstance pids $svcPid >> $PROCESSING/_killList.txt
	#echo $processList >> $PROCESSING/_killList.txt
	#echo ======= >> $PROCESSING/_killList.txt
	/bin/kill -9 $svcPid 2>&1
	
	afterKill=`ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 `
	
	printIt "`date` After killing, the following was found '$afterKill'"

	if [ "$afterKill" != "" ] ; then
		printIt "Found process still alive, sleeping 5s and trying again"
		sleep 5;
		/bin/kill -9 $svcPid  2>&1
		afterKill=`ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 `
		printIt "After killing again, the following was found '$afterKill'"
						
	fi
else 
	printIt "Skipping kill since no processes found for $csapName"
fi ;

showIfDebug  "killInstance.sh\t:" echo processes found post kill
showIfDebug  "killInstance.sh\t:" `ps -u $USER -f| grep $serviceName | grep -v -e grep -e killInstance`


if [ $isClean == "1" ] ||  [ $isSuperClean == "1"  ] ; then
	
	if  [ $isKeepLogs == "yes"  ] ; then
		printIt "preserving logs in $runDir.logs"
		\rm -rf $runDir.logs
		mv $runDir/logs $runDir.logs
	fi;
	
	runDir="$PROCESSING/$svcInstance" ;
	if [ $isSuperClean == "1"  ] ; then
		runDir="$PROCESSING" ;
	fi ;
	printIt "clean was passed in, doing a rm -rf $runDir" 
	if [ -e "$runDir" ]; then
		rm -rf $runDir ;
	else
		printIt "WARNING: dir does not exist: $runDir"
	fi ;
fi ;
	
if [ "$serviceName" == "CsAgent" ] ; then
	  echo == Special hook to autostart CsAgent
	  if [ ! -d $PROCESSING ] ; then  mkdir $PROCESSING ; fi ;
			mv  $PROCESSING/CsAgentStartNohup.txt $PROCESSING/CsAgentStartNohup.old.txt 
			nohup $scriptDir/startInstance.sh $args > $PROCESSING/CsAgentStartNohup.txt 2>&1 &
			printIt  "to view startup info, run tail -f $PROCESSING/CsAgentStartNohup.txt"
	
fi;


