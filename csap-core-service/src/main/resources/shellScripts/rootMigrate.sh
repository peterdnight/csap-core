#!/bin/bash

#
# This file is overwritten everytime a os service is deployed. As a  1 time initialization for CsAp
# the first time startup will actually load this into sudoers
#

echo == Checking if /etc/sudoers is up to date

numMatches=`grep rootRenice.sh /etc/sudoers | wc -l`

# fix in case permissions 
echo == Bug in previous release might leave incorrect permissions. Not necessary in 3.0.1 or later
chown -R ssadmin /home/ssadmin/staging/scripts

if [ $numMatches == 0 ] ; then 
	NOW=$(date +"%h-%d-%I-%M-%S")
	
	echo == Backing up /etc/sudoers to /etc/sudoers-csapInstall-$NOW
	cp /etc/sudoers /etc/sudoers-csapInstall-$NOW
	
		echo ==
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
		echo ssadmin ALL=NOPASSWD: /home/ssadmin/staging/bin/rootRenice.sh >> /etc/sudoers
		echo ssadmin ALL=NOPASSWD: /bin/su >> /etc/sudoers
		# get rid of tty dependency so that this can be done via webapps
		sed -i "/requiretty/d"  /etc/sudoers
	
fi ;