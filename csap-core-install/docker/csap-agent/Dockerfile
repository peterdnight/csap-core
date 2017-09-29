
FROM docker.io/peterdnight/csap-base

RUN yum -y install net-tools sysstat
ENV csapInstaller="5.1.7-SNAPSHOT"


ENV USER="csapadmin"
ENV HOME="/home/$USER"
WORKDIR $HOME

RUN echo "getting csap installer"

ADD CsapInstall-$csapInstaller.zip .
#RUN wget -nv --no-cookies --no-check-certificate \
#	"http://maven.yourcompany.com/artifactory/yourartifactory/org/csap/CsapInstall/$csapInstaller/CsapInstall-$csapInstaller.zip" 

RUN unzip -qq CsapInstall*zip

ENV csapAgent="5.1.6"	
ADD csap$csapAgent.zip .
#RUN wget -nv --no-cookies --no-check-certificate \
#	"http://csaptools.yourcompany.com/csap/csap$csapAgent-full.zip" 

RUN groupadd -r csapadmin
RUN useradd -r -g csapadmin -d /home/csapadmin -s /sbin/nologin -c "Docker image user" csapadmin

RUN chown -R csapadmin /home/csapadmin
USER csapadmin

RUN mkdir $HOME/demo; ./dist/install.sh -targetFs $HOME/demo -noPrompt -toolsServer csaptools -mavenRepoUrl  http://maven.yourcompany.com/artifactory/yourgroup

# http 
EXPOSE 8011


# adding -DcsapJava8 will trigger csap to  mount java
# -DcsapJmxPort triggers jmx configuration WHEN started from CSAP
#  refer to https://wiki.yourcompany.com/display/SFAECSAP/Csap+Docker
ENV PATH="$HOME/demo/csap/staging/bin/:${PATH}"
ENV agentLogs="$HOME/demo/csap/processing/CsAgent_8011/logs/consoleLogs.txt"
ENTRYPOINT [ \
			"/bin/bash", \
			"-c", \
			"echo running as `id`; echo \"path is $PATH\"; java -version; restartAdmin.sh && sleep 2 && tail -f  $agentLogs" \
]
