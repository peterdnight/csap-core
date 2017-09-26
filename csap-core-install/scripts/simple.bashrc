#!/bin/bash

# Source global definitions
if [ -f /etc/bashrc ]; then
        . /etc/bashrc
fi

# User specific aliases and functions

function prompt_command {
     #   How many characters of the $PWD should be kept
     local pwd_length=32
     if [ $(echo -n $PWD | wc -c | tr -d " ") -gt $pwd_length ]; then
        newPWD="$(echo -n $PWD | sed -e "s/.*\(.\{$pwd_length\}\)/\1/")"
        curIndex=`expr index "$newPWD" //`
        if [ $curIndex -gt 0 ]; then
                #echo index is $curIndex
                #newPWD="$NC.../$HILIT${newPWD:$curIndex}"
                newPWD=".../${newPWD:$curIndex}"
        fi
     else
        newPWD="$(echo -n $PWD)"
     fi

     #another choice - just use the last directories
     # PS1='${PWD#${PWD%/[!/]*/*}/} '
        PS1="`hostname`:$newPWD> "
}
export PROMPT_COMMAND=prompt_command

alias s="source ~/.bashrc"
ulimit -n CSAP_FD_PARAM
ulimit -u CSAP_THREADS_PARAM
#
export PATH=$PATH:/sbin
export PATH=`echo -n $PATH | awk -v RS=: '{ if (!arr[$0]++) {printf("%s%s",!ln++?"":":",$0)}}'`
