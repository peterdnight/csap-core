package org.csap.test.t2_user_interface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = CsapCoreService.class)
@ActiveProfiles("junit")
public class Host_Operations_Test {
	final static private Logger logger = LoggerFactory.getLogger( Host_Operations_Test.class );

	 @Autowired
	 private WebApplicationContext wac;

	 private MockMvc mockMvc;

	 @BeforeClass
	 public static void setUpBeforeClass() throws Exception {
			InitializeLogging.printTestHeader( logger.getName() );
	 }

	 @AfterClass
	 public static void tearDownAfterClass() throws Exception {
	 }

	 @Inject
	 Application csapApp;

	 @Before
	 public void setUp() throws Exception {
		  logger.info( "Deleting: {}",  csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile().getAbsolutePath());

		  Application.setJvmInManagerMode(false);
		  csapApp.setAutoReload(false);

		  FileUtils.deleteQuietly(csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile());

		  File csapApplicationDefinition = new File(
					 getClass().getResource("/org/csap/test/data/test_application_model.json").getPath());

		  logger.info("Loading test configuration: {}",  csapApplicationDefinition);
		  
		  assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		  this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
		  csapApp.setTestMode(true);
	 }

	 @After
	 public void tearDown() throws Exception {
	 }

	 ObjectMapper jacksonMapper = new ObjectMapper();

	 @Test
	 public void validate_get_cpu() throws Exception {

		  String message = InitializeLogging.TC_HEAD + "Getting cpu json\n";
		  logger.info(message);
		  
		  assertThat( csapApp.isCollectorsStarted() ).isTrue() ;

		  // mock does much validation.....
		  ResultActions resultActions = mockMvc.perform(
					 post(CsapCoreService.OS_URL + "/getCpu")
					 .param("filter", "yes")
					 .accept(MediaType.APPLICATION_JSON));

		  String responseText = resultActions
					 .andExpect(status().isOk())
					 .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					 .andReturn()
					 .getResponse()
					 .getContentAsString();

		  // Validate response
		  JsonNode responseJson = jacksonMapper.readTree(responseText);

		  logger.info("result:\n" + jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseJson));

		  assertTrue(message + " has ps", responseJson.has("ps"));

		  assertTrue(message + " has mp", responseJson.has("mp"));

		  assertTrue(message + " has CsAgent", responseJson.get("ps").has("CsAgent"));

		  assertTrue(message + " has currentProcessPriority", responseJson.get("ps").get("CsAgent").has("currentProcessPriority"));

	 }

	 @Test
	 public void getManagerJson() throws Exception {

		  logger.info(InitializeLogging.TC_HEAD);

		  // mock does much validation.....
		  ResultActions resultActions = mockMvc.perform(
					 get(CsapCoreService.OS_URL + "/getManagerJson")
					 .param("resetCache", "no")
					 .accept(MediaType.APPLICATION_JSON));

		  // But you could do full parsing of the Json result if needed
		  String responseJson = resultActions
					 .andExpect(status().isOk())
					 .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					 .andReturn()
					 .getResponse()
					 .getContentAsString();

		  // Validate response
		  JsonNode jsonResults = jacksonMapper.readTree(responseJson);

		  // Print it out for generating tests using jackson
		  logger.info(jacksonMapper.writerWithDefaultPrettyPrinter()
					 .writeValueAsString(jsonResults));

		  assertTrue("Found $.hostStats",
					 jsonResults.at("/hostStats") != null);

		  assertEquals("Found hostStats.cpuCount value", "62%",
					 jsonResults.at("/hostStats/df/~1").asText());
	 }

	 StringBuilder sampleScript = new StringBuilder();

	 @Test
	 public void executeScript() throws Exception {

		  String message = InitializeLogging.TC_HEAD + "Executing script\n";
		  logger.info(message);

		// create scriptDir
		  if (!csapApp.getScriptDir().exists()) {
				logger.info("Creating scriptDir: " + csapApp.getScriptDir().getCanonicalPath());
				csapApp.getScriptDir().mkdirs();
		  }

		  sampleScript.append("#!/bin/bash\n");
		  sampleScript.append("ls\n");

		  // mock does much validation.....
		  ResultActions resultActions = mockMvc.perform(
					 get(CsapCoreService.OS_URL + "/executeScript")
					 .param("contents", sampleScript.toString())
					 .param("chownUserid", "ssadmin")
					 .param("hosts", "localhost")
					 .param("jobId", Long.toString(System.currentTimeMillis()))
					 .param("scriptName", "junitTest.sh")
					 .param("timeoutSeconds", "30")
					 .accept(MediaType.APPLICATION_JSON));

		  // But you could do full parsing of the Json result if needed
		  String responseJson = resultActions
					 .andExpect(status().isOk())
					 .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					 .andReturn()
					 .getResponse()
					 .getContentAsString();

		  // Validate response
		  JsonNode jsonResults = jacksonMapper.readTree(responseJson);

		  // Print it out for generating tests using jackson
		  logger.info(jacksonMapper.writerWithDefaultPrettyPrinter()
					 .writeValueAsString(jsonResults));

		  assertTrue("Found /scriptOutput",
					 jsonResults.at("/scriptOutput") != null);

		  assertTrue("Found /scriptOutput", jsonResults.at("/scriptOutput").size() >= 2);
	 }
}
