package org.openldes.ldio.pipeline.creation;

import org.openldes.ldio.pipeline.OrchestratorConfig;
import org.openldes.ldio.pipeline.PipelineConfig;
import org.openldes.ldio.pipeline.exception.LdiAdapterMissingException;
import org.openldes.ldio.pipeline.web.dto.ComponentDefinitionTO;
import org.openldes.ldio.pipeline.web.dto.InputComponentDefinitionTO;
import org.openldes.ldio.pipeline.web.dto.PipelineConfigTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ComponentScan("org.openldes.ldio")
@TestPropertySource(properties = "my.password=<PASSWORD>")
class PipelineCreatorServiceTest {
    private static final String PIPELINE_NAME = "pipeline-name";
    @Autowired
    private OrchestratorConfig orchestratorConfig;
    @Autowired
    private ConfigurableApplicationContext configurableApplicationContext;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    private PipelineCreatorService pipelineCreatorService;

    private static PipelineConfig getPipelineConfig(ComponentDefinitionTO adapterTO, Map<String, String> inputConfig) {
        final InputComponentDefinitionTO inputDefinitionTO = new InputComponentDefinitionTO("dummyIn", adapterTO, inputConfig);
        final PipelineConfigTO pipelineConfigTO = new PipelineConfigTO(PIPELINE_NAME, "", inputDefinitionTO, List.of(), List.of());
        return pipelineConfigTO.toPipelineConfig();
    }

    @BeforeEach
    void setUp() {
        pipelineCreatorService = new PipelineCreatorService(orchestratorConfig, configurableApplicationContext, eventPublisher, null);
    }

    @Test
    void given_InputConfigWithoutAdapter_when_InitialisePipeline_then_ThrowException() {
        final PipelineConfig pipelineConfig = getPipelineConfig(null, Map.of());

        assertThatThrownBy(() -> pipelineCreatorService.initialisePipeline(pipelineConfig))
                .isInstanceOf(LdiAdapterMissingException.class)
                .hasMessage("Pipeline \"%s\": Input: \"%s\": Missing LDI Adapter", PIPELINE_NAME, "dummyIn");
    }

    @Test
    void given_InputConfigWithAdapter_when_InitialisePipeline_then_ThrowNoException() {
        final ComponentDefinitionTO adapterTO = new ComponentDefinitionTO("dummyAdapt", Map.of());
        PipelineConfig pipelineConfig = getPipelineConfig(adapterTO, Map.of());

        assertThatNoException().isThrownBy(() -> pipelineCreatorService.initialisePipeline(pipelineConfig));
    }

    @Test
    void given_InputConfigWithExistingPropertyToResolve_when_InitialisePipeline_then_PropertyGetsResolved() {
        final ComponentDefinitionTO adapterTO = new ComponentDefinitionTO("dummyAdapt", Map.of());
        final PipelineConfig pipelineConfig = getPipelineConfig(adapterTO, Map.of("password", "${my.password}"));
        pipelineCreatorService.initialisePipeline(pipelineConfig);

        assertThat(pipelineConfig.getInput().getConfig().getConfig().get("password")).isEqualTo("<PASSWORD>");
    }

    @Test
    void given_InputConfigWithPropertyToResolveDefaultValue_when_InitialisePipeline_then_PropertyGetsResolved() {
        final ComponentDefinitionTO adapterTO = new ComponentDefinitionTO("dummyAdapt", Map.of());
        final PipelineConfig pipelineConfig = getPipelineConfig(adapterTO, Map.of("password", "${test:<DEFAULT_PASSWORD>}"));
        pipelineCreatorService.initialisePipeline(pipelineConfig);

        assertThat(pipelineConfig.getInput().getConfig().getConfig().get("password")).isEqualTo("<DEFAULT_PASSWORD>");
    }
}