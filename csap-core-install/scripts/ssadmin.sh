#!/bin/bash


# Source function library.
. /etc/init.d/functions

[ -f /home/ssadmin/staging/bin/startInstance.sh ] || exit 0

RETVAL=0

umask 077

start() {
       echo -n $"Starting ssadmin: "
       # this will do a autorestart after killing any existing services
       daemon --user=ssadmin /home/ssadmin/staging/bin/killInstance.sh -d
       echo
       return $RETVAL
}
stop() {
       echo -n $"Shutting  ssadmin: "
       daemon --user=ssadmin /home/ssadmin/staging/bin/kills.sh
       echo
       return $RETVAL
}

restart() {
       stop
       start
}
case "$1" in
 start)
       start
       ;;
 stop)
       stop
       ;;
 restart|reload)
       restart
       ;;
 *)
       echo $"Usage: $0 {start|stop|restart}"
       exit 1
esac

exit $?

