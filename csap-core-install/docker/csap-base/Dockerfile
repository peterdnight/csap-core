#
# ref https://springframework.guru/running-spring-boot-in-a-docker-container/
# 


FROM centos

# used in dockerbuild
ENV javaVersion="141"

ENV JAVA_HOME="/java-local/jdk1.8.0_$javaVersion"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV PARENT_HOST="$(hostname)"

RUN echo installing core os tools
RUN yum -y install wget which unzip
RUN yum -y update bind-license

RUN echo installing java
RUN mkdir -p /java-local ;
WORKDIR "/java-local"
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/java/jdk-8u$javaVersion-linux-x64.tar.gz" 
RUN tar -xzf jdk*.tar.gz
RUN rm -rf jdk*.tar.gz


RUN echo installing java strong encyption
RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/java/jce_policy-8.zip"
RUN unzip -qq -o jce*.zip -d jdk*/jre/lib/security
RUN rm -rf jce*.zip
RUN mv -f  jdk*/jre/lib/security/UnlimitedJCEPolicyJDK8/*.jar  jdk*/jre/lib/security



WORKDIR "/"
RUN echo simple java test2

ENTRYPOINT [ \
	"java",  \
	"-version" \
]
