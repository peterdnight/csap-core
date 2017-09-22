#!/bin/bash
#
#
#
# set -o verbose #echo on

scriptDir=`dirname $0`
echo ====================================================================
echo == Running $0 : 
echo == script dir is $scriptDir
echo == param count: $#
echo == $1 $2 $3
echo == using `pwd` to extract settings: contextRoot_env_HttpPort
echo ====================================================================


source dirVars.sh

function checkInstalled() { 
	packageName="$1"
	rpm -q $packageName
	if [ $? != 0 ] ; then 
		printIt error: $packageName not found, install using yum -y install; 
		exit; 
	fi   
}


printIt "Validating Jmeter OS Dependencies"
checkInstalled libXrender
checkInstalled libXtst
checkInstalled libXi

# do not kill either this process or the grep command
checkForRunningJmeters=`ps -ef | grep csapVerify=$serviceName | grep -v -e grep -e $0  | wc -l`


#printIt "Checking if jmeter is already running: $checkForRunningJmeters"


if [ "$checkForRunningJmeters" != "0" ] ; then
	parentPid=`ps -ef | grep csapVerify=$serviceName  | grep -v -e grep -e $0 | awk '{ print $2 }'`
	
	# In case process has spawned any children, they will be killed as well
	searchPidsWithEadded=`echo $parentPid | sed 's/ / -e /g'`
	svcPid=`ps -ef | grep -e $searchPidsWithEadded  | grep -v -e grep -e $0 | awk '{ print $2 }'`

	
	printIt "found an already running instance on pid: $svcPid , killing it"
	kill -9 $svcPid
	
	printIt "Exiting maven verify. Run again to start a new test."

	
	exit;
fi

printIt "Running maven verify to trigger Jmeter or other automated tests"

#nohup  $csapWorkingDir/XMP_IPP/bin/xmpstart.ksh -nodecap > nohup.txt  &

export webDir=`echo $csapWorkingDir/jmeter`
export csapWorkingDir=`echo $csapWorkingDir`

printIt  "webDir set to $webDir csapWorkingDir set to $csapWorkingDir"

if [ -e "$webDir" ] ; then 
	printIt Deleting webDir $webDir
	\rm -rf $webDir
fi ;


contextDir=$csapWorkingDir/webapps/$serviceContext*
verifyDir=`echo $csapWorkingDir/webapps/$serviceContext*/WEB-INF/classes/jmeter`
if [ "$csapServer" == "SpringBoot" ] ; then
	
	contextDir=$csapWorkingDir/jarExtract/BOOT-INF/classes/static
	verifyDir=`echo $csapWorkingDir/jarExtract/BOOT-INF/classes/jmeter`
fi ;

printIt "contextDir: $contextDir  , verifyDir: $verifyDir"

if [ ! -L $webDir ] ; then
	printIt "Fix for maven jmeter plugin path bug: creating link: $contextDir $webDir" 
	ln -s  $contextDir $webDir 
fi ;


logOutput="$csapWorkingDir/logs/mavenVerify.txt"

printIt "running: $verifyDir ,  output: $logOutput"

if [ -f "$logOutput" ]  ; then
	printIt Found $logOutput: removing previous file
	\rm -f $logOutput
	
fi

export MAVEN_OPTS="-Djava.awt.headless=true -Xms2048m -Xmx2048m"
printIt "MAVEN_OPTS: $MAVEN_OPTS"


if [ -f "$verifyDir/pom.xml" ] ; then

	printIt "setting directory:  $verifyDir"
	cd $verifyDir 
	
	if [ -e "$serviceConfig/settings.xml" ] ; then
		printIt "Running in background:  nohup mvn verify using $serviceConfig/settings.xml"
		nohup mvn -B -s $serviceConfig/settings.xml -DcsapVerify=$serviceName verify &> $logOutput &
	else
		printIt "Warning: $serviceConfig/settings.xml not found - best practice is to include one in capability/propertyOverride folder"
		printIt "Running in background:  nohup mvn $STAGING/bin/settings.xml -DcsapVerify=$serviceName verify using default settings.xml"
		nohup mvn -B -s $STAGING/bin/settings.xml -DcsapVerify=$serviceName verify  &> $logOutput &
	fi ;
else 
	printIt "Warning: could not find folder: \"$verifyDir/pom.xml\" "
	printIt mvn verify not run
	exit ;
fi

printIt "Load test is running in background, use console to view output file: $logOutput"
printLine "Notes:" 
printLine "1. when run is completed, you can view the jmeter report"
printLine "2. If you did not update the _jmeterVariables.csv file with correct params (user,pass,...), then you will see lots of errors"
printLine "3. You can modify run duration in jmeter/pom.xml"
printLine "4. Clicking on jmeter again will kill your jmeter instance"



