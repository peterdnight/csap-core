#!/bin/bash
function installCsapJavaPackage() {
	
	
	#printIt Placing  JDK confg package in $csapWorkingDir, source: $csapDefaultJdk 
	#\rm -rf JavaDevKitPackage-8u*.zip
	#wgetWrapper $csapDefaultJdk

	unzip -qo $STAGING/warDist/jdk.zip
	
	printIt "Setting java variables"
	source $csapWorkingDir/scripts/consoleCommands.sh
	
	#cd $targetFs/csap
	printIt current dir: `pwd`
	cd $csapWorkingDir
	
	startWrapper

}

# Placed at top for ez updating of package
function javaInstall() {
	prompt "Installing CSAP JDK package"
	mkdir -p $STAGING/temp
	cd $STAGING/temp
	
	csapName="jdk";
	csapWorkingDir=`pwd`;

	installCsapJavaPackage

	
	#printIt Exiting ; exit
}
#export JAVA_HOME=/opt/java/jdk1.8.0_101


#
# Note: this is a common installer used for both gen 1 and gen 2 installs; Changes need to be tested in both envs
#

if [ ! -f  $HOME/.cafEnv ] ; then 

	echo == creating Csap  $HOME/.cafEnv, assuming this is a gen2 vm that is being reImaged

	echo  export STAGING=$HOME/staging >> $HOME/.cafEnv
	echo  export PROCESSING=$HOME/processing >> $HOME/.cafEnv
	
	if [ "$isMemoryAuth"  == "1" ] ; then
		echo == Security Setup is using Memory only settings, update CsAgent users.txt file
		echo  export csapAuth="memory" >> $HOME/.cafEnv
	else
		echo == using default settings
	fi;
fi

# load variables for access to staging and processing contents
source $HOME/.cafEnv

scriptDir=`dirname $0`
scriptName=`basename $0`
source $scriptDir/installFunctions.sh

csapPackageUrl="http://$toolsServer/csap/csap6.0.0$allInOnePackage.zip"

prompt == Starting $0 : params are $*

if [ -d $STAGING ] ; then
	printIt "ERROR: Found existing application: $STAGING. Confirm disks have been wiped or previous version removed, and all previous processes killed, and retry" ;
	exit 66;
fi;


# Note that this includes staging in zip
#\rm -rf csap*.zip

numberPackagesLocal=`ls -l csap*.zip | wc -l`
localDir="/media/sf_workspace/packages"
if [ -e $localDir ] ; then 
	printIt using local copies from $localDir
	cp $localDir/* .
elif (( $numberPackagesLocal == 1 )) ; then
	printIt "Found a local package, using csap*zip";
else
	printIt "Getting csap install from $csapPackageUrl"
	wgetWrapper $csapPackageUrl
fi;
	
unzip -q csap*.zip
javaInstall



if [ $mavenSettingsUrl != "default" ] ; then
	printIt "downloading $STAGING/bin/defaultConf/propertyOverride :  $mavenSettingsUrl"
	wgetWrapper $mavenSettingsUrl
	\mv settings.xml $STAGING/bin/defaultConf/propertyOverride
else 
	printIt " mavenSettingsUrl was not specified in installer, public spring repo is the default"
fi

if [ $mavenRepoUrl != "default" ] ; then
	printIt "updating $STAGING/bin/defaultConf/Application.json with $mavenRepoUrl"
	sed -i "s=http://repo.spring.io/libs-release=$mavenRepoUrl=g" $STAGING/bin/defaultConf/Application.json
else 
	printIt " mavenRepoUrl was not specified in installer, public spring repo is the default"
fi

printIt "Updating $HOME/.bashrc using $STAGING/bin/admin.bashrc"
echo  source $STAGING/bin/admin.bashrc >> $HOME/.bashrc
source ~/.bashrc

mkdir $PROCESSING

cd $HOME
# staging/bin/buildAndInstall.sh

printIt "Setting up CSAP Application using $cloneHost"

if [ "$starterUrl" != "" ] ; then
	printIt "Getting Starter configuration: $starterUrl"
 	\rm -rf $STAGING/conf getConfigZip*
 	wget $starterUrl
 	unzip  -q -o -d $STAGING/conf getConfigZip*

 	
elif [ $cloneHost == "default" ] ; then
	printIt Creating default cluster using $STAGING/bin/defaultConf
	 	\rm -rf $STAGING/conf;
	 	\cp -r $STAGING/bin/defaultConf $STAGING/conf;
 	
else
	printIt "Getting configuration using host: http://$cloneHost:8011/CsAgent/os/getConfigZip"
 	\rm -rf $STAGING/conf
 	# scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -oBatchmode="yes" -p -r $cloneHost:staging/conf $HOME/staging
 	wget http://$cloneHost:8011/CsAgent/os/getConfigZip
 	unzip  -q -o -d $STAGING/conf getConfigZip
fi ;

printIt ssadmin install completed

