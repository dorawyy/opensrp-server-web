package org.opensrp.web.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.opensrp.domain.AssignedLocations;
import org.opensrp.domain.Organization;
import org.opensrp.search.OrganizationSearchBean;
import org.opensrp.service.OrganizationService;
import org.opensrp.service.PractitionerService;
import org.opensrp.web.GlobalExceptionHandler;
import org.opensrp.web.bean.OrganizationAssigmentBean;
import org.opensrp.web.config.security.filter.CrossSiteScriptingPreventionFilter;
import org.opensrp.web.rest.it.TestWebContextLoader;
import org.smartregister.domain.Practitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.AssertionErrors.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Samuel Githengi created on 09/17/19
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = TestWebContextLoader.class, locations = { "classpath:test-webmvc-config.xml", })
@ActiveProfiles(profiles = { "jedis", "postgres", "basic_auth" })
public class OrganizationResourceTest {

	@Rule
	public MockitoRule rule = MockitoJUnit.rule();

	@Autowired
	protected WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@Mock
	private OrganizationService organizationService;

	@Mock
	private PractitionerService practitionerService;

	@Captor
	private ArgumentCaptor<Organization> organizationArgumentCaptor;

	@InjectMocks
	private OrganizationResource organizationResource;

	private String BASE_URL = "/rest/organization/";

	private String organizationJSON = "{\"id\":null,\"identifier\":\"801874c0-d963-11e9-8a34-2a2ae2dbcce4\",\"active\":true,\"name\":\"B Team\",\"partOf\":1123,\"type\":{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/organization-type\",\"code\":\"team\",\"display\":\"Team\"}]},\"assignedLocations\":null,\"dateCreated\":null,\"dateEdited\":null,\"serverVersion\":0,\"memberCount\":null}";

	private static ObjectMapper objectMapper = new ObjectMapper();
	private String MESSAGE = "The server encountered an error processing the request.";

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(organizationResource)
				.setControllerAdvice(new GlobalExceptionHandler()).
						addFilter(new CrossSiteScriptingPreventionFilter(), "/*").build();
		objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
	}

	@Test
	public void testGetAllOrganizations() throws Exception {
		List<Organization> expected = Collections.singletonList(getOrganization());
		when(organizationService.getAllOrganizations(any(OrganizationSearchBean.class))).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL)).andExpect(status().isOk()).andReturn();
		verify(organizationService).getAllOrganizations(any(OrganizationSearchBean.class));
		verifyNoMoreInteractions(organizationService);
		assertEquals("[" + organizationJSON + "]", result.getResponse().getContentAsString());

	}

	@Test
	public void testGetAllOrganizationsUnderLocation() throws Exception {
		List<Organization> expected = Collections.singletonList(getOrganization());
		when(organizationService.getAllOrganizations(any(OrganizationSearchBean.class))).thenReturn(expected);
		mockMvc.perform(get(BASE_URL).param("location_id", "12345")).andExpect(status().isOk()).andReturn();
		verify(organizationService).selectOrganizationsEncompassLocations("12345");
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testGetOrganizationByIdentifier() throws Exception {
		Organization expected = getOrganization();
		when(organizationService.getOrganization(expected.getIdentifier())).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL + "/{identifier}", expected.getIdentifier()))
				.andExpect(status().isOk()).andReturn();
		verify(organizationService).getOrganization(expected.getIdentifier());
		verifyNoMoreInteractions(organizationService);
		assertEquals(organizationJSON, result.getResponse().getContentAsString());

	}

	@Test
	public void testCreateOrganization() throws Exception {

		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isCreated());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		assertEquals(organizationJSON, objectMapper.writeValueAsString(organizationArgumentCaptor.getValue()));
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testCreateOrganizationWithoutIdentifier() throws Exception {
		doThrow(IllegalArgumentException.class).when(organizationService).addOrganization(any(Organization.class));
		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isBadRequest());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testCreateOrganizationWithError() throws Exception {
		doThrow(RuntimeException.class).when(organizationService).addOrganization(any(Organization.class));
		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isInternalServerError());
		verify(organizationService).addOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganization() throws Exception {

		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isCreated());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		assertEquals(organizationJSON, objectMapper.writeValueAsString(organizationArgumentCaptor.getValue()));
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganizationWithoutIdentifier() throws Exception {
		doThrow(new IllegalArgumentException()).when(organizationService)
				.updateOrganization(any(Organization.class));
		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isBadRequest());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testUpdateOrganizationWithError() throws Exception {
		doThrow(new IllegalStateException()).when(organizationService).updateOrganization(any(Organization.class));
		mockMvc.perform(put(BASE_URL + "/{identifier}", getOrganization().getIdentifier())
				.contentType(MediaType.APPLICATION_JSON).content(organizationJSON.getBytes()))
				.andExpect(status().isInternalServerError());
		verify(organizationService).updateOrganization(organizationArgumentCaptor.capture());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testAssignLocationAndPlan() throws Exception {
		mockMvc.perform(post(BASE_URL + "/assignLocationsAndPlans").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(getOrganizationAssignment()))).andExpect(status().isOk());
		for (OrganizationAssigmentBean bean : getOrganizationAssignment())
			verify(organizationService).assignLocationAndPlan(bean.getOrganization(), bean.getJurisdiction(),
					bean.getPlan(), bean.getFromDate(), bean.getToDate());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testAssignLocationAndPlanWithMissingParams() throws Exception {
		doThrow(new IllegalArgumentException()).when(organizationService).assignLocationAndPlan(null, null,
				null, null, null);
		OrganizationAssigmentBean[] beans = new OrganizationAssigmentBean[] { new OrganizationAssigmentBean() };
		mockMvc.perform(post(BASE_URL + "/assignLocationsAndPlans").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(beans))).andExpect(status().isBadRequest());
		for (OrganizationAssigmentBean bean : beans)
			verify(organizationService).assignLocationAndPlan(bean.getOrganization(), bean.getJurisdiction(),
					bean.getPlan(), bean.getFromDate(), bean.getToDate());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testAssignLocationAndPlanWithInternalError() throws Exception {
		doThrow(new IllegalStateException()).when(organizationService).assignLocationAndPlan(null, null, null, null,
				null);
		OrganizationAssigmentBean[] beans = new OrganizationAssigmentBean[] { new OrganizationAssigmentBean() };
		mockMvc.perform(post(BASE_URL + "/assignLocationsAndPlans").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(beans))).andExpect(status().isInternalServerError());
		for (OrganizationAssigmentBean bean : beans)
			verify(organizationService).assignLocationAndPlan(bean.getOrganization(), bean.getJurisdiction(),
					bean.getPlan(), bean.getFromDate(), bean.getToDate());
		verifyNoMoreInteractions(organizationService);

	}

	@Test
	public void testGetAssignedLocationAndPlan() throws Exception {
		String identifier = UUID.randomUUID().toString();
		List<AssignedLocations> expected = getOrganizationLocationsAssigned();
		when(organizationService.findAssignedLocationsAndPlans(identifier,true, null, null,null,null)).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL + "/assignedLocationsAndPlans/{identifier}", identifier))
		        .andExpect(status().isOk()).andReturn();
		
		verify(organizationService).findAssignedLocationsAndPlans(identifier,true, null, null,null,null);
		verifyNoMoreInteractions(organizationService);
		assertEquals(objectMapper.writeValueAsString(expected), result.getResponse().getContentAsString());

	}

	@Test
	public void testGetAssignedLocationAndPlanWithMissingParams() throws Exception {
		String identifier = UUID.randomUUID().toString();
		doThrow(new IllegalArgumentException()).when(organizationService).findAssignedLocationsAndPlans(identifier,true, null, null,null,null);
		
		MvcResult result = mockMvc.perform(get(BASE_URL + "/assignedLocationsAndPlans/{identifier}", identifier))
		        .andExpect(status().isBadRequest()).andReturn();
		
		verify(organizationService).findAssignedLocationsAndPlans(identifier,true, null, null,null,null);
		verifyNoMoreInteractions(organizationService);
		assertEquals("", result.getResponse().getContentAsString());

	}

	@Test
	public void testGetAssignedLocationAndPlanWithInternalError() throws Exception {
		String identifier = UUID.randomUUID().toString();
		doThrow(new RuntimeException()).when(organizationService).findAssignedLocationsAndPlans(identifier,true, null, null,null,null);
		
		MvcResult result = mockMvc.perform(get(BASE_URL + "/assignedLocationsAndPlans/{identifier}", identifier))
		        .andExpect(status().isInternalServerError()).andReturn();
		
		verify(organizationService).findAssignedLocationsAndPlans(identifier,true, null, null,null,null);
		verifyNoMoreInteractions(organizationService);
		String responseString = result.getResponse().getContentAsString();
		if (responseString.isEmpty()) {
			fail("Test case failed");
		}
		JsonNode actualObj = objectMapper.readTree(responseString);
		assertEquals(actualObj.get("message").asText(), MESSAGE);

	}


	@Test
	public void testGetAssignedLocationsAndPlansByPlanId() throws Exception {
		String identifier = UUID.randomUUID().toString();
		List<AssignedLocations> expected = getOrganizationLocationsAssigned();
		when(organizationService.findAssignedLocationsAndPlansByPlanIdentifier(identifier, null, null,null,null)).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL + "/assignedLocationsAndPlans?plan=" + identifier))
		        .andExpect(status().isOk()).andReturn();
		
		verify(organizationService).findAssignedLocationsAndPlansByPlanIdentifier(identifier, null, null,null,null);
		verifyNoMoreInteractions(organizationService);
		assertEquals(objectMapper.writeValueAsString(expected), result.getResponse().getContentAsString());

	}

	@Test
	public void testGetOrgPractitioners() throws Exception {
		String identifier = UUID.randomUUID().toString();
		List<Practitioner> expected = new ArrayList<>();
		expected.add(getPractioner());
		when(practitionerService.getPractitionersByOrgIdentifier(any(String.class))).thenReturn(expected);
		MvcResult result = mockMvc.perform(get(BASE_URL + "/practitioner/{identifier}", identifier))
				.andExpect(status().isOk()).andReturn();

		String responseString = result.getResponse().getContentAsString();
		if (responseString.isEmpty()) {
			fail("Test case failed");
		}
		JsonNode actualObj = objectMapper.readTree(responseString);
		assertEquals(actualObj.get(0).get("identifier").asText(), expected.get(0).getIdentifier());
		assertEquals(actualObj.get(0).get("active").asBoolean(), expected.get(0).getActive());
		assertEquals(actualObj.size(),expected.size());
	}

	@Test
	public void testGetOrgPractitionersWithInternalError() throws Exception {
		String identifier = UUID.randomUUID().toString();
		doThrow(new IllegalStateException()).when(practitionerService).getPractitionersByOrgIdentifier(any(String.class));

		MvcResult result = mockMvc.perform(get(BASE_URL + "/practitioner/{identifier}", identifier))
				.andExpect(status().isInternalServerError()).andReturn();

		verify(practitionerService).getPractitionersByOrgIdentifier(identifier);
		verifyNoMoreInteractions(practitionerService);
		String responseString = result.getResponse().getContentAsString();
		if (responseString.isEmpty()) {
			fail("Test case failed");
		}
		JsonNode actualObj = objectMapper.readTree(responseString);
		assertEquals(actualObj.get("message").asText(), MESSAGE);
	}

	private Organization getOrganization() throws Exception {
		return objectMapper.readValue(organizationJSON, Organization.class);
	}

	private OrganizationAssigmentBean[] getOrganizationAssignment() {
		List<OrganizationAssigmentBean> organizationAssigmentBeans = new ArrayList<>();
		Random random = new Random();
		for (int i = 0; i < 5; i++) {
			OrganizationAssigmentBean bean = new OrganizationAssigmentBean();
			bean.setOrganization("org" + i);
			bean.setJurisdiction("loc" + i);
			bean.setPlan("plan" + i);
			if (random.nextBoolean())
				bean.setFromDate(new Date());
			if (random.nextBoolean())
				bean.setToDate(new Date());

		}
		return organizationAssigmentBeans.toArray(new OrganizationAssigmentBean[] {});

	}

	private List<AssignedLocations> getOrganizationLocationsAssigned() {
		List<AssignedLocations> organizationAssigmentBeans = new ArrayList<>();
		Random random = new Random();
		for (int i = 0; i < 5; i++) {
			OrganizationAssigmentBean bean = new OrganizationAssigmentBean();
			bean.setOrganization("org" + i);
			bean.setJurisdiction("loc" + i);
			bean.setPlan("plan" + i);
			if (random.nextBoolean())
				bean.setFromDate(new Date());
			if (random.nextBoolean())
				bean.setToDate(new Date());

		}
		return organizationAssigmentBeans;

	}

	private Practitioner getPractioner() {
		Practitioner practitioner = new Practitioner();
		practitioner.setIdentifier("ID-123");
		practitioner.setActive(Boolean.TRUE);
		return practitioner;

	}

}
