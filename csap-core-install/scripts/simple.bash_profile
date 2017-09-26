# @(#)local.profile 1.8 99/03/26 SMI
stty istrip
stty erase ^H

#.................................................................
set -o vi

export EDITOR=vi

# set default files to 755 -rwxr-xr-x
umask 022


# Prompt
export PS1="[\u@\h] \W [\!] "

### Variables that don't relate to bash

# Set variables for a warm fuzzy environment
#export EDITOR=/usr/local/bin/emacs
export PAGER=less

# Execute the subshell script
source ~/.bashrc

PS1="\h:\w> "
