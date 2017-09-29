package org.csap.test.t6_linux_integration;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.csap.agent.linux.TopRunnable;
import org.csap.test.t1_container.Boot_Container_Test;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Top_Command_Test {

	final static private Logger logger = LoggerFactory.getLogger( Top_Command_Test.class );
	


	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName()) ;
	}
	
	@Test
	public void checkForVmCpu() {
		TopRunnable topRunnable = new TopRunnable(1) ;
		
		int attempts=0 ;
		String cpu ="" ;
		while ( attempts++ < 5) {
			
			String[] pids = { TopRunnable.VM_TOTAL } ;
			 cpu = topRunnable.getCpuForPid(Arrays.asList(  pids ) );
			
			if ( !cpu.equals("0.0")) break ;
			
			try {
				// Only do work if needed
				logger.info("Waiting for resutls");
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		assertTrue("Found Total", attempts < 4);
		assertEquals("CPu parsed", "33.0" , cpu );
		
	}
	
	@Test
	public void simpleString() {
		
		String message="3.0.26 Maven Deploy by someDeveloper using Cssp3Reference on csap-dev01" ;
		logger.info("Contains space: " + message.contains(" ") + "Index of space: " + message.indexOf(" "));
	}

}
