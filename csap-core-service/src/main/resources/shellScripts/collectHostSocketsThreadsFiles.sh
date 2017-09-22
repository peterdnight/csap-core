#!/bin/bash

#
# Run by OsManager.java as part of shared collections
#

openFiles=`cat /proc/sys/fs/file-nr | awk '{print $1}'`
totalThreads=`ps -e --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`
csapThreads=`ps -u$USER --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`

networkConns=`ss | grep -iv wait | wc -l`	
networkWait=`ss | grep -i wait | wc -l`	
totalFileDescriptors=-1 ;	
csapFileDescriptors=-1 ;	
	
# takes a long time
# totalFileDescriptors=`/usr/sbin/lsof  | wc -l`
csapFileDescriptors=`/usr/sbin/lsof -u $USER  | wc -l`
totalFileDescriptors=0;

# default to only using the current user.
allUsers=$USER ;

if [ "$USER" == "root" ] ; then	
	# if running as root - add all together
	allUsers=$(sed</etc/passwd 's/:.*$//g');
fi ;

for userid in $allUsers; do 
   currentUserCount=`lsof -u $userid  2>/dev/null | wc -l`
   totalFileDescriptors=$((totalFileDescriptors+ currentUserCount))
done


##
## Do not modify without updating parsing in OsManager 
##
echo openFiles: $openFiles totalThreads: $totalThreads csapThreads $csapThreads \
totalFileDescriptors: $totalFileDescriptors csapFileDescriptors: $csapFileDescriptors \
networkConns: $networkConns networkWait: $networkWait