#!/bin/bash

# used in serviceresource runnable when run as root
pidstat -hd 15 1 | sed 's/  */ /g'

