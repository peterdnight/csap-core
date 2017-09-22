#!/bin/bash

params=$@

# echo params is: $params
# Supress any info messages
source $STAGING/bin/dirVars.sh >/dev/null

csapShellJars="$PROCESSING/CsAgent_8011/jarExtract/BOOT-INF/lib/*"
# echo == cp is $cp
# A little tricky but we run eval to allow caller to quote params
eval -- java -classpath \"$csapShellJars\" CsapShell  $params


# sapShell  $params