package org.csap.test.t5_data_collection;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.springframework.jmx.support.MBeanServerConnectionFactoryBean;

public class JmxConnection {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String opHost = "csap-dev01";
		//opHost = "csseredapp-dev-03";
		String opPort = "8016";
		//opPort = "8356";

//		String serviceUrl = "service:jmx:rmi://" + opHost
//				+ "/jndi/rmi://" + opHost + ":" + opPort
//				+ "/jmxrmi";
		String serviceUrl = "service:jmx:rmi:///jndi/rmi://" + opHost + ":" + opPort
				+ "/jmxrmi";
		try {
			MBeanServerConnectionFactoryBean jmxFactory = null;
			String mbeanName = "java.lang:type=Memory";
			String attributeName = "HeapMemoryUsage";
			jmxFactory = new MBeanServerConnectionFactoryBean();
			jmxFactory.setServiceUrl( serviceUrl );

			jmxFactory.afterPropertiesSet();

			long start = System.currentTimeMillis();
			MBeanServerConnection mbeanConn = jmxFactory.getObject();

			CompositeData resultData = (CompositeData) mbeanConn
					.getAttribute( new ObjectName( mbeanName ),
							attributeName );

			int heapUsed = (int) Long
					.parseLong( resultData.get( "used" )
							.toString() ) / 1024 / 1024;

			int heapMax
					= (int) Long.parseLong( resultData.get( "max" )
							.toString() ) / 1024 / 1024;

			System.out.println( "heapUsed" + heapUsed );
			System.out.println( "heapMax" + heapMax );
			System.out.println( opHost + " === Time Taken (ms): " + (System.currentTimeMillis() - start) );

			// Tomcat only
//			String connName = "http-nio-8021" ; 
//			// connName = "http*" ; 
//			mbeanName = "Catalina:type=ThreadPool,name=\""
//					+ connName + "\"";
//			attributeName = "connectionCount";
//			Long conns = (Long) mbeanConn.getAttribute(
//					new ObjectName(mbeanName),
//					attributeName);
//
//			System.out.println("conns" + conns) ;
//			
//
//			// connName = "http*" ; 
//			mbeanName = "Catalina:type=ThreadPool,name=\"*\"";
//			attributeName = "connectionCount";
//			Set<ObjectInstance> matchingBeans = mbeanConn.queryMBeans(
//					new ObjectName(mbeanName),
//					null);
//
//			conns = 0l;
//			for (ObjectInstance objectInstance : matchingBeans) {
//				System.out.println("objectInstance: " + objectInstance.getObjectName()) ;
//				conns += (Long) mbeanConn.getAttribute(
//						objectInstance.getObjectName(),
//						attributeName);
//			}
//			System.out.println("conns" + conns) ;
//			mbeanName = "Catalina:type=ThreadPool,name=\"p*\"";
//			Set<ObjectName> queryNames(ObjectName name, QueryExp query) ;
//			for (ObjectInstance objectInstance : matchingBeans) {
//				System.out.println("objectInstance: " + objectInstance.getObjectName()) ;
//			}
		} catch ( Exception e ) {
			System.out.println( "Failed to collect" + e );
		}

	}

}
