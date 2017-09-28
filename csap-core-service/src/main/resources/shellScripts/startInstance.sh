#!/bin/bash
#
# Script uses convention of containing folder name to generate default for war name and env and JMX_PORT
# You can override if you want, but sticking to convention is easier
#

echo == sourcing $HOME/.bashrc 
source $HOME/.bashrc

scriptDir=`dirname $0`




# loads params
source dirVars.sh

showIfDebug Running $0 : dir is $scriptDir
showIfDebug param count: $#
showIfDebug params: $@

 if [ ! -e "$STAGING/conf" ] ; then
	printIt Did not find Application definition. Use restartAdmin.sh clone on existing clusters or add -clone to install
	printIt Sleeping for 5 seconds in case of file lag
	sleep 5
	if [ ! -e "$STAGING/conf" ] ; then
		printIt WARNING Sleeping for 10 seconds in case of file lag
		sleep 10
	fi
 	#mkdir -p $STAGING/conf;
 	
 	#\cp -prf $STAGING/bin/defaultConf/* $STAGING/conf
 	#echo creating zip for convenience
 	#pushd $STAGING  ;   zip -r conf.zip conf ; popd
 
 fi ;

 if [ ! -e "$HOME/csapOverRide.properties" ] ; then
 	echo == Creating default $HOME/csapOverRide.properties
 	\cp -p $STAGING/bin/csapOverRide.properties $HOME/csapOverRide.properties
 fi ;
 
isClean=`expr match "$svcClean" 'clean' != 0`
isSuperClean=`expr match "$svcClean" 'super' != 0`
isSpawn=`expr match "$svcSpawn" 'yes' != 0`
isSkip=`expr match "$svcSkipDeployment" 'yes' != 0`
isHotDeploy=`expr match "$hotDeploy" 'yes' != 0`

if [ "$serviceName" == "" ]; then
	echo usage $0 -d starts cs agent with default settings - everything else should use ui
	# echo usage $0 -i \<service_port\> -u \<scmUser\> optional: -b \<scmBranch\> -w \<warDir\> 
	echo exiting
	exit ;
fi ;



if [ "$serverRuntime" == "" ] || [ "$serviceName" == "" ] ; then
	echo ==
	echo == Error: unable to find required parameters, use -d for service agent
	echo ==
	exit ;
fi ;

# push into background on 8011 because we are killing the admin jvm
overRideClean="" ;
if [ "$serviceName" == "CsAgent" ] ; then

	echo == svcClean is $svcClean
	if [ "$svcClean" != "" ] ; then
		echo svcClean set so overriding so that super clean does not occur
		overRideClean="-cleanType clean" ;
	fi ;
	
	#Hook for maven releases MUST UPDATE M2 in admin.bashrc
	if [ ! -e $STAGING/apache-maven-3.3.3 ] ; then
		echo == Maven upgrade in progress $STAGING/apache-maven-3.3.3
		cd $STAGING
		\rm -rf apache-maven*
		wget -nv  http://$toolsServer/csap/apache-maven-3.3.3-bin.zip
		unzip -q apache*.zip
		\rm  apache*.zip
		cd -
	fi;
	
	# Hook for migrating existing hosts to latest platform requirements. Only done when root install done
	if [ -e $STAGING/bin/rootMigrate.sh ] && [ "$CSAP_NO_ROOT" != "yes" ]; then
		echo;echo;echo =========== Platform Migration Check =============== ;echo;
		rm -rf $STAGING/bin/rootDeploy.sh ;
		cat $STAGING/bin/rootMigrate.sh > $STAGING/bin/rootDeploy.sh ;
		chmod 755 $STAGING/bin/rootDeploy.sh ;
		
		echo == One time check: populating sudo commands 
		sudo $STAGING/bin/rootDeploy.sh 
		echo == One time check: Blowing away any svn folders in /home/ssadmin/staging/conf
		find /home/ssadmin/staging/conf -name "*.svn" -exec rm -rf '{}' \;
		
		rm -rf $STAGING/bin/rootMigrate.sh ;
	fi ;
	
	# isSpawn prevents circular calls with killInstance
	if [ $isSpawn == "0" ] ; then
	  # The "-q" option to grep suppresses output.
	  echo "== $serviceName matchs CsAgent so running in background since admin process kills itself "
	  echo == look for content in $STAGING/nohup.txt file. Wait a few minutes and reload the browser.
	  echo == 
	  
	  $scriptDir/killInstance.sh $args $overRideClean 
	  exit ;
	fi;
	#sleep 5 ;
else
	if [ $isHotDeploy != "1" ] ; then
		echo Running Kill in case process has not been stopped
		$scriptDir/killInstance.sh $args
	fi ; 
fi ;

if  [ "$csapHttpPort" != 0 ] ; then 
	printIt "Running port check to verify service ports is available: $csapHttpPort"
	sleep 1
	ptest=$(netstat -atunp | grep $csapHttpPort | grep LISTEN ) 
	portCheck=`netstat -atun | grep $csapHttpPort | grep LISTEN | wc -l` ;
	ptest2=`netstat -atun | grep $csapHttpPort ` | grep LISTEN ;
	if [  "$portCheck" != "0" ] && [ $isHotDeploy != "1"  ] ; then
		printIt "portCheck: $portCheck , $ptest2"
		echo == processes: $ptest
		echo
		echo =========== CSSP Abort ===========================
		echo __ERROR: Found a conflicting port ID 
		echo == Could be due to standard time_wait, so try again. If it happens repeatedly
		echo == you may need to run netstat as root to see another process name
		echo == Contact administrator to update port in $CLUSTERDEF
		echo ==
		echo ==
		echo == sleeping for 10 seconds and continuing , should work if it is a simple race
		sleep 10
		echo running netstat again:
		ptest2=`netstat -atun | grep $csapHttpPort | grep LISTEN ` ;
		echo == $ptest2
	fi
fi


#echo ==
#echo == $STAGING/warDist Contents
#echo ==
#ls -lh $STAGING/warDist

if [ ! -e "$STAGING/warDist/$serviceName.war" ] \
	&& [   ! -e "$STAGING/warDist/$serviceName.zip" ] \
	&& [   ! -e "$STAGING/warDist/$serviceName.jar" ] ; then
	echo ==
	echo == Did not find $STAGING/warDist/$serviceName., build must be done
	echo ==
	
	exit ;
fi ;



# hmm - use csapJmxPort?
#export JMX_PORT=`expr $csapHttpPort + 5`
export JAVA_OPTS="$csapParams -Djava.rmi.server.hostname=`hostname`"

if [ "$csapJmxPort" != "" ] ; then
	export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$csapJmxPort -Dcom.sun.management.jmxremote.rmi.port=$csapJmxPort" ;
fi ;
#export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$JMX_PORT"
export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=$isJmxAuth"
export JAVA_OPTS="$JAVA_OPTS  -Dcom.sun.management.jmxremote.ssl=false"
# JMX Firewall Users
export JAVA_OPTS="$JAVA_OPTS  -Dcom.sun.management.jmxremote.password.file=$jmxPassFile"
export JAVA_OPTS="$JAVA_OPTS  -Dcom.sun.management.jmxremote.access.file=$jmxAccessFile"

export csapProcessId="csapProcessId=$svcInstance"

export JAVA_OPTS="$JAVA_OPTS  -D$csapProcessId -DcsapEnvironmentVariables=arePresent"

if [[ "$csapParams" = *csapLegacy* ]] ; then 
	echo == WARNING - adding legacy parameters. It is strongly recommended to switch to csap env variables
	export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$csapJmxPort" ;

	export servicePort="$csapHttpPort"
	export serviceName="$csapName"
	export serviceEnv="$csapServiceLife"
	export loadBalanceUrl="$csapLbUrl"

	export JAVA_OPTS="$JAVA_OPTS  -DredirectOutput=true"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.name=$serviceName"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.host=`hostname`"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.httpport=$csapHttpPort"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.jmxport=$JMX_PORT"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.cluster=$serviceEnv"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.loadBalanceUrl=$csapLbUrl"
	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.life=$lifecycle"
	export JAVA_OPTS="$JAVA_OPTS  -Dcisco.life=$lifecycle"
fi ;



#if [ -e $STAGING/warDist/$serviceName.war.txt ] ; then
#	ver=`grep -o '<version>.*<' $STAGING/warDist/$serviceName.war.txt  | cut -d ">" -f 2 | cut -d "<" -f 1`
#	export JAVA_OPTS="$JAVA_OPTS  -Dcom.cisco.ca.csp.cso.platform.service.version=$ver"
#fi;


# hack for some jars with hardcoded ccix vars
export JAVA_OPTS="$JAVA_OPTS  -DSTAGING=$STAGING" # Added for keystore location in server.xml
#
# gc logging - is triggered when needed via the UI . no need for it in here
# export JAVA_OPTS="$JAVA_OPTS  -Xloggc:$CATALINA_BASE/logs/garbageCollect.log -XX:+PrintHeapAtGC -XX:+PrintGCDetails"
# export JAVA_OPTS="$JAVA_OPTS  -XX:+PrintGCTimeStamps -XX:-HeapDumpOnOutOfMemoryError"
 
printIt JAVA_OPTS: $JAVA_OPTS


#customPeerId=$csapName"_peer_"
# echo customPeerId $customPeerId
# env | grep "peer"
#peers=`env | grep $customPeerId`

function updateServiceOsPriority {
	servicePattern="$1"
	if [ -e $STAGING/bin/rootRenice.sh ]  \
			&& [ "$CSAP_NO_ROOT" != "yes" ] \
			&& [ "$osProcessPriority" != 0 ] ; then
		
		printIt Service has set a custom os priority
		sleep 5 ;
		
		sudo $STAGING/bin/rootRenice.sh $servicePattern $osProcessPriority
	fi
}

	
startOverrideFile="$serviceConfig/serviceStartOverride.sh"
if [ -e "$startOverrideFile" ] ; then
	printIt  Warning: $startOverrideFile  found  in capability/propertyOverride folder 
	echo  This can corrupt the FS - project team must carefully test
	echo
	native2ascii $startOverrideFile $STAGING/temp/serviceStartOverride.sh
	chmod 755 $STAGING/temp/serviceStartOverride.sh
	$STAGING/temp/serviceStartOverride.sh $serviceName $serviceEnv $lifecycle $platformVersion
else
	echo Info only: $startOverrideFile not found in capability/propertyOverride folder  
fi

if [ ! -e "$csapWorkingDir" ]; then
	printIt Creating  $csapWorkingDir
	mkdir $csapWorkingDir
fi ;

if [ "$serverRuntime" == "SpringBoot" ] ; then
	
	cd $runDir ;
	source SpringBootWrapper.sh
	startBoot

	servicePattern='.*java.*csapProcessId='$serviceName'.*'
	updateServiceOsPriority $servicePattern

	exit ;
	
fi ;

if [ "$serverRuntime" == "wrapper" ] ; then
	echo ==
	echo == Found a wrapper...extracting $STAGING/warDist/$serviceName.zip to $runDir
	echo ==
	
	
	cd $runDir ;
	
	source checkForWrapperExtract.sh
	startWrapper
	
	echo == Wrapper started
	echo 
	if [ -e $STAGING/bin/rootRenice.sh ]  ; then
		servicePattern='.*processing.*'$serviceName'.*'
		
		if [ "$osProcessPriority" != "0" ] && [ "$CSAP_NO_ROOT" != "yes" ]; then
			sudo $STAGING/bin/rootRenice.sh $servicePattern $osProcessPriority
		else
			echo == Skipping priority since it is 0
	 fi
	fi
	
	echo Flag to exit read loop in AdminController.java XXXYYYZZZ_AdminController ;
	exit ;
fi ;

if [ "$csapTomcat" == "true" ] ; then 
	if [ -e $PROCESSING/tomcat_0/scripts/TomcatWrapper.sh ]  ; then
		source $PROCESSING/tomcat_0/scripts/TomcatWrapper.sh
		tomcatStart
		printIt tomcat start has completed. Use csap application portal and logs to verify service is active.
		exit ;
	else
		printIt Did not find $STAGING/bin/TomcatWrapper.sh. Update your application to include it.
	fi;
fi;

printIt Unhandled csapServer: $csapServer . Contact your Application manager for support


