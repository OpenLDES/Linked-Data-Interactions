package org.openldes.ldio;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.config.LdioAmqpInRegistrator;
import org.openldes.ldio.exceptions.InvalidAmqpMessageException;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.valueobjects.LdioAmpqInProperties;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;

import static org.openldes.ldio.config.AmqpConfig.CONTENT_TYPE_HEADER;

public class LdioAmqpIn extends LdioInput implements MessageListener {
	public static final String NAME = "Ldio:AmqpIn";
	private static final Logger log = LoggerFactory.getLogger(LdioAmqpIn.class);
	private final LdioAmqpInRegistrator ldioAmqpInRegistrator;
	private final LdioAmpqInProperties properties;
	private final String listenerId;

	/**
	 * Creates a LdiInput with its Component Executor and LDI Adapter
	 *
	 * @param executor            Instance of the Component Executor. Allows the LDI Input to pass
	 *                            data on the pipeline
	 * @param adapter             Instance of the LDI Adapter. Facilitates transforming the input
	 *                            data to a linked data model (RDF).
	 * @param ldioObserver		  Instance of the LDIO Observer, for observation, logging and monitoring reaons
	 * @param jmsInRegistrator    Global service to maintain JMS listeners.
	 * @param properties		  Configuration properties
	 */
	public LdioAmqpIn(ComponentExecutor executor, LdiAdapter adapter, LdioObserver ldioObserver,
					  LdioAmqpInRegistrator jmsInRegistrator, LdioAmpqInProperties properties,
					  ApplicationEventPublisher applicationEventPublisher) {
		super(executor, adapter, ldioObserver, applicationEventPublisher);
		this.properties = properties;
		SimpleJmsListenerEndpoint endpoint = listenerEndpoint(properties.jmsConfig().queue());
		ldioAmqpInRegistrator = jmsInRegistrator;
		listenerId = endpoint.getId();
		jmsInRegistrator.registerListener(properties.jmsConfig(), endpoint);
		this.start();
	}

	@Override
	public void onMessage(Message message) {
		final LdiAdapter.Content content;
		try {
			String contentTypeProperty = message.getStringProperty(CONTENT_TYPE_HEADER);
			String contentType = contentTypeProperty != null ? contentTypeProperty : properties.defaultContentType();
			content = LdiAdapter.Content.of(message.getBody(String.class), contentType);
		} catch (JMSException e) {
			throw new InvalidAmqpMessageException(e);
		}
		log.atDebug().log("Incoming jms message: {}", content);
		processInput(content);
	}

	private SimpleJmsListenerEndpoint listenerEndpoint(String queue) {
		SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
		endpoint.setId(properties.pipelineName());
		endpoint.setDestination(queue);
		endpoint.setMessageListener(this);
		return endpoint;
	}

	@Override
	public void shutdown() {
		ldioAmqpInRegistrator.stopListener(listenerId);
	}

	@Override
	protected void resume() {
		ldioAmqpInRegistrator.startListener(listenerId);
	}

	@Override
	protected void pause() {
		ldioAmqpInRegistrator.stopListener(listenerId);
	}
}
