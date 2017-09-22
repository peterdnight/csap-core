#!/bin/bash

scriptDir=`dirname $0`
scriptName=`basename $0`

printIt() {
	echo;echo;
	echo ============== Spring Boot `date` ================ 
	echo = $*
	echo = 
}

function addOracleOci() {
	
	jdbcSource="" ;
	
	# override if we find other versions
	if [ -e $ORACLE_HOME/ojdbc7.jar ] ; then 
		jdbcJar=ojdbc7.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc6.jar ] ; then 
		jdbcJar=ojdbc6.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc6_g.jar ] ; then 
		jdbcJar=ojdbc6_g.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/ojdbc6.jar ] ; then 
		jdbcJar=ojdbc6.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc14_g.jar ] ; then 
		jdbcJar=ojdbc14_g.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/jdbc/lib/ojdbc14.jar ] ; then
		jdbcJar=ojdbc14_g.jar ;
		jdbcSource="$ORACLE_HOME/jdbc/lib/$jdbcJar" ;
	elif [ -e $ORACLE_HOME/ojdbc14.jar ] ; then
		jdbcJar=ojdbc14.jar ;
		jdbcSource="$ORACLE_HOME/$jdbcJar" ;
	else
		printIt WARNING:  Unable to locate oracle jdbc driver in  $ORACLE_HOME, validate /home/ssadmin/.cafEnvOverride 
		echo ==
		echo == Start will continue, but note if ORACLE is needed services may fail to start
	fi ; 
	
	printIt Oracle Configuration $jdbcSource
	
}


startBoot() {
	
	printIt Starting: $serviceName.jar in directory $runDir
	
		
	jarExtractDir="jarExtract"	
	
	# SpringBoot <= 1.3
	springBootClasses="$jarExtractDir"
	
	if  [ "$isSkip" != "1" ]  ; then
		
		\rm -rf $springBootClasses
		
		
		echo extracting jar contents to $springBootClasses 
		/usr/bin/unzip -qq -o $STAGING/warDist/$serviceName.jar -d $springBootClasses
		
		if [ -e "$springBootClasses/BOOT-INF" ]; then
			echo springboot 1.4 or later detected
			springBootClasses="$jarExtractDir/BOOT-INF/classes"
		fi ;
		
		if [ -e "$springBootClasses/$lifecycle" ]; then
			printIt Found packaged resources: $springBootClasses/$lifecycle, copying to  $springBootClasses
			\cp -fr $springBootClasses/$lifecycle/* $springBootClasses
		else
			printIt Did not find packaged resources: $springBootClasses/$lifecycle
		fi ;
		
		if [ -e "$serviceConfig/$serviceName/resources/common" ]; then
			printIt Found lifecycle Overide properties: $serviceConfig/$serviceName/resources/common, copying to  $springBootClasses
			\cp -fr $serviceConfig/$serviceName/resources/common/* $springBootClasses
		else
			printIt Did not find common override resources: $serviceConfig/$serviceName/resources/common
		fi ;
		
		if [ -e "$serviceConfig/$serviceName/resources/$lifecycle" ]; then
			printIt Found lifecycle Overide properties: $serviceConfig/$serviceName/resources/$lifecycle, copying to  $springBootClasses
			\cp -fr $serviceConfig/$serviceName/resources/$lifecycle/* $springBootClasses
		else
			printIt Did not find lifecycle override resources: $serviceConfig/$serviceName/resources/$lifecycle
		fi ;
		
		if [ -e "$serviceConfig/$serviceName/resources/$serviceEnv" ]; then
			printIt Found serviceEnv Overide properties: $serviceConfig/$serviceName/resources/$serviceEnv, copying to  $springBootClasses
			\cp -fr $serviceConfig/$serviceName/resources/$serviceEnv/* $springBootClasses
		else
			printIt Did not find serviceEnv override resources: $serviceConfig/$serviceName/resources/$serviceEnv
		fi ;
		
		csapExternal=`eval echo $csapExternalPropertyFolder`
		if [[ "$csapExternal" != "" && -e "$csapExternal/$lifecycle" ]]; then
			
			printIt Found csapExternalPropertyFolder variable, $csapExternal/$lifecycle == copying to $springBootClasses
	
			\cp -rf $csapExternal/$lifecycle/* $springBootClasses
				
		else
			
			printIt Did not find csapExternalPropertyFolder environment: $csapExternal
		fi
		
		
		ssoFile="$serviceConfig/CsAgent/resources/$lifecycle/csapSecurity.properties"
		if [ -e "$ssoFile" ] ; then
			echo
			echo Copying in SSO setup file: $ssoFile
			echo
			\cp -fr "$ssoFile" $springBootClasses
		else
			echo Warning: $ssoFile not found - best practice is to include one in capability/propertyOverride folder 
			echo  
			echo
		fi
		
		if [ -e "temp" ] ; then
			printIt Warning temp already exists, and may contain state such as tomcat persisted session data 
		else
			printIt Creating temp folder 
			mkdir temp ;
		fi

		configureLogging
	else
		printIt skipping Extract
	fi
	
	
	configureDeployVersion
	
	# add oracle OCI driver if present
	addOracleOci
	
	# spring boot inserts manifest to classes and lib folders
	export CLASSPATH="$jdbcSource:$jarExtractDir"
	printIt classpath is $CLASSPATH
	
	# default process filter for boot is java.*serviceName.*--server.port. Update InstanceConfig if you modify
	#java  $JAVA_OPTS -Dloader.home="config" -jar $serviceName.jar --spring.profiles.active=$lifecycle  --server.port=$servicePort >> logs/consoleLogs.txt 2>&1 &
	
	# running exploded provides more flexibility for externalized configuration and property filesconfig:config/lib/*

	springProfiles="--spring.profiles.active=$lifecycle"
	if [[ $JAVA_OPTS == *spring.profiles.active* ]] ; then 
		printIt Using  spring profile specified in java parameters
		springProfiles="";
		JAVA_OPTS="${JAVA_OPTS/CSAP_LIFE/$lifecycle}"
		printIt JAVA_OPTS: $JAVA_OPTS
	else
		printIt Using default spring profile $springProfiles
	fi
	
	if [ "$csapDockerTarget" == "true" ]  ; then
		echo == Service configured for docker, start will be triggered via docker container apis
	else
		set -x
		$JAVA_HOME/bin/java  $JAVA_OPTS -Djava.io.tmpdir="temp" org.springframework.boot.loader.JarLauncher $springProfiles  --server.port=$csapHttpPort >> $csapLogDir/consoleLogs.txt 2>&1 &
		set +x ; sync
	fi ;

	printIt Service has been started - review logs and application metrics to assess health.

}

configureLogging() {
	printIt "== creating log folder: $csapLogDir"
	
	if [ -d $csapLogDir ] ; then
		
		printIt == log folder already exists
		return;
	fi ;
	
	if [ -e $runDir.logs ] ; then 
		echo == moving existing Log folder from: $runDir.logs 
		mv  $runDir.logs $csapLogDir
	else
		mkdir -p $csapLogDir
	fi;

	if [ -e "$springBootClasses/logRotate.config" ] ; then
		echo ==
		echo == Detected log rotation policy file 
		echo == $springBootClasses/logRotate.config
		echo == note that incorrect syntax in  config files will prevent rotations from occuring. 
		echo == Logs are examined hourly: ensure rotations are occuring or your service will be shutdown
		echo ==
		cp -vf $springBootClasses/logRotate.config $csapLogDir

		sed -i "s=_LOG_DIR_=$csapLogDir=g" $csapLogDir/logRotate.config
		echo ; echo
	else 

		echo == creating $csapLogDir/logRotate.config

		echo "#created by SpringBootWrapper" > $csapLogDir/logRotate.config
		echo "$csapLogDir/consoleLogs.txt {" >> $csapLogDir/logRotate.config
		echo "copytruncate" >> $csapLogDir/logRotate.config
		echo "weekly" >> $csapLogDir/logRotate.config
		echo "rotate 3" >> $csapLogDir/logRotate.config
		echo "compress" >> $csapLogDir/logRotate.config
		echo "missingok" >> $csapLogDir/logRotate.config
		echo "size 10M" >> $csapLogDir/logRotate.config
		echo "}" >> $csapLogDir/logRotate.config
		echo "" >> $csapLogDir/logRotate.config
	fi; 

		
}

configureDeployVersion() {
	bootVersion="none"
	if [ -e $STAGING/warDist/$serviceName.jar.txt ] ; then
		bootVersion=`grep -o '<version>.*<' $STAGING/warDist/$serviceName.jar.txt  | cut -d ">" -f 2 | cut -d "<" -f 1`
	fi;

	printIt == creating $runDir/version : $bootVersion
	\rm -rf $runDir/version
	mkdir -p $runDir/version/$bootVersion
	touch $runDir/version/$bootVersion/created_by_SpringBootWrapper

		
}

stopBoot() {


	csapProcessFilter="csapProcessId=$svcInstance"
	svcPid=`ps -u $USER -f| grep $csapProcessFilter  | grep -v -e grep -e $0 | awk '{ print $2 }'`
	
	printIt stop: $serviceName pid: $svcPid
		
	if [ "$svcPid" != "" ] ; then
		echo == SpringBoot uses SIGTERM to shutdown gracefully. Use kill if it does not shutdown.
		kill -SIGTERM $svcPid
	else
		echo == pid not found 
	fi ;
	
}


deployBoot() {
	
	printIt deploy: $*
	
}


killBoot() {
	
	if [ "$csapName" == "admin" ] ; then 
		printIt Shutting csap admin down gracefully to allow for alert backup and sleeping 5 seconds;
		stopBoot ;
		sleep 5 ;
	fi ;
	
	printIt killing: $serviceName pid: $svcPid
	
}