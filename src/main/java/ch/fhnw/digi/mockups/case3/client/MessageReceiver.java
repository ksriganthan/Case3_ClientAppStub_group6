package ch.fhnw.digi.mockups.case3.client;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import ch.fhnw.digi.mockups.case3.JobAssignmentMessage;
import ch.fhnw.digi.mockups.case3.JobMessage;
import ch.fhnw.digi.mockups.case3.JobRequestMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.stereotype.Component;

@Component
public class MessageReceiver {

	@Autowired
	private UI ui;

	@Value("${client.region:}")
	private String filterRegion;

	@Value("${client.jobType:}")
	private String filterJobType;

	@Value("${client.id:group6}")
	private String clientId;

	// Neue Aufträge vom Topic empfangen
	@JmsListener(destination = "group6.dispo.jobs.new", containerFactory = "myFactory")
	public void receiveJob(JobMessage job) {
		// Optionaler Region-Filter (leer = alle Regionen anzeigen)
		if (filterRegion != null && !filterRegion.isEmpty()
				&& !filterRegion.equalsIgnoreCase(job.getRegion())) {
			return;
		}
		//JobType-Filter (leer = alle Typen anzeigen)
		if (filterJobType != null && !filterJobType.isEmpty()
				&& !filterJobType.equalsIgnoreCase(job.getJobType())) {
			return;
		}
		ui.addJobToList(job);
	}

	// Zuweisungsantworten vom Topic empfangen
	@JmsListener(destination = "group6.dispo.jobs.assignments", containerFactory = "myFactory")
	public void receiveAssignment(JobAssignmentMessage assignment) {
		ui.assignJob(assignment);
	}
	
	
	@Bean
	public DefaultJmsListenerContainerFactory myFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();

		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(true);
		factory.setMessageConverter(jacksonJmsMessageConverter());


		/*
		// Durable Subscriber: Nachrichten werden gespeichert auch wenn Client offline
		factory.setSubscriptionDurable(true);
		factory.setClientId(clientId);
		factory.setSubscriptionShared(true); */

		return factory;
	}

	@Bean // Serialize message content to json/from using TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");

		// Mapping der Typ-IDs vom Sender auf lokale Klassen
		Map<String, Class<?>> typeIdMappings = new HashMap<>();

		typeIdMappings.put("ch.fhnw.digi.demo.JobMessage", JobMessage.class);
		typeIdMappings.put("ch.fhnw.digi.demo.JobAssignmentMessage", JobAssignmentMessage.class);
		typeIdMappings.put("ch.fhnw.digi.demo.JobRequestMessage", JobRequestMessage.class);
		converter.setTypeIdMappings(typeIdMappings);

		return converter;
	}

}
