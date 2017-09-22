package org.csap.agent.services;

public enum DockerJson {

	containerName("containerName"), versionCommand("versionCommand"),imageName("image"), errorReason("reason"), error("error"), 
	command("command"), entryPoint("entryPoint"), workingDirectory("workingDirectory"),
	networkMode("networkMode"), restartPolicy("restartPolicy"),  runUser("runUser"),
	portMappings("portMappings"),
	environmentVariables("environmentVariables"),
	defaultJavaDocker("defaultJavaDocker"),
	hostJavaDocker("hostJavaDocker");
	
	public String key ;
	
	private DockerJson(String key) {
		this.key = key ;
	}
}
