#!/bin/bash

# Notes:
# 1. this textarea is resizable by grabbing the lower right corner
# 2. for long running commands, use linux nohup eg. nohup "someCommand.sh" $args  > yourResults.txt &
# 3. CS-AP file browser can be used to tail the in progress commands results file

cd /home/ssadmin/processing
rpm -qa | grep bash

numMatches=`grep  "6." /etc/redhat-release | wc -l`
if [ $numMatches == 0 ] ; then 
	echo patching rh5	
	cd /var/tmp
	wget -nv http://wwwin-kickstart-dev/ALLRPMS/redhat/5/bash-3.2-33.el5.1.x86_64.rpm
	yum -y localupdate bash*rpm

else	
		
	echo updating  redhat 6
	yum -y update http://wwwin-kickstart-dev/ALLRPMS/redhat/6/bash-4.1.2-15.el6_5.1.x86_64.rpm http://wwwin-kickstart-dev/ALLRPMS/redhat/6/bash-debuginfo-4.1.2-15.el6_5.1.x86_64.rpm http://wwwin-kickstart-dev/ALLRPMS/redhat/6/bash-doc-4.1.2-15.el6_5.1.x86_64.rpm

fi



rpm -qa | grep bash
