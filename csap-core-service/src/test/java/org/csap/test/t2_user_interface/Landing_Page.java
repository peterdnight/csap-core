package org.csap.test.t2_user_interface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.csap.agent.CsapCoreService;
import org.csap.test.t1_container.Boot_Container_Test;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@SpringBootTest(classes = CsapCoreService.class)
@ActiveProfiles("junit")
public class Landing_Page {
	final static private Logger logger = LoggerFactory.getLogger( Landing_Page.class );

	@Autowired
	private WebApplicationContext wac;

	private MockMvc mockMvc;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		Boot_Container_Test.printTestHeader( logger.getName() );

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		// db.shutdown();
	}

	@Before
	public void setUp() throws Exception {
		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build();

	}


	@After
	public void tearDown() throws Exception {
	}

	ObjectMapper jacksonMapper = new ObjectMapper();



	@Test
	public void http_get_landing_page() throws Exception {
		logger.info(Boot_Container_Test.TC_HEAD + "simple mvc test" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get(  "/test" )
						.param( "sampleParam1", "sampleValue1" )
						.param( "sampleParam2", "sampleValue2" )
						.accept( MediaType.TEXT_PLAIN ) );

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/html;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "result:\n" + result );

		assertThat( result )
				.contains( "hello") ;

	}

	@Test
	public void http_get_hello_endpoint() throws Exception {
		logger.info(Boot_Container_Test.TC_HEAD + "simple mvc test" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/hello" )
						.accept( MediaType.TEXT_PLAIN ) );

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/plain;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "result:\n" + result );

		assertThat( result )
				.startsWith( "Hello") ;
	}

	
	@Ignore
	@Test
	public void http_get_hello_round_robin() throws Exception {
		logger.info(Boot_Container_Test.TC_HEAD + "simple mvc test" );
		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				get( "/helloRoundRobin" )
						.accept( MediaType.TEXT_PLAIN ) );

		//
		String result = resultActions
				.andExpect( status().isOk() )
				.andExpect( content().contentType( "text/plain;charset=UTF-8" ) )
				.andReturn().getResponse().getContentAsString();
		logger.info( "result:\n" + result );

		assertThat( result )
				.startsWith( "Response: Hello") ;
	}
	

}
