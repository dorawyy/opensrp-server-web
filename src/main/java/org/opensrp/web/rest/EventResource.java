package org.opensrp.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.opensrp.common.AllConstants.BaseEntity;
import org.smartregister.domain.Client;
import org.smartregister.domain.Event;
import org.opensrp.search.EventSearchBean;
import org.opensrp.service.ClientService;
import org.opensrp.service.EventService;
import org.smartregister.utils.DateTimeTypeConverter;
import org.opensrp.web.Constants;
import org.opensrp.web.bean.EventSyncBean;
import org.opensrp.web.bean.Identifier;
import org.opensrp.web.bean.SyncParam;
import org.opensrp.web.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensrp.common.AllConstants.BaseEntity.BASE_ENTITY_ID;
import static org.opensrp.common.AllConstants.BaseEntity.LAST_UPDATE;
import static org.opensrp.common.AllConstants.CLIENTS_FETCH_BATCH_SIZE;
import static org.opensrp.common.AllConstants.Event.ENTITY_TYPE;
import static org.opensrp.common.AllConstants.Event.EVENT_DATE;
import static org.opensrp.common.AllConstants.Event.EVENT_TYPE;
import static org.opensrp.common.AllConstants.Event.LOCATION_ID;
import static org.opensrp.common.AllConstants.Event.PROVIDER_ID;
import static org.opensrp.common.AllConstants.Event.TEAM;
import static org.opensrp.common.AllConstants.Event.TEAM_ID;
import static org.opensrp.common.AllConstants.Form.SERVER_VERSION;
import static org.opensrp.web.Constants.RETURN_COUNT;
import static org.opensrp.web.Constants.TOTAL_RECORDS;
import static org.opensrp.web.rest.RestUtils.getDateRangeFilter;
import static org.opensrp.web.rest.RestUtils.getIntegerFilter;
import static org.opensrp.web.rest.RestUtils.getStringFilter;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
@RequestMapping(value = "/rest/event")
public class EventResource extends RestResource<Event> {

	private static Logger logger = LogManager.getLogger(EventResource.class.toString());

	private EventService eventService;

	private ClientService clientService;

	Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
	        .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();

	@Value("#{opensrp['opensrp.sync.search.missing.client']}")
	private boolean searchMissingClients;

	private static final String IS_DELETED = "is_deleted";

	private static final String FALSE = "false";
	
	@Autowired
	public EventResource(ClientService clientService, EventService eventService) {
		this.clientService = clientService;
		this.eventService = eventService;
	}

	@Override
	public Event getByUniqueId(String uniqueId) {
		return eventService.find(uniqueId);
	}

	/**
	 * Get an event using the event id
	 *
	 * @param eventId the event id
	 * @return event with the event id
	 */
	@RequestMapping(value = "/findById", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	public Event getById(@RequestParam("id") String eventId) {
		return eventService.findById(eventId);
	}

	/**
	 * Fetch events ordered by serverVersion ascending order and return the clients associated with the
	 * events
	 *
	 * @param request
	 * @return a map response with events, clients and optionally msg when an error occurs
	 */
	@RequestMapping(value = "/sync", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	protected ResponseEntity<String> sync(HttpServletRequest request) throws JsonProcessingException {
		EventSyncBean response = new EventSyncBean();
		try {
			String providerId = getStringFilter(PROVIDER_ID, request);
			String locationId = getStringFilter(LOCATION_ID, request);
			String baseEntityId = getStringFilter(BASE_ENTITY_ID, request);
			String serverVersion = getStringFilter(BaseEntity.SERVER_VERSIOIN, request);
			String team = getStringFilter(TEAM, request);
			String teamId = getStringFilter(TEAM_ID, request);
			Integer limit = getIntegerFilter("limit", request);
			boolean returnCount = Boolean.getBoolean(getStringFilter(RETURN_COUNT, request));

			if (team != null || providerId != null || locationId != null || baseEntityId != null || teamId != null) {				
				
				EventSyncBean eventSyncBean = sync(providerId, locationId, baseEntityId, serverVersion, team, teamId, limit, returnCount);
				
				HttpHeaders headers = RestUtils.getJSONUTF8Headers();
				if (returnCount){
					headers.add(TOTAL_RECORDS, String.valueOf(eventSyncBean.getTotalRecords()));
				}
				
				return new ResponseEntity<>(
				        objectMapper.writeValueAsString(eventSyncBean), headers, HttpStatus.OK);
				
			} else {
				response.setMsg("specify atleast one filter");
				return new ResponseEntity<>(objectMapper.writeValueAsString(response), BAD_REQUEST);
			}

		}
		catch (Exception e) {
			response.setMsg("Error occurred");
			logger.error("", e);
			return new ResponseEntity<>(objectMapper.writeValueAsString(response), INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Fetch events ordered by serverVersion ascending order and return the clients associated with the
	 * events
	 *
	 * @param syncParam Parameters passed for sync
	 * @return a map response with events, clients and optionally msg when an error occurs
	 */
	@RequestMapping(value = "/sync", method = POST, produces = { MediaType.APPLICATION_JSON_VALUE })
	protected ResponseEntity<String> syncByPost(@RequestBody SyncParam syncParam) throws JsonProcessingException {
		EventSyncBean response = new EventSyncBean();
		try {

			if (syncParam.getTeam() != null || syncParam.getProviderId() != null || syncParam.getLocationId() != null
			        || syncParam.getBaseEntityId() != null || syncParam.getTeamId() != null) {
				
				EventSyncBean eventSyncBean = sync(syncParam.getProviderId(), syncParam.getLocationId(), syncParam.getBaseEntityId(),
			        syncParam.getServerVersion(), syncParam.getTeam(), syncParam.getTeamId(), syncParam.getLimit(), syncParam.isReturnCount());
				
				HttpHeaders headers = RestUtils.getJSONUTF8Headers();
				if (syncParam.isReturnCount()){
					headers.add(TOTAL_RECORDS, String.valueOf(eventSyncBean.getTotalRecords()));
				}
				
				return new ResponseEntity<>(
				        objectMapper.writeValueAsString(eventSyncBean), headers, HttpStatus.OK);
			} else {
				response.setMsg("specify atleast one filter");
				return new ResponseEntity<>(objectMapper.writeValueAsString(response), BAD_REQUEST);
			}

		}
		catch (Exception e) {			
			response.setMsg("Error occurred");
			logger.error("", e);
			return new ResponseEntity<>(objectMapper.writeValueAsString(response), INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Fetch clients and associated events allongside family registration events for the family that
	 * they attached to for the list of base entity ids passed
	 *
	 * @param jsonObject Json Object containing a jsonArray with baseEntityIds, and an optional boolean
	 *            named withFamilyEvents for obtaining family events if the value passed is true.
	 * @return a map response with events, clients and optionally msg when an error occurs
	 */
	@RequestMapping(value = "/sync-by-base-entity-ids", method = POST, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<String> syncClientsAndEventsByBaseEntityIds(@RequestBody String jsonObject)
	        throws JsonProcessingException {
		EventSyncBean combinedEventClients = new EventSyncBean();
		List<Event> combinedEvents = new ArrayList<>();
		List<Client> combinedClients = new ArrayList<>();
		try {
			JSONObject object = new JSONObject(jsonObject);
			boolean withFamilyEvents = object.optBoolean(Constants.WITH_FAMILY_EVENTS, false);
			List<String> baseEntityIdsList = gson.fromJson(object.getJSONArray(Constants.BASE_ENTITY_IDS).toString(),
			    new TypeToken<ArrayList<String>>() {}.getType());
			for (String baseEntityId : baseEntityIdsList) {
				EventSyncBean eventSyncBean = sync(null, null, baseEntityId, "0", null, null, null, false);
				combinedEvents.addAll(eventSyncBean.getEvents());
				combinedClients.addAll(eventSyncBean.getClients());
				if (eventSyncBean != null && eventSyncBean.getClients() != null && !eventSyncBean.getClients().isEmpty()) {
					List<Client> clients = eventSyncBean.getClients();
					//Obtaining family registration events for client's family if withFamilyEvents is true.
					if (clients.size() == 1 && clients.get(0).getRelationships().containsKey(Constants.FAMILY)
					        && withFamilyEvents) {
						List<String> clientRelationships = clients.get(0).getRelationships().get(Constants.FAMILY);
						for (String familyRelationship : clientRelationships) {
							EventSyncBean familyEvents = sync(null, null, familyRelationship, "0", null, null, null, false);
							combinedEvents.addAll(familyEvents.getEvents());
							combinedClients.addAll(familyEvents.getClients());
						}
					}
				}
			}
			combinedEventClients.setEvents(combinedEvents);
			combinedEventClients.setClients(combinedClients);
			combinedEventClients.setNoOfEvents(combinedEventClients.getEvents().size());
			
			return new ResponseEntity<>(objectMapper.writeValueAsString(combinedEventClients),
			        RestUtils.getJSONUTF8Headers(), HttpStatus.OK);
			
		}
		catch (Exception e) {
			EventSyncBean response = new EventSyncBean();
			response.setMsg("Error occurred: " + e.getLocalizedMessage());
			logger.error("", e);
			return new ResponseEntity<>(objectMapper.writeValueAsString(response), INTERNAL_SERVER_ERROR);
		}
	}
	
	public EventSyncBean sync(String providerId, String locationId, String baseEntityId, String serverVersion, String team,
	        String teamId, Integer limit, boolean returnCount) {
		Long lastSyncedServerVersion = null;
		if (serverVersion != null) {
			lastSyncedServerVersion = Long.parseLong(serverVersion) + 1;
		}

		EventSearchBean eventSearchBean = new EventSearchBean();
		eventSearchBean.setTeam(team);
		eventSearchBean.setTeamId(teamId);
		eventSearchBean.setProviderId(providerId);
		eventSearchBean.setLocationId(locationId);
		eventSearchBean.setBaseEntityId(baseEntityId);
		eventSearchBean.setServerVersion(lastSyncedServerVersion);

		return getEventsAndClients(eventSearchBean, limit == null || limit == 0 ? 25 : limit, returnCount);

	}
	
	private EventSyncBean getEventsAndClients(EventSearchBean eventSearchBean, Integer limit, boolean returnCount) {
		List<Event> events = new ArrayList<Event>();
		List<String> clientIds = new ArrayList<String>();
		List<Client> clients = new ArrayList<Client>();
		long startTime = System.currentTimeMillis();
		events = eventService.findEvents(eventSearchBean, BaseEntity.SERVER_VERSIOIN, "asc", limit == null ? 25 : limit);
		Long totalRecords = 0l;
		logger.info("fetching events took: " + (System.currentTimeMillis() - startTime));
		if (!events.isEmpty()) {
			for (Event event : events) {
				if (org.apache.commons.lang.StringUtils.isNotBlank(event.getBaseEntityId())
				        && !clientIds.contains(event.getBaseEntityId())) {
					clientIds.add(event.getBaseEntityId());
				}
			}
			for (int i = 0; i < clientIds.size(); i = i + CLIENTS_FETCH_BATCH_SIZE) {
				int end = Math.min(i + CLIENTS_FETCH_BATCH_SIZE, clientIds.size());
				clients.addAll(clientService.findByFieldValue(BASE_ENTITY_ID, clientIds.subList(i, end)));
			}
			logger.info("fetching clients took: " + (System.currentTimeMillis() - startTime));

			searchMissingClients(clientIds, clients, startTime);
			
			if (returnCount) {
				totalRecords = eventService.countEvents(eventSearchBean);
			}
			
		}

		
		EventSyncBean eventSyncBean = new EventSyncBean();
		eventSyncBean.setClients(clients);
		eventSyncBean.setEvents(events);
		eventSyncBean.setNoOfEvents(events.size());
		eventSyncBean.setTotalRecords(totalRecords);
		return eventSyncBean;
	}

	private void searchMissingClients(List<String> clientIds, List<Client> clients, long startTime) {
		if (searchMissingClients) {

			List<String> foundClientIds = new ArrayList<>();
			for (Client client : clients) {
				foundClientIds.add(client.getBaseEntityId());
			}

			boolean removed = clientIds.removeAll(foundClientIds);
			if (removed) {
				for (String clientId : clientIds) {
					Client client = clientService.getByBaseEntityId(clientId);
					if (client != null) {
						clients.add(client);
					}
				}
			}
			logger.info("fetching missing clients took: " + (System.currentTimeMillis() - startTime));
		}
	}

	/**
	 * Fetch events ordered by serverVersion ascending order and return the clients associated with the
	 * events
	 *
	 * @return a map response with events, clients and optionally msg when an error occurs
	 */
	@RequestMapping(value = "/getAll", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
	protected ResponseEntity<String> getAll(@RequestParam long serverVersion,
	        @RequestParam(required = false) String eventType, @RequestParam(required = false) Integer limit)
	        throws JsonProcessingException {
		
		try {
			EventSearchBean eventSearchBean = new EventSearchBean();
			eventSearchBean.setServerVersion(serverVersion > 0 ? serverVersion + 1 : serverVersion);
			eventSearchBean.setEventType(eventType);
			return new ResponseEntity<>(
			        objectMapper.writeValueAsString(getEventsAndClients(eventSearchBean, limit == null ? 25 : limit, false)),
			        RestUtils.getJSONUTF8Headers(), HttpStatus.OK);

		}
		catch (Exception e) {
			EventSyncBean response = new EventSyncBean();
			response.setMsg("Error occurred");
			logger.error("", e);
			return new ResponseEntity<>(objectMapper.writeValueAsString(response), INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(headers = { "Accept=application/json" }, method = POST, value = "/add")
	public ResponseEntity<String> save(@RequestBody String data, Authentication authentication) {
		String username = authentication.getName();
		List<String> failedClientsIds = new ArrayList<>();
		List<String> failedEventIds = new ArrayList<>();
		Map<String, Object> response = new HashMap<>();

		try {
			JSONObject syncData = new JSONObject(data);
			if (!syncData.has("clients") && !syncData.has("events")) {
				return new ResponseEntity<>(BAD_REQUEST);
			}

			if (syncData.has("clients")) {
				ArrayList<Client> clients = gson.fromJson(Utils.getStringFromJSON(syncData, "clients"),
						new TypeToken<ArrayList<Client>>() {

						}.getType());

				logger.info("[SYNC_INFO] {} Clients submitted by user {}", clients.size(), username);

				for (Client client : clients) {
					try {
						clientService.addorUpdate(client);
					}
					catch (Exception e) {
						logger.error("[SYNC_INFO] Client {} failed to sync", client.getBaseEntityId(), e);
						failedClientsIds.add(client.getBaseEntityId());
					}
				}
			}

			if (syncData.has("events")) {
				ArrayList<Event> events = gson.fromJson(Utils.getStringFromJSON(syncData, "events"),
						new TypeToken<ArrayList<Event>>() {

						}.getType());

				logger.info("[SYNC_INFO] {} Events submitted by user {}", events.size(), username);

				for (Event event : events) {
					try {
						event = eventService.processOutOfArea(event);
						eventService.addorUpdateEvent(event, username);

						logger.info("[SYNC_INFO] Event {} of type {} saved", event.getFormSubmissionId(), event.getEventType());
					}
					catch (Exception e) {
						logger.error("[SYNC_INFO] Event {} of type {} for client {} failed to sync", event.getFormSubmissionId(), event.getEventType(), event.getBaseEntityId(), e);
						failedEventIds.add(event.getFormSubmissionId());
					}
				}
			}
		}
		catch (Exception e) {
			logger.error("[SYNC_INFO] Sync data processing failed with exception: ", e);
			return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
		}

		logger.info("[SYNC_INFO] Number of Events NOT saved: {}", failedEventIds.size());
		logger.info("[SYNC_INFO] Number of Clients NOT saved: {}", failedClientsIds.size());

		if (failedClientsIds.isEmpty() && failedEventIds.isEmpty()) {
			return new ResponseEntity<>(CREATED);
		} else {
			JsonArray clientsArray = (JsonArray) gson.toJsonTree(failedClientsIds, new TypeToken<List<String>>() {

			}.getType());

			JsonArray eventsArray = (JsonArray) gson.toJsonTree(failedEventIds, new TypeToken<List<String>>() {

			}.getType());

			response.put("failed_events", eventsArray);
			response.put("failed_clients", clientsArray);
			return new ResponseEntity<>(gson.toJson(response), CREATED);
		}
	}

	@Override
	public Event create(Event o) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return eventService.addEvent(o, authentication.getName());
	}

	@Override
	public List<String> requiredProperties() {
		List<String> p = new ArrayList<>();
		p.add(BASE_ENTITY_ID);
		p.add(EVENT_TYPE);
		p.add(PROVIDER_ID);
		return p;
	}

	@Override
	public Event update(Event entity) {
		return eventService.mergeEvent(entity);
	}

	@Override
	public List<Event> search(HttpServletRequest request) throws ParseException {
		String clientId = getStringFilter("identifier", request);
		DateTime[] eventDate = getDateRangeFilter(EVENT_DATE, request);
		String eventType = getStringFilter(EVENT_TYPE, request);
		String location = getStringFilter(LOCATION_ID, request);
		String provider = getStringFilter(PROVIDER_ID, request);
		String entityType = getStringFilter(ENTITY_TYPE, request);
		DateTime[] lastEdit = getDateRangeFilter(LAST_UPDATE, request);
		String team = getStringFilter(TEAM, request);
		String teamId = getStringFilter(TEAM_ID, request);
		
		if (!StringUtils.isBlank(clientId)) {
			Client c = clientService.find(clientId);
			if (c == null) {
				return new ArrayList<>();
			}

			clientId = c.getBaseEntityId();
		}
		EventSearchBean eventSearchBean = new EventSearchBean();
		eventSearchBean.setBaseEntityId(clientId);
		eventSearchBean.setEventDateFrom(eventDate == null ? null : eventDate[0]);
		eventSearchBean.setEventDateTo(eventDate == null ? null : eventDate[1]);
		eventSearchBean.setEventType(eventType);
		eventSearchBean.setEntityType(entityType);
		eventSearchBean.setProviderId(provider);
		eventSearchBean.setLocationId(location);
		eventSearchBean.setLastEditFrom(lastEdit == null ? null : lastEdit[0]);
		eventSearchBean.setLastEditTo(lastEdit == null ? null : lastEdit[1]);
		eventSearchBean.setTeam(team);
		eventSearchBean.setTeamId(teamId);

		return eventService.findEventsBy(eventSearchBean);
	}

	@Override
	public List<Event> filter(String query) {
		return eventService.findEventsByDynamicQuery(query);
	}

	/**
	 * Fetch events ids filtered by eventType sorted by server version ascending
	 *
	 * @param eventType
	 * @return A list of event ids and last server version
	 */
	@RequestMapping(value = "/findIdsByEventType", method = RequestMethod.GET, produces = {
	        MediaType.APPLICATION_JSON_VALUE })
	protected ResponseEntity<Identifier> getAllIdsByEventType(
	        @RequestParam(value = EVENT_TYPE, required = false) String eventType,
	        @RequestParam(value = SERVER_VERSION) long serverVersion,
	        @RequestParam(value = IS_DELETED, defaultValue = FALSE, required = false) boolean isDeleted,
			@RequestParam(value = "fromDate", required = false) String fromDate,
			@RequestParam(value = "toDate", required = false) String toDate){

		try {

			Pair<List<String>, Long> eventIdsPair = eventService.findAllIdsByEventType(eventType, isDeleted, serverVersion,
			    Constants.DEFAULT_GET_ALL_IDS_LIMIT, Utils.getDateTimeFromString(fromDate), Utils.getDateTimeFromString(toDate));
			Identifier identifiers = new Identifier();
			identifiers.setIdentifiers(eventIdsPair.getLeft());
			identifiers.setLastServerVersion(eventIdsPair.getRight());
			return new ResponseEntity<>(identifiers, RestUtils.getJSONUTF8Headers(), HttpStatus.OK);

		}
		catch (Exception e) {
			logger.warn(e.getMessage(), e);
			return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
		}
	}

	public void setEventService(EventService eventService) {
		this.eventService = eventService;
	}

	public void setClientService(ClientService clientService) {
		this.clientService = clientService;
	}

}
