#!/bin/bash

# Defaults
skipKernalAndPackageUpdates="0"
isSkipAgent="0"
isPrompt="1" ;
isSmall="0"
isJava7="1"
cloneHost="default"
starterUrl=""
isMemoryAuth="0"
allInOnePackage="";

# logical volume name

CSAP_VOLUME_GROUP="cstg_volume_group" ;
CSAP_VOLUME_DEVICE="/dev/$CSAP_VOLUME_GROUP" ;
CSAP_INSTALL_VOLUME="ssadminLV" ;
CSAP_EXTRA_VOLUME="extraLV" ;
CSAP_ACTIVEMQ_VOLUME="activeMqLV" ;
CSAP_ORACLE_VOLUME="oracleLV" ;

installDisk="/dev/sdb"
# fs sizes - note they are only used if selected and they are shown as offsets
# dev numbers are determined by run order below
csapFs=0;  # always need to leave  1Gb free
extraFs=0;
extraDisk=""
mqFs=0;  
targetFs=0;
skipDisk=0;
vmZone="" ; # timeZone 
oracleFs=0; moreSwap=0 # SGA configured in oracle scripts :  oracleSga=14; 

sshCertDir=""

#preserve original params to pass to child scripts
origParams="$*"

toolsServer="notSpecified"
mavenSettingsUrl="default" # nimbus.xml, customer.xml, etc
mavenRepoUrl="default" # nimbus.xml, customer.xml, etc

userPassword="password";

fsType="ext4"

#rhVersion=`cat /etc/redhat-release | awk '{ print $7 $8}'`

# default open files on gen2. Can be overridden as a install param
maxOpenFiles=16384;
maxThreads=2048;

# this is helper for printing out console logs
function printIt() {
	echo;echo;
	echo = 
	echo = $*
	echo = 
}

function processCommandline() {
		
	while [ $# -gt 0 ]
	do
	  case $1
	  in
	    -samplewithParam )
	      echo "-samplewithParam was specified,  Parameter: $2"   ;
	      samplewithParam="$2" ;
	      shift 2
	    ;;
	    
	    -sampleNoParam )
	      echo "-sampleNoParam was triggered "  
	      samplewithParam="yes";
	      shift 1
	      ;;
	    
	    -allInOnePackage )
	      echo "-allInOnePackage was triggered "  
	      allInOnePackage="-full";
	      shift 1
	      ;;
	    
	    -skipKernel )
	      echo "-skipKernel was triggered, kernel and os packages will be skipped "  
	      skipKernalAndPackageUpdates="1";
	      shift 1
	      ;;
	    
	    -zone )
	      echo "-zone was specified,  Parameter: $2"   ;
	     vmZone="$2" ;
	      shift 2
	    ;;
	    
	    -pass )
	      echo "-pass was specified"   ;
	      userPassword="$2" ;
	      shift 2
	    ;;
	    
	    -maxOpenFiles )
	      echo "-maxOpenFiles was specified,  Parameter: $2"   ;
	      maxOpenFiles="$2" ;
	      shift 2
	    ;;
	    
	    -maxThreads )
	      echo "-maxThreads was specified,  Parameter: $2"   ;
	      maxThreads="$2" ;
	      shift 2
	    ;;
	    
	    -toolsServer )
	    echo "-toolsServer was triggered,  Parameter: $2 " ;
		 toolsServer="$2" ;
	      shift 2
	      ;;
	    
	    -mavenSettingsUrl )
	    echo "-mavenSettingsUrl was triggered,  Parameter: $2 " ;
		 mavenSettingsUrl="$2" ;
	      shift 2
	      ;;
	    
	    -mavenRepoUrl )
	    echo "-mavenRepoUrl was triggered,  Parameter: $2 " ;
		 mavenRepoUrl="$2" ;
	      shift 2
	      ;;
	      
	    -installDisk )
	    echo "-installDisk was triggered,  Parameter: $2 " ;
		 installDisk="$2" ;
	      shift 2
	      ;;
	
	    -sshCertDir )
	      echo "-sshCertDir was specified,  Parameter: $2"   ;
	      sshCertDir="$2" ;
	      shift 2
	    ;;
	          
	    -installOracle )
	      echo "-installOracle was specified,  Parameter: $2"   ;
	      oracleFs="$2" ;
	      shift 2
	    ;;
	    
	    -moreSwap )
	      echo "-moreSwap was specified,  Parameter: $2"   ;
	      moreSwap="$2" ;
	      shift 2
	    ;;
	      
	    -installCsap )
	      echo "-installCsap was specified,  Parameter: $2"   ;
	      csapFs="$2" ;
	      shift 2
	    ;;
	      
	      
	    -extraDisk )
	      echo "-extraDisk was specified,  Parameter: $2 , Parameter $3"   ;
	    	extraDisk="$2" ;
	    	extraFs="$3" ;
	    	shift 3
	    ;;
	    
	    -targetFs )
	      echo "-targetFs was specified,  Parameter: $2"   ;
	      targetFs="$2" ;
	      shift 2
	    ;;
	    
	    -fsType )
	      echo "-fsType was specified,  Parameter: $2"   ;
	      fsType="$2" ;
	      shift 2
	    ;;
	      
	    -installActiveMq )
	      echo "-installActiveMq was specified,  Parameter: $2"   ;
	      mqFs="$2" ;
	      shift 2
	    ;;
	      
	    -n | -noPrompt )
	      echo "-noPrompt was triggered "  
	      isPrompt="0" ;
	      shift 1
	      ;;
	      
	    -memoryAuth )
	      echo "-memoryAuth was triggered "  
	      isMemoryAuth="1" ;
	      shift 1
	      ;;
	      
	    -s | -small )
	      echo "-sampleNoParam was triggered "  
	      isSmall="1" ;
	      shift 1
	      ;;
	      
	    -j | -java )
	      echo "-java was specified,  Parameter: $2"   ;
	      if [ "$2" == "6" ] ; then
	      	isJava7="0" ;
	      fi ;
	      shift 2
	    ;;
	      
	    -c | -clone )
	      echo "-clone was specified,  Parameter: $2"   ;
	      cloneHost="$2"
	      shift 2
	    ;;
	      
	    -starterUrl )
	      echo "-starterUrl was specified,  Parameter: $2"   ;
	      starterUrl="$2"
	      shift 2
	    ;;
	    
	    *)
	      	echo usage $0 optional -n -f -j \<7\> 
			echo n : no prompts
			echo clone host : will pull the clusterDef from specified host
			echo h : show this help
			echo "Note: oracle client install is now done via the cluster configuration. Refer to release notes."  
			echo exiting
			exit ;
	      shift 1
	    ;;
	  esac
	done

}

printIt "Processing parameters"
processCommandline $*


# note maven master may be needed
#csapDefaultJdk="$mavenRepoUrl/org/csap/JavaDevKitPackage/8u121.1/JavaDevKitPackage-8u121.1.zip"
#if [ "$mavenRepoUrl" == "default" ] ; then
#	printIt "Aborting installation: specify -mavenRepoUrl as it is used to install java: $csapDefaultJdk "
#	exit 12;
#fi;

if [ "$userPassword" == "password" ] ; then
	printIt "-pass was NOT specified - using default. It is strongly recommended to exit install and set" ;
	sleep 5;
fi;

printIt parameters: $*
echo == Settings: csapFs: $csapFs extraDisk: $extraDisk $extraFs  targetFs: $targetFs 
echo == fsType: $fsType mqFs: $mqFs  oracleFs: $oracleFs
echo == isJava7: $isJava7 cloneHost: $cloneHost JAVA_HOME: $JAVA_HOME isPrompt: $isPrompt 
echo; echo
sleep 2

	
function prompt() {
	echo;echo;
	echo = installFunctions.sh:
	echo = $*
	echo = 
	
	if [ $isPrompt  == "1" ] ; then
		echo == enter to continue or ctrl-c to exit
		read progress
	fi;
}



function wgetWrapper() {
	printIt wgetWrapper: $*
	if [ $toolsServer != "notSpecified" ] ; then
		wget $*
	else
		echo skipping wget because -toolsServer not specified. If found in /root it will be copied
		wname="/root/"`basename $1`
		if [ -f "$wname" ] ; then
			echo found $wname
			cp $wname .
		fi
	fi;
}

if [ $toolsServer != "notSpecified" ] ; then

	printIt testing connectivity using command: wget $toolsServer --tries=1 --timeout=3

	sleep 2
	wgetWrapper $toolsServer  --tries=1 --timeout=3
	if [ $? -gt 0 ]; then
	    echo == Failed to connect to $toolsServer. Network acls likely cause
	    echo == If gen2, ensure connectivity between systems by requesting policy entry
	    exit 99 ;
	fi
	
else
	prompt Running with no toolsServer
fi

echo
echo





doesUserExist() {
        /bin/egrep -i "^${1}" /etc/passwd
        if [ $? -eq 0 ]
        then
                return 0
        else
                return 1
        fi
}


checkgroup() {
        /bin/egrep -i "^${1}" /etc/group
        if [ $? -eq 0 ]
        then
                return 0
        else
                return 1
        fi
}


creategroup() {
        if checkgroup ${1}
        then
                echo ==  group ${1} exists
        else
                echo == Creating group ${1}
                /usr/sbin/groupadd ${1}
        fi
}

createuser() {
        if ! doesUserExist ${1}
        then
                echo ==  user ${1} exists, deleteing
                /usr/sbin/userdel ${1}
         fi
         
        echo == Creating user ${1}
        /usr/sbin/adduser $@
        echo -e "$userPassword\n$userPassword" | passwd ${1}

}

setupHomeDir() {
	echo == creating .bashrc .profile for user $1
	if [ "$1" != "" ] ; then
		\cp -f $HOME/dist/simple.bashrc /home/$1/.bashrc
		
		sed -i "s/CSAP_FD_PARAM/$maxOpenFiles/g" /home/$1/.bashrc
		sed -i "s/CSAP_THREADS_PARAM/$maxThreads/g" /home/$1/.bashrc
		
		\cp -f $HOME/dist/simple.bash_profile /home/$1/.bash_profile
		
		chown -R $1 /home/$1
		chgrp -R $1 /home/$1
	fi ;
}


##
## usage: replaceValueInFile "prop=value" filename
replaceComment="# csapChanged look at bottom of file "
replaceValueInFile() {


	propString=$1
	propName=${propString%=*}
	
	propFile=$2
	
	printIt Updating propString $propString  propFile $propFile 
	count=`grep $propName $propFile | wc -l`;
	
	if [ $count == 0 ] ; then 
		echo "$propString" >> $propFile
	else
		#sed -i "/$propName/ d" $propFile
		sed -i "/$propName/s/^/$replaceComment/" $propFile
		echo "$propString" >> $propFile
#		sed -i '/$propName/s/^/# csapChanged /' $propFile
#		sed -i '/$propName/ a\
#		$propString' $propFile
	fi
	
}

function createDisk() {
	
	diskVolume=$1
	diskSize=$2
	diskDevice="$CSAP_VOLUME_DEVICE/$diskVolume" ;
	
	# subtracting 1 from 1024 as sizing begins at 0. csapFS is specified in GB
	lvSize=$((diskSize*1024-5))M

	prompt "creating logical volume (lvcreate): diskGroup: $CSAP_VOLUME_GROUP, diskVolume: $diskVolume,  diskSize: $lvSize"
	lvcreate -L"$lvSize" -n$diskVolume $CSAP_VOLUME_GROUP
	
	# mke2fs $CSAP_VOLUME_DEVICE/$CSAP_INSTALL_VOLUME
	printIt "Cleanup: running umount $diskDevice";sleep 1;
	umount $diskDevice
	
	printIt "Creating $fsType on  $diskDevice (mkdfs) in 1 second"
	sleep 1;
	if [ "$fsType" == "ext4" ] ; then
		# journaled fs
		printIt "Running: mkfs -t $fsType -j $diskDevice"
		mkfs -t $fsType -j $diskDevice
	else 
		#mkfs -t $fsType $diskDevice ;
		printIt "Running: mkfs.xfs -f  $diskDevice"
		mkfs.xfs -f  $diskDevice
	fi ;
	
	if [ $? -gt 0 ]; then
	    printIt "Failed to complete: mkfs"
	    exit 909 ;
	fi
	
	printIt "Filesystem create completed $diskDevice `ls -l $diskDevice`"
	sleep 3		
}



targetInstall() {
	
	printIt Running targetInstall to: $targetFs/csap
	
	prompt running kills.sh
	kills.sh
	
	
	prompt stopping $APACHE_HOME/bin/apachectl stop
	$APACHE_HOME/bin/apachectl stop

	
	installDir=`pwd`
	\rm -rf $targetFs/csap
	mkdir $targetFs/csap
	cd $targetFs/csap
	
	if [ -e $HOME/csap*.zip ] ; then
		printIt "Found a csap install package in $HOME, adding a link"
		ln -s $HOME/csap*.zip .
	else
		printIt "Did not find csap zip in $HOME, it will be downloaded from tools server"
	fi ;
	
	STAGING=$targetFs/csap/staging
	PROCESSING=$targetFs/csap/processing
	
	\rm -rf $HOME/.cafEnv

	prompt == creating Csap  $HOME/.cafEnv

	echo  export STAGING=$STAGING >> $HOME/.cafEnv
	echo  export PROCESSING=$PROCESSING  >> $HOME/.cafEnv
	echo  export toolsServer=$toolsServer  >> $HOME/.cafEnv
	echo  export CSAP_NO_ROOT=yes >> $HOME/.cafEnv
	echo  export ORACLE_HOME="$STAGING/../oracle" >> $HOME/.cafEnv
	
	\cp -f $installDir/dist/simple.bash_profile $HOME/.bash_profile
	\cp -f $installDir/dist/simple.bashrc $HOME/.bashrc
	
	sed -i "s/CSAP_FD_PARAM/$maxOpenFiles/g" $HOME/.bashrc
	sed -i "s/CSAP_THREADS_PARAM/$maxThreads/g" $HOME/.bashrc
	
	printIt ulimits in bashrc are commented out. Validate with your sysadmin and set as needed 
	
	sed -i "s/ulimit/#ulimit/g" $HOME/.bashrc
	
	mkdir $targetFs/csap/java
	# java package installs relative to STAGING var, create a temp version to allow to proceed 
	mkdir $STAGING
	#javaInstall
	
	\rm -rf  $STAGING
	cd $targetFs/csap
	prompt "Continue with CSAP Agent Install"
	$installDir/dist/ssadminInstall.sh $*
	
	source $HOME/.bashrc
	printIt "to start the agent run: restartAdmin.sh"
	# restartAdmin.sh
}

ssadminExtraDisk() {
	
	if [ "$extraDisk" == "" ] ; then 
		return;
	fi ;
	
	printIt cleaning up $extraDisk
	sed -ie "\=$extraDisk= d" /etc/fstab
	
	printIt Creating CSAP data disk	
	createDisk $CSAP_EXTRA_VOLUME $extraFs
	
	printIt  mounting storage into ssadmin 
	
	printIt mounting extra storage into $extraDisk 
	echo $CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME $extraDisk $fsType defaults 1 2 >> /etc/fstab
	
	mkdir -p $extraDisk
	mount $extraDisk
	
	printIt chowning to ssadmin:  $extraDisk 
	chown -R ssadmin $extraDisk
}

ssadminInstall() {
	printIt Running ssadminInstall
	
	pkill -9 -u ssadmin
	
	if [ $installDisk == "default" ] ; then
		printIt Skipping ssadmin partition setup. Install will occur under /home/ssadmin 
		userdel -r ssadmin; useradd  ssadmin
		setupHomeDir ssadmin
	elif [ $installDisk == "vbox" ] ; then
		printIt Skipping ssadmin partition setup. Install will occur under /home/ssadmin 
		userdel -r ssadmin; useradd -G vboxsf ssadmin
		setupHomeDir ssadmin
	else
		printIt cleaning Up ssadmin
		sed -ie '/ssadmin/ d' /etc/fstab
		
		printIt Creating ssadmin filesystem	
		createDisk $CSAP_INSTALL_VOLUME $csapFs
		
		printIt  mounting storage into ssadmin 
		echo $CSAP_VOLUME_DEVICE/$CSAP_INSTALL_VOLUME /home/ssadmin $fsType defaults 1 2 >> /etc/fstab
		
		\rm -rf /home/ssadmin
		mkdir -p /home/ssadmin
		mount /home/ssadmin
		setupHomeDir ssadmin
	
	fi;
	
	printIt running ssadmin setup
	\cp -f $HOME/dist/ssadminInstall.sh /home/ssadmin
	\cp -f $HOME/dist/installFunctions.sh /home/ssadmin
	
	numberPackagesLocal=`ls -l csap6*.zip | wc -l`
	if (( $numberPackagesLocal == 1 )) ; then
		printIt "Found csap*zip - copying to ssadmin";
		\cp -f csap*zip /home/ssadmin
		chown ssadmin /home/ssadmin/csap*zip
	fi ;
	
	
	cp -r /root/version /home/ssadmin
	#chown -R ssadmin /home/ssadmin/version
	chown -R ssadmin /home/ssadmin
	
	if [ $isSkipAgent  == "1" ] ; then
		printIt skipping agent install
		return;
	fi;
	
	if [ "$sshCertDir" != "" ] ; then 
		echo == certs are optional
		\cp -f -r $HOME/dist/$sshCertDir /home/ssadmin/.ssh
		chown -R ssadmin /home/ssadmin/.ssh
		chgrp -R ssadmin /home/ssadmin/.ssh
		chmod 700 -R  /home/ssadmin/.ssh
		
		echo == selinux fix
		chcon -R unconfined_u:object_r:user_home_t:s0 /home/ssadmin/.ssh/
	else
		echo == No certs specified.
	fi
	
	su - ssadmin -c "/home/ssadmin/ssadminInstall.sh $*"
	
	installReturnCode="$?" ;
	
	printIt "CSAP application install return code: $installReturnCode"
	
	if [ "$installReturnCode" == "66" ] ; then
		printIt "Aborting install" ;
		exit $installReturnCode ;
	fi ;
	
	cd $HOME
	\cp -f dist/ssadmin.sh /etc/init.d/ssadmin

	chmod 755 /etc/init.d/ssadmin
	chkconfig --add ssadmin
	chkconfig ssadmin on
	
	ssadminExtraDisk
	
	prompt Starting CsAgent

	service ssadmin start
	
	printIt CSAP install complete. To validate: http://`hostname`:8011/CsAgent
	
	\rm -rf index.html*
}




mqInstall() {
	printIt Running mqInstall
	
	
	sed -ie '/mquser/ d' /etc/fstab
	
	printIt Creating mq filesystem	
	createDisk $CSAP_ACTIVEMQ_VOLUME $mqFs
	
	cd $HOME
	printIt creating mquser user
	/usr/sbin/adduser mquser
	echo -e "$userPassword\n$userPassword" | passwd mquser

	printIt mounting storage into /home/mquser
	echo $CSAP_VOLUME_DEVICE/$CSAP_ACTIVEMQ_VOLUME /home/mquser $fsType defaults 1 2 >> /etc/fstab
	mkdir -p /home/mquser
	mount /home/mquser
	setupHomeDir mquser
	
	printIt Active MQ Software is deployed by setting autoStart in capability definition file.

	cd $HOME
}



oracleInstall() {
	printIt Running oracleInstall
	
	rm -rf /etc/ora*	
	sed -ie '/oracle/ d' /etc/fstab

	
	cd $HOME

	printIt creating oracle user and groups
	creategroup oinstall
   	creategroup dba
	creategroup oper
	creategroup dsm

	createuser  oracle -d /home/oracle -s /bin/bash -g oinstall -G dba,oper,dsm
	

	
	printIt Creating oracle filesystem	
	createDisk $CSAP_ORACLE_VOLUME $oracleFs
	

	printIt mounting storage into /home/oracle
	echo $CSAP_VOLUME_DEVICE/$CSAP_ORACLE_VOLUME /home/oracle $fsType defaults 1 2 >> /etc/fstab
	mkdir -p /home/oracle
	mount /home/oracle
	setupHomeDir oracle
	chgrp -R oinstall /home/oracle  # override group name with oracle
	
	printIt setting up temp dir required for patching
	chown -R oracle /opt/ora*



}


#
#
# Ref http://download.oracle.com/docs/cd/B28359_01/install.111/b32002/pre_install.htm#BABBBDGA 
# http://www.oracle-base.com/articles/11g/OracleDB11gR2InstallationOnEnterpriseLinux5.php#OracleValidatedSetup
# section 2.7 and 2.8 
#
configureKernel() {
	
	if [ $skipKernalAndPackageUpdates  == "1" ] ; then
		printIt skipping kernel install
		return;
	fi;

	prompt Updating Kernel
	
	printIt backup restore of /etc/sysctl.conf
	if [  -e /etc/sysctl.conf.orig ] ; then
		cp /etc/sysctl.conf.orig  /etc/sysctl.conf
	else 
		 numMatches=`grep csapChanged /etc/sysctl.conf | wc -w`
		 if [ $numMatches != "0" ] ; then 
		 	echo hook to ensure a clean /etc/sysctl.conf , copying in from $HOME/dist/sysctl.conf
		 	cp $HOME/dist/sysctl.conf /etc/sysctl.conf
		 fi ;
		 
		 printIt  /etc/sysctl.conf to /etc/sysctl.conf.orig
		cp /etc/sysctl.conf /etc/sysctl.conf.orig
	fi ;
	
	echo "#" >> /etc/sysctl.conf
	echo "# csap Installer inserted values" >> /etc/sysctl.conf
	echo "#" >> /etc/sysctl.conf
	
	#echo == Removing previous comments in /etc/sysctl.conf
	#sed -i "s/$replaceComment//g" /etc/sysctl.conf 
	
	# previous instances will be commented out
	replaceValueInFile "fs.file-max = 6815744" /etc/sysctl.conf
	replaceValueInFile "fs.suid_dumpable = 1" /etc/sysctl.conf
	# replaceValueInFile "fs.aio-max-nr = 1048576" /etc/sysctl.conf
	replaceValueInFile "net.ipv4.ip_local_port_range = 9000 65500" /etc/sysctl.conf
	replaceValueInFile  "net.core.rmem_default=4194304" /etc/sysctl.conf
	replaceValueInFile  "net.core.rmem_max=4194304" /etc/sysctl.conf
	replaceValueInFile  "net.core.wmem_default=262144" /etc/sysctl.conf
	replaceValueInFile  "net.core.wmem_max=1048586" /etc/sysctl.conf
	
	# Oracle-Validated setting for kernel.shmmax is 4398046511104 on x86_64 and 4294967295 on i386 architecture. Refer Note id 567506.1
    # Changes Oracle version 11.2.0.2
        
	replaceValueInFile  "kernel.msgmni = 2878" /etc/sysctl.conf
	replaceValueInFile  "kernel.msgmax = 8192" /etc/sysctl.conf
	replaceValueInFile  "kernel.msgmnb = 65536" /etc/sysctl.conf
	replaceValueInFile  "kernel.sem = 250 32000 100 142" /etc/sysctl.conf
	replaceValueInFile  "kernel.shmmni = 4096" /etc/sysctl.conf
	replaceValueInFile  "kernel.shmall = 5368709120" /etc/sysctl.conf
	replaceValueInFile  "kernel.shmmax = 21474836480" /etc/sysctl.conf
	replaceValueInFile  "kernel.sysrq = 1" /etc/sysctl.conf
	replaceValueInFile  "fs.aio-max-nr = 3145728" /etc/sysctl.conf
	replaceValueInFile  "vm.min_free_kbytes = 51200" /etc/sysctl.conf
	replaceValueInFile  "vm.swappiness = 10" /etc/sysctl.conf
	

	printIt Updating /etc/security/limits.conf
	
	sed -i '/oracle/ d' /etc/security/limits.conf
	sed -i '/ssadmin/ d' /etc/security/limits.conf
	sed -i '/mquser/ d' /etc/security/limits.conf
	
	echo oracle              soft    nproc   2047 >> /etc/security/limits.conf
	echo oracle              hard    nproc   16384 >> /etc/security/limits.conf
	echo oracle              soft    nofile  1024 >> /etc/security/limits.conf
	echo oracle              hard    nofile  65536 >> /etc/security/limits.conf
	echo oracle              soft    stack   10240 >> /etc/security/limits.conf
	
	echo ssadmin              soft    nofile  1024 >> /etc/security/limits.conf
	echo ssadmin              hard    nofile  65536 >> /etc/security/limits.conf
	echo ssadmin              soft    nproc  2048 >> /etc/security/limits.conf
	echo ssadmin              hard    nproc  4096 >> /etc/security/limits.conf
	
	echo mquser              soft    nofile  1024 >> /etc/security/limits.conf
	echo mquser              hard    nofile  65536 >> /etc/security/limits.conf
	
	printIt Reloading kernel
	sysctl -p
	
	printIt Any errorss and you may need to do: sed -i 's/csapChanged//g' /etc/sysctl.conf
	
	prompt Kernel Updates complete
}



oracleCleanup() {
	#
 	# Oracle is stateful in the OS. 
	#
	
	for semid in `ipcs -s | grep -v -e - -e key  -e "^$" | cut -f2 -d" "`; do ipcrm -s $semid; done
	for semid in `ipcs -m | grep -v -e - -e key  -e "^$" | cut -f2 -d" "`; do ipcrm -m $semid; done
	
	printIt ipcs output, note that oracle install will fail if ipcs is still showing semaphores
	ipcs -a
	
	numLeft=`ipcs | grep -v -e - -e key -e root -e "^$" | wc -l`
	
	if [ $numLeft != "0" ] ; then
		printIt WARNING : oracle is not ok with semaphores on system
		sleep 10 ;
	fi
	printIt Cleaning up tmp
	rm -rf /tmp/*
	rm -rf /tmp/.oracle
	rm -rf /var/tmp/.oracle
	
	prompt Attempted to cleanup oracle on disk and os semaphores.
}



partionCleanUp() {
	
	if [ $installDisk == "default" ] || [ $installDisk == "vbox" ] ; then
		printIt Skipping partition setup. Install will occur under /home/ssadmin 
		return 
	fi;
	
	printIt partition cleanup: fdisk -l
	fdisk -l ; 
	
	
	printIt partition cleanup: vgdisplay
	vgdisplay ;
	
	prompt Partition Cleanup, Verify physical is correct. $CSAP_VOLUME_GROUP  will be deleted and recreated
	
	for filename in $CSAP_VOLUME_DEVICE/*; do
		
		printIt Wiping disk signatures from $filename
		wipefs -f -a $filename
		#sleep 3;
		
	done
	
	\rm -rf /opt 
	printIt Creating mountpoint for java and oracle /opt
	mkdir -p /opt/java
	
	
	# umount -fl l=lazy
	#fuser -k $extraDisk
	#umount -fl $extraDisk
	#lvremove -ff $CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME
	printIt killing any processes accessing disks
	fuser -k /home/oracle ; 
	fuser -k /home/ssadmin ; 
	fuser -k /home/mquser ; 
	if [ "$extraDisk" != "" ] ; then 
		fuser -k $extraDisk ; 
	fi ;
	
	
	printIt Unmounting any disks from previous installs
	umount -f /home/oracle $extraDisk /home/ssadmin /home/mquser
	
	numberOfCsapFileSystems=`df -h | grep $CSAP_VOLUME_GROUP |  wc -l`
	if (( numberOfCsapFileSystems > 0 )) ; then 
		printIt "Error found CSAP filesystems mounted: $numberOfCsapFileSystems" ;
		df -h  | grep $CSAP_VOLUME_GROUP
		echo "verify all filesystems are unmounted and removed using 'umount ...'"
		exit 58 ;
	fi;
	
	printIt Disabling any swap from previous installs
	swapoff /home/oracle/swapfile
	swapoff $CSAP_VOLUME_DEVICE/swapLV
	
	printIt removing shared memory as it may result in memory being used.
	umount /dev/shm
	sed -ie '/\/dev\/shm/ d' /etc/fstab
	
	printIt "Running dmsetup to remove volumes"
	dmsetup remove $CSAP_VOLUME_DEVICE-$CSAP_EXTRA_VOLUME
	dmsetup remove $CSAP_VOLUME_DEVICE-$CSAP_INSTALL_VOLUME
	dmsetup ls
	


	printIt  "Disabling any volumegroups from previous install (vgchange)"
	vgchange -a n $CSAP_VOLUME_GROUP
	
	printIt  "Removing logical volume $CSAP_VOLUME_GROUP (vgremove)"
	vgremove -f $CSAP_VOLUME_GROUP
	
	printIt "Verifying removal of $CSAP_VOLUME_GROUP"
	vgdisplay $CSAP_VOLUME_GROUP 
	if (( $? == 0 )) ; then 
		printIt "Error removing volume group: $CSAP_VOLUME_GROUP" ;
		echo "verify all volumes are unmounted and removed"
		dmsetup ls | grep $CSAP_VOLUME_GROUP
		exit 56 ;
	fi; 
	
	numberOfCsapVolumes=`dmsetup ls | grep $CSAP_VOLUME_GROUP |  wc -l`
	if (( numberOfCsapVolumes > 0 )) ; then 
		printIt "Error removing volume group: $CSAP_VOLUME_GROUP, volumes remaining: $numberOfCsapVolumes" ;
		dmsetup ls | grep $CSAP_VOLUME_GROUP
		echo "verify all volumes are unmounted and removed using 'dmsetup remove ...'"
		exit 57 ;
	fi;
	
	printIt  "Scanning disks"
	pvscan --cache
	
	printIt  "showing logical volumes lvdisplay"
	lvdisplay
	
	#
	# Check for required disk
	prompt Working only on "$installDisk" - wiping all partitions
	if [ ! -e "$installDisk" ] ; then
		if [ -e /dev/vdb ] ; then
			printIt " WARNING: installDisk set to /dev/vdb   because specified disk $installDisk does not exist" ; 
			installDisk="/dev/vdb"
			 fdisk -l 
			sleep 3 ;
		else
			printIt fdisk listing:
		    fdisk -l 
			printIt " MAJOR ERROR: $installDisk not found - supplementary disk is needed by CS-AP installer" ; 
			printIt " Use VM dashboard to confirm second disk is displayed, and confirm with fdisk -l " ;
			exit 99; 
		fi ;
	fi;
	
	printIt "partioning $installDisk in 3 seconds" ;
	sleep 3
	/sbin/parted --script "$installDisk" mklabel msdos
	
	#printIt Wiping disk signature from $installDisk
	#wipefs -a $installDisk
	
 	# this only works if all lvs are deleted first pvremove -y -ff "$installDisk"

	printIt issueing pvcreate $installDisk
	pvcreate -ff -y "$installDisk"
	
	printIt "Creating volume group: vgcreate $CSAP_VOLUME_GROUP $installDisk"  
	vgcreate $CSAP_VOLUME_GROUP "$installDisk"
	
	
	printIt "volumeGroup vgdisplay"
	vgdisplay
	prompt Verify volume groups, confirm creating of $CSAP_VOLUME_GROUP
	
	
	
}


timeZoneProcessing() {
	
	prompt Running timeZoneProcessing
	
	

	if [ "$vmZone" != "" ] ; then
		echo ZONE="$vmZone" > /etc/sysconfig/clock
		\rm -rf /etc/localtime
		ln -s /usr/share/zoneinfo/$vmZone /etc/localtime
		
		return ;
	fi ; 
	
	#
	# Check for correct timezone entries
	clockMatches=`grep  "Chicago" /etc/sysconfig/clock | wc -l`
	tzLink=`readlink /etc/localtime`
	if [[ $clockMatches == 0  ||  "$tzLink" != *Chicago ]] ; then
		
		printIt "VM Time zone WARNING: did not find Chicago in /etc/sysconfig/clock. Update manually if needed. Sample: "

		printIt MOST CSAP VMS run in central time. ctrl-c and update
		
		echo == hit enter to ignore or 
		echo == ctrl-c  and add -zone "America/Chicago"  param to auto correct to central time or
		echo == ctrl-c and manually set to your zone
		
		prompt Make a choice
		
	fi
}

patchOs() {
	
	if [ $skipKernalAndPackageUpdates  == "1" ] ; then
		printIt skipping OS package updates
		return;
	fi;
	
	#printIt installing key packages from CEL repos, contact is jawelsh.  Note that yourcompany repos are at http://wwwin-kickstart/yum
	# any issues should verify repos are configured in /etc/yum.repos.d
	#sed -i 's/enabled=no/enabled=1/g' /etc/yum.repos.d/yourcompany-internal-rhel.repo; sed -i 's/enabled=yes/enabled=1/g' /etc/yum.repos.d/yourcompany-internal-rhel.repo
	
	#	printIt Running package update using yum
	# yum -y update
	
	
	#mv /etc/yum.repos.d/* /tmp
	#\cp -f $HOME/dist/*.repo /etc/yum.repos.d
	#mv /etc/sysconfig/rhn /etc/sysconfig/rhn.bak
	yum clean all
	#yum -y  install nc dos2unix  gcc.x86_64  gcc-c++-4.1.2-48.el5.x86_64  vnc-server.x86_64 yum_utils  sysstat.x86_64 openssl097a.x86_64
	# yum -y  install nc dos2unix psmisc net-tools wget dos2unix sysstat lsof gcc  gcc-c++-4.1.2-48.el5.x86_64   yum_utils   openssl097a
	
	osPackages="nc dos2unix psmisc net-tools wget dos2unix sysstat lsof gcc  gcc-c++   yum_utils   openssl097a bind-utils" ;
	if [ $oracleFs != "0" ] ; then 
		osPackages="$osPackages make" ;
	fi ;
	printIt "OS Packages being installed or updated: $osPackages"
	yum -y  install $osPackages
	
	printIt removing openssl-devel.i686 as it seems to break httpd builds
	yum -y remove openssl-devel.i686
	
   # Use yum whatprovides to find packages needed. Eg.
	# yum list installed | grep openssl

	## cvs probably not needed anymore, gcc/g++ needed for httpd builds, vnc optional



	

#	ntpCheck=x=`rpm -q yourcompany-ntp-config`
#	printIt yourcompany NTP version $ntpCheck

	# if [[ "$ntpCheck" == *not\ installed* || "$ntpCheck" == *-1.2* ]] ; then
	# bug in later versions as well
#	if [[ "$ntpCheck" != *1.3-3* ]] ; then
#		printIt "Did not find yourcompany-ntp-config 1.3-3 rpm installed. It can be downloaded from: http://wwwin-kickstart-dev/yum/testing"
		#cd /var/tmp
		
		#printIt :
		#rpm -e yourcompany-ntp-config
	    #wget -nv http://wwwin-dev/yum/testing/yourcompany-ntp-config-1.3-3.noarch.rpm
	    #yum -y install yourcompany-ntp-config*
	    
	    # wget -nv http://wwwin-iaas-citeis-repo/yum/rhel6Server-x86_64/rpms.os/dos2unix-3.1-37.el6.x86_64.rpm
        # yum -y install dos2unix*
#	else 
#		printIt yourcompany ntp configuration is already installed
#	fi ; 

    
    printIt Complete os patching
}


coreInstall() {

	printIt Staring coreInstall
	
	timeZoneProcessing
	
	if ! doesUserExist ssadmin ; then
		printIt == creating ssadmin user first to ensure uid is consistent on all vms
		/usr/sbin/adduser ssadmin
		echo -e "$userPassword\n$userPassword" | passwd ssadmin
		echo;echo
	else
		printIt == ssadmin user already exists: skipping create
	fi
	

	printIt backup restore of /etc/bashrc
	
	if [  -e /etc/bashrc.orig ] ; then
		cp /etc/bashrc.orig  /etc/bashrc
	else 
		 numMatches=`grep JAVA_HOME /etc/bashrc | wc -w`
		 if [ $numMatches != "0" ] ; then 
		 	echo hook to ensure a clean /etc/bashrc , copying in from $HOME/dist/etcbashrc
		 	cp $HOME/dist/etcbashrc /etc/bashrc
		 fi ;
		 
		echo backing up  /etc/bashrc to /etc/bashrc.orig
		cp /etc/bashrc /etc/bashrc.orig
	fi ;
	
    # PLACED in env so csap install packages can reference if needed 
	echo  export toolsServer="$toolsServer" >> /etc/bashrc
	echo export ORACLE_HOME=/you/need/to/add/csap/package >> /etc/bashrc
	
	export PATH=$JAVA_HOME/bin:$PATH
		
	# get a simple bash profile first into root
	\cp -f $HOME/dist/simple.bash_profile $HOME/.bash_profile
	\cp -f $HOME/dist/simple.bashrc $HOME/.bashrc
	
	sed -i "s/CSAP_FD_PARAM/4096/g" $HOME/.bashrc
	sed -i "/CSAP_THREADS_PARAM/d" $HOME/.bashrc
	
	
	printIt getting rid of any prev installs killall java httpd
	killall java httpd  mpstat
	
	printIt stopping docker
	systemctl stop docker.service
	systemctl stop docker-latest.service
	
	printIt issueing killall on ssadmin
	killall -u ssadmin
	
	if [ -e /home/oracle/base/product/11.2*/db_1/bin/lsnrctl ] ; then 
		/home/oracle/base/product/11.2*/db_1/bin/lsnrctl stop;
	fi ;
	
	printIt issue pkill commands for users orace, ssadmin, mquser
	
	if [ `id -u oracle 2>&1 | wc -w` == 1 ] ; then pkill -9 -u oracle ; oracleCleanup ; fi
	if [ `id -u ssadmin 2>&1 | wc -w` == 1 ] ; then pkill -9 -u ssadmin ; fi
	if [ `id -u mquser 2>&1 | wc -w` == 1 ] ; then pkill -9 -u mquser ; fi
	
	printIt sleeping for 5 seconds to let everything die
	sleep 5 
	# rm -rf /opt /home/oracle/* /home/ssadmin/* /home/denodo/* /home/mquser/*

	partionCleanUp
	
	printIt Checking if /etc/sudoers is up to date
	
	numMatches=`grep scriptRunAsRoot.sh /etc/sudoers | wc -l`
	
	if [ $numMatches == 0 ] ; then 
		NOW=$(date +"%h-%d-%I-%M-%S")
		
		printIt Backing up /etc/sudoers to /etc/sudoers-csapInstall-$NOW
		cp /etc/sudoers /etc/sudoers-csapInstall-$NOW
		
		echo; echo ==
		echo ssadmin ALL=NOPASSWD: /usr/bin/pmap >> /etc/sudoers
		echo ssadmin ALL=NOPASSWD: /sbin/service >> /etc/sudoers 
		echo ssadmin ALL=NOPASSWD: /bin/kill >> /etc/sudoers  
		echo ssadmin ALL=NOPASSWD: /bin/rm >> /etc/sudoers 
		echo ssadmin ALL=NOPASSWD: /bin/nice >> /etc/sudoers 
		echo ssadmin ALL=NOPASSWD: /usr/bin/pkill >> /etc/sudoers 
		echo ssadmin ALL=NOPASSWD: /home/ssadmin/staging/bin/rootDeploy.sh >> /etc/sudoers   
		echo ssadmin ALL=NOPASSWD: /home/ssadmin/staging/bin/editAsRoot.sh >> /etc/sudoers   
		echo ssadmin ALL=NOPASSWD: /home/ssadmin/staging/bin/unzipAsRoot.sh >> /etc/sudoers   
		echo ssadmin ALL=NOPASSWD: /home/ssadmin/staging/bin/scriptRunAsRoot.sh >> /etc/sudoers
		echo ssadmin ALL=NOPASSWD: /bin/su >> /etc/sudoers
		# get rid of tty dependency so that this can be done via webapps
		sed -i "/requiretty/d"  /etc/sudoers
	fi ;
	
	configureKernel
	

	
	#javaInstall
	
	echo  export JAVA_HOME=$JAVA_HOME >> /etc/bashrc
	echo export PATH=\$JAVA_HOME/bin:\$PATH >> /etc/bashrc
	
	source $HOME/.bash_profile
	
	cd $HOME

	#
	# Check for correct resolv.conf entries
#	numMatches=`grep  yourcompany.com /etc/resolv.conf | wc -l`
#	if [ $numMatches == 0 ] ; then 
#		
#		printIt " WARNING: did not find yourcompany.com in /etc/resolv.conf. Will try to override with dist/kvm-resolv.conf" ; 
#		NOW=$(date +"%h-%d-%I-%M-%S")
#		
#		printIt Backing up /etc/resolv.conf to /etc/resolv.conf-csapInstall-$NOW
#		mv /etc/resolv.conf /etc/resolv.conf-csapInstall-$NOW
#		cp dist/kvm-resolv.conf /etc/resolv.conf
#			sleep 3 ;
#	fi ;
	
	#
	# Check for correct hosts entries
	numMatches=`grep  "$HOSTNAME" /etc/hosts | wc -l`
	if [ $numMatches == 0 ] ; then 
		
		printIt " WARNING: did not find $HOSTNAME in /etc/hosts. Update manually, and re run install. Sample: "
		echo echo ip $HOSTNAME $HOSTNAME '>>' /etc/hosts
		exit
		#ip=`hostname -i`
		#echo  $ip $name  $name  >> /etc/hosts
		#sleep 5 ;
		
	fi
	
	patchOs
	
	if [ "$moreSwap" != "0" ] ; then
		swapoff /home/oracle/swapfile
		#echo == creating swap for oracle 
		#dd if=/dev/zero of=/home/oracle/swapfile bs=1024 count=$((1048576*$moreSwap))
		#mkswap /home/oracle/swapfile
		#swapon /home/oracle/swapfile
		swapSize=$((moreSwap*1024-5))M
		lvcreate -L"$swapSize" -nswapLV $CSAP_VOLUME_GROUP
		mkswap $CSAP_VOLUME_DEVICE/swapLV
		# get rid of previouse
		sed -ie '/swapLV/ d' /etc/fstab
		echo $CSAP_VOLUME_DEVICE/swapLV swap swap defaults 0 0 >> /etc/fstab
		swapon -va
	fi

}



