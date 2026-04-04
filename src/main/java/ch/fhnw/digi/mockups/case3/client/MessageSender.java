package ch.fhnw.digi.mockups.case3.client;

import ch.fhnw.digi.mockups.case3.JobRequestMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import ch.fhnw.digi.mockups.case3.JobMessage;

@Component
public class MessageSender {


	@Autowired
	private JmsTemplate jmsTemplate;
	@Autowired
	private MessageConverter jacksonJmsMessageConverter;
	@Value("${client.id:group6}")
	private String clientId;
	@Value("${channel.queue.requestAssignment:group6.dispo.jobs.requestAssignment}")
	private String requestAssignmentQueue;

	void requestJobFromDispo(JobMessage job) {

		// JobRequestMessage erzeugen
		JobRequestMessage request = new JobRequestMessage();
		request.setJobId(job.getJobId());
		request.setClientId(clientId);

		// An Queue senden (nicht Topic!)
		jmsTemplate.setMessageConverter(jacksonJmsMessageConverter);
		jmsTemplate.setPubSubDomain(false);
		jmsTemplate.convertAndSend(requestAssignmentQueue, request);
	}

}
