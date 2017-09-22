#!/bin/bash

function printIt() { echo; echo; echo =========; echo == $* ; echo =========; }

printIt "Reference: http://www.itworld.com/operating-systems/317369/setting-limits-ulimit"


printIt "limit from cat /proc/sys/fs/file-nr: `cat /proc/sys/fs/file-nr`"


totalThread=`ps -e --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`
printIt "Total Threads: $totalThread"


userThreads=`ps -u$USER --no-heading --sort -pcpu -o pcpu,rss,nlwp,ruser,pid  | awk '{ SUM += $3} END { print SUM }'`
printIt "$USER Threads: $userThreads"	
	
numberFiles=`/usr/sbin/lsof 2>/dev/null | wc -l`
printIt "Open File Descriptors using lsof: $numberFiles"	
	
	
numUserFiles=`/usr/sbin/lsof 2>/dev/null | grep $USER  | wc -l`
printIt "$USER Open File Descriptors using lsof: $numUserFiles"	

numUserFiltered=`/usr/sbin/lsof -u $USER |grep /|sort  -k9 -u |wc -l`
printIt "$USER Open File Descriptors filtered by file  using lsof: $numUserFiltered"	

printIt "ulimit -a"
ulimit -a

printIt "Running cat on /etc/security/limits.conf configured by csap installer"
cat /etc/security/limits.conf

printIt "sysctl -a output. To update: /usr/sbin/sysctl -w net.ipv6.conf.all.forwarding=1 "
sysctl -a


