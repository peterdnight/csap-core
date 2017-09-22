package org.csap.agent.services;

public enum HostKeys {
	
	lastCollected("lastCollected"), hostStats("hostStats"),
	services("services"), healthReportCollected("healthReportCollected");
	
	
	public String jsonId;
	private HostKeys(String jsonId) {
		this.jsonId = jsonId ;
	}

}
