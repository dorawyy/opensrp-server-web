/**
 * 
 */
package org.opensrp.web.config.security;

import java.text.DateFormat;

import org.joda.time.DateTime;
import org.opensrp.util.DateTimeDeserializer;
import org.opensrp.util.DateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * @author Samuel Githengi created on 03/18/20
 */
@Configuration
@EnableWebMvc
@ComponentScan
@EnableAsync
@EnableAspectJAutoProxy
public class WebConfig {
	
	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		objectMapper.setDateFormat(DateFormat.getDateTimeInstance());
		
		SimpleModule dateTimeModule = new SimpleModule("DateTimeModule");
		dateTimeModule.addDeserializer(DateTime.class, new DateTimeDeserializer());
		dateTimeModule.addSerializer(DateTime.class, new DateTimeSerializer());
		objectMapper.registerModule(dateTimeModule);
		return objectMapper;
	}
	
	@Bean(name = "mappingJackson2JsonView")
	public MappingJackson2JsonView mappingJackson2JsonView() {
		MappingJackson2JsonView jsonView = new MappingJackson2JsonView();
		jsonView.setObjectMapper(objectMapper());
		jsonView.setExtractValueFromSingleKeyModel(true);
		return jsonView;
	}
	
	@Bean(name = "mappingJackson2HttpMessageConverter")
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
		converter.setObjectMapper(objectMapper());
		return converter;
	}
	
}
