#!/bin/bash


header() {
	echo;echo
	echo "======================================================="
	echo -e "$*"
	echo "======================================================="
}

header "Uptime for host: $HOSTNAME"
uptime


header "File System"
df -h
