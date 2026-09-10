package org.openldes.ldio.config;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldio.LdioLdesClientPropertyKeys;
import org.openldes.ldio.management.status.ClientStatusService;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ldes.client.treenodesupplier.domain.valueobject.ClientStatus;
import org.apache.jena.rdf.model.Model;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openldes.ldio.LdioLdesClient.NAME;
import static org.openldes.ldio.LdioLdesClientPropertyKeys.URLS;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

public class LdioLdesClientITSteps extends LdesClientInIT {
	private static final WireMockServer wireMockServer = new WireMockServer(options().port(10101));
	private final String pipelineName = "pipelineName";
	private final ApplicationEventPublisher applicationEventPublisher = applicationEventPublisher();
	private final Map<String, String> componentPropsMap = new HashMap<>();
	private final List<Model> members = new ArrayList<>();
	private final ClientStatusService statusService = mock(ClientStatusService.class);

	@BeforeAll
	public static void before_all() {
		wireMockServer.start();
	}

	@Before
	public void before() {
		componentPropsMap.clear();
		members.clear();
		reset(statusService);
	}

	@Given("I want to follow the following LDES")
	public void iWantToFollowTheFollowingLDES(List<String> urls) {
		AtomicInteger counter = new AtomicInteger();

		urls.forEach(url -> {
			String key = "%s.%d".formatted(URLS, counter.getAndIncrement());
			componentPropsMap.put(key, wireMockServer.baseUrl() + url);
		});
	}

	@And("I configure this to be of RDF format {string}")
	public void iConfigureThisToBeOfRDFFormat(String contentType) {
		componentPropsMap.put(LdioLdesClientPropertyKeys.SOURCE_FORMAT, contentType);
	}

	@When("^I start an ldes-ldio-in component")
	public void iStartAnLdesLdioInComponentWithUrl() {
		members.clear();
		ComponentExecutor componentExecutor = members::add;

		var props = new ComponentProperties(pipelineName, NAME, componentPropsMap);
		var ldioInputConfigurator = new LdioLdesClientAutoConfig().ldioConfigurator(statusService, null);
		ldioInputConfigurator.configure(null, componentExecutor, applicationEventPublisher, props);
	}

	@Then("All {int} members from the stream are passed to the pipeline")
	public void allMembersFromTheStreamArePassedToThePipeline(int memberCount) {
		await().atMost(Duration.ofMinutes(2)).until(() -> members.size() == memberCount);
	}

	@And("I want to add the following properties")
	public void iWantToConfigureTheFollowingProperties(Map<String, String> properties) {
		this.componentPropsMap.putAll(properties);
	}

	@And("I expect {int} REPLICATING, {int} SYNCHRONISING and {int} COMPLETED updates")
	public void iExpectREPLICATINGSYNCHRONISINGAndCOMPLETEDUpdates(int replicatingTimes, int synchronisingTimes, int completedTimes) {
		verify(statusService, times(replicatingTimes)).updateStatus(pipelineName, ClientStatus.REPLICATING);
		verify(statusService, times(synchronisingTimes)).updateStatus(pipelineName, ClientStatus.SYNCHRONISING);
		verify(statusService, times(completedTimes)).updateStatus(pipelineName, ClientStatus.COMPLETED);
	}
}
