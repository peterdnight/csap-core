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

if [ "$serviceName" == "CsAgent" ] ; then
	echo == CsAgent stop not supported. Use a kill which will autorestart the service
	exit ;
fi

export JAVA_OPTS=""
cd $runDir

if [ "$serverRuntime" == "SpringBoot" ] ; then
	showIfDebug  "stopInstance.sh\t:" == Spring Boot...
	source SpringBootWrapper.sh
	stopBoot
	exit;
fi ;

if [ "$serverRuntime" == "wrapper" ] ; then
	echo ==
	echo == Found a wrapper loading custom $runDir/scripts/consoleCommands.sh
	echo ==
	
	source checkForWrapperExtract.sh
	
	source $runDir/scripts/consoleCommands.sh
	echo == invoking stopWrapper
	stopWrapper
	echo Flag to exit read loop in AdminController.java XXXYYYZZZ_AdminController ;
	echo == exiting wrapper
	exit ;

fi ;

if [ "$csapTomcat" == "true" ] ; then 
	if [ -e $PROCESSING/tomcat_0/scripts/TomcatWrapper.sh ]  ; then
		source $PROCESSING/tomcat_0/scripts/TomcatWrapper.sh
		tomcatStop
		printIt tomcat stop has completed. Use csap application portal and logs to verify service is active.
		exit ;
	else
		printIt Did not find $STAGING/bin/TomcatWrapper.sh. Update your application to include it.
	fi;
fi;

printIt Unhandled csapServer: $csapServer . Contact your Application manager for support
