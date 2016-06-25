# Master
An implementation of SecureSmartHome/master without Android dependencies.

## Build
```
./gradlew fatJar
```

## Start daemon
Ensure that jsvc is installed. (Package `java-jsvc` in ArchLinux)
```
/usr/bin/jsvc -home /usr/lib/jvm/default -outfile $PWD/out.txt -errfile '&1' -pidfile $PWD/pidfile -debug -stop -cp /usr/share/java/commons-daemon-1.0.15.jar:$PWD/master-all-1.0-SNAPSHOT.jar de.unipassau.isl.evs.ssh.master.Main
```
