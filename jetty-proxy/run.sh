#!/bin/bash -e

PROXY_TO=http://localhost:3000/

root="$(cd "$(dirname "$0")" && pwd)"

if ! [ "$root/target/.classpath" -nt "$root/pom.xml" ]; then
    mvn dependency:build-classpath compile -DincludeScope=test -Dmdep.outputFile=target/.classpath
fi

exec java -cp "$root/target/classes:$(cat $root/target/.classpath)" \
   "-Dlog4j.configuration=file://$root/log4j.properties" \
   run.Guice \
   -Mcom.distelli.webserver.proxy.ProxyAllModule="$PROXY_TO" \
   com.distelli.webserver.GuiceWebServer.run=8080
