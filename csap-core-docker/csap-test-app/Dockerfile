
FROM docker.io/peterdnight/csap-base
#FROM pnightin/csap-base

ENV csapTestVersion="1.0.33"

RUN echo installing CSAP sample code

RUN wget -nv --no-cookies --no-check-certificate \
	"http://maven.yourcompany.com/artifactory/yourgroup/org/csap/BootEnterprise/$csapTestVersion/BootEnterprise-$csapTestVersion.jar" \
	-O csapTest.jar


RUN sh -c 'touch csapTest_version_$csapTestVersion'
RUN sh -c 'touch csapTest.jar'

#ADD BootEnterprise-1.0.32.jar /csapTest.jar
VOLUME /tmp

# http 
EXPOSE 8080

ENV WORKING="/working"
WORKDIR "$WORKING"
RUN chmod 777 "$WORKING"

# docker run -it -u 0 --entrypoint="bash" daa27344c067

ENTRYPOINT [ \
	"java", "-Xms256M", "-Xmx256M", \
	"-DcsapDockerJava", \
	"-Dspring.profiles.active=embedded", \
	"-DcsapJmxPort=8086", \
	"-Dserver.port=8080", \
	"-jar", \
	"/csapTest.jar" \
]




# for local and fast builds
# for debugging builds and multiple tries - copy jdk and others into local build context target/docker/pnightin/csap-tester/build/
#RUN echo adding jdk
#ADD jdk-8u141-linux-x64.tar.gz /java-local

#RUN echo adding secure java 
#ADD jce_policy-8.zip /java-local/jce_policy-8.zip
#RUN unzip -qq -o /java-local/jce_policy-8.zip -d /java-local/jdk1.8.0_141/jre/lib/security

#RUN sh -c 'touch /csapTest.jar'



#jmx 
#EXPOSE 8086

#
# Java RMI requires explicit host name - Recommend a string substition at lauch  
#
#ENTRYPOINT [ \
#	"java", "-Xms256M", "-Xmx256M", \
#	"-Dspring.profiles.active=embedded", \
#	"-Djava.security.egd=file:/dev/./urandom", \
#	"-Djava.rmi.server.hostname=_HOST_NAME_",  "-Dcom.sun.management.jmxremote.port=8086", "-Dcom.sun.management.jmxremote.rmi.port=8086", \
#	"-Dcom.sun.management.jmxremote.local.only=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-Dcom.sun.management.jmxremote.ssl=false", \
#	"-jar","/csapTest.jar" \
# ]


