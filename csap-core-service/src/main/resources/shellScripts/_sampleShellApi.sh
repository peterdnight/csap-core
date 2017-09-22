#!/bin/bash


function printIt() { echo; echo; echo =========; echo == $* ; echo =========; }

printIt Show help
csap.sh -help
adminUrl="http://$HOSTNAME:8080/admin"
agentUrl="http://$HOSTNAME:8011/CsAgent"

# echo show api commands 
# csap.sh -api help -script

# Get a port number. Note the use of jpath to parse json output
# jpath syntax is at: http://tools.ietf.org/html/draft-ietf-appsawg-json-pointer-03
printIt Sample: get port number for CsAgent
port=`csap.sh -lab $adminUrl -api model/services/byName/CsAgent -parseOutput -jpath /0/port`
echo port is $port


printIt Sample: get log files for CsAgent
logFileName=`csap.sh -lab $adminUrl -api agent/service/log/list -params 'serviceName_port=CsAgent_8011,userid=csapeng.gen,pass=$csapPass1' -parseOutput -jpath /0`
echo  First log file is $logFileName



# Note the escape quotes are needed to handle greps with spaces.
printIt Sample: grep log file content for CsAgent
csap.sh -parseOutput -textResponse -lab $agentUrl \
	-api 'agent/service/log/filter' \
	-params 'fileName=consoleLogs.txt,filter=Start,serviceName_port=CsAgent_8011,userid=csapeng.gen,pass=$csapPass1'

printIt Sample: Iterate over hosts in dev-mongocluster-1
hosts=`csap.sh -parseOutput -lab $adminUrl -api model/hosts/dev-mongocluster-1 -script` ;
for hostName in $hosts ; do
	echo == found host: $hostName
done

#echo; echo == Sample invocation: get log file content for CsAgent
#p=`csap.sh -parseOutput -textResponse -lab http://testhost.yourcompany.com:8080/admin -api service/CsAgent_8011/logs/$logFileName  `
#echo  log file content $p


# printIt json format with optional timeout
# csap.sh -api help -timeOutSeconds 14

# Service Deploy - update password
# csap.sh -lab  http://localhost:8011/CsAgent -api serviceDeploy/ServletSample_8041 -timeOutSeconds 11 -params "mavenId=com.your.group:Servlet3Sample:1.0.2:war,userid=ssplatform.gen,pass=SSplatformxxxx,cluster=dev-AuditCluster-1"
# csap.sh -lab  https://csap-secure.yourcompany.com/admin -api serviceDeploy/ServletSample_8041 -timeOutSeconds 11 -params "mavenId=com.your.group:Servlet3Sample:1.0.2:war,userid=ssplatform.gen,pass=SSplatformxxxx,cluster=dev-AuditCluster-1"

# service start/stop - update password
#csap.sh  -timeOutSeconds 11 -lab  http://localhost:8011/CsAgent -api serviceStart/ServletSample_8041 -params "userid=ssplatform.gen,pass=SSplatformxxxx"
#csap.sh  -timeOutSeconds 11 -lab  http://localhost:8011/CsAgent -api serviceStop/ServletSample_8041 -params "userid=ssplatform.gen,pass=SSplatformxxxx"


#
#csap.sh  -timeOutSeconds 11 -lab  http://localhost:8011/CsAgent -api mavenArtifacts

# Hitting manager of entire lifecycle. These are HA calls
# csap.sh -lab https://csap-secure.yourcompany.com/admin summary

# echo == hosts
# csap.sh -api hosts -script


# echo == hosts with JSON output
# csap.sh -lab https://csap-secure.yourcompany.com/admin -api hosts/dev-AuditCluster-1 

# echo == hosts with script output
# csap.sh -lab https://csap-secure.yourcompany.com/admin -api hosts/dev-AuditCluster-1 -script



