package org.openldes.ldio.config;

import org.openldes.ldi.HttpSparqlOut;
import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldi.factory.DeleteFunctionBuilder;
import org.openldes.ldi.factory.InsertFunctionBuilder;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.skolemisation.EmptySkolemizer;
import org.openldes.ldi.skolemisation.Skolemizer;
import org.openldes.ldi.skolemisation.SkolemizerImpl;
import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldi.valueobjects.DeleteFunction;
import org.openldes.ldi.valueobjects.InsertFunction;
import org.openldes.ldi.valueobjects.SparqlQuery;
import org.openldes.ldio.LdioHttpSparqlOut;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.requestexecutor.LdioRequestExecutorSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioHttpSparqlOut.NAME;

@Configuration
public class LdioHttpSparqlOutAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(name = NAME)
	public LdioHttpSparqlOutConfigurator ldiHttpSparqlOutConfigurator() {
		return new LdioHttpSparqlOutConfigurator();
	}

	public static class LdioHttpSparqlOutConfigurator implements LdioOutputConfigurator {

		@Override
		public LdiOutput configure(ComponentProperties properties) {
			final LdioHttpSparqlOutProperties httpSparqlOutProperties = new LdioHttpSparqlOutProperties(properties);
			final DeleteFunction deleteFunction = createDeleteFunction(httpSparqlOutProperties);
			final InsertFunction insertFunction = httpSparqlOutProperties.getGraph()
					.map(InsertFunctionBuilder::withGraph)
					.orElseGet(InsertFunctionBuilder::create)
					.build();
			final Skolemizer skolemizer = httpSparqlOutProperties.getSkolemisationDomain()
					.map(SkolemisationTransformer::new)
					.map(skolemisationTransformer -> (Skolemizer) new SkolemizerImpl(skolemisationTransformer))
					.orElseGet(EmptySkolemizer::new);
			final RequestExecutor requestExecutor = new LdioRequestExecutorSupplier().getRequestExecutor(properties);

			final SparqlQuery sparqlQuery = new SparqlQuery(insertFunction, deleteFunction);
			final HttpSparqlOut httpSparqlOut = new HttpSparqlOut(httpSparqlOutProperties.getEndpoint(), sparqlQuery, skolemizer, requestExecutor);

			return new LdioHttpSparqlOut(httpSparqlOut);
		}

		private static DeleteFunction createDeleteFunction(LdioHttpSparqlOutProperties properties) {
			if (!properties.isReplacementEnabled()) {
				return DeleteFunctionBuilder.disabled();
			}
			return properties.getReplacementDeleteFunction()
					.map(DeleteFunction::ofQuery)
					.orElseGet(() -> properties.getGraph()
							.map(DeleteFunctionBuilder::withGraph)
							.orElseGet(DeleteFunctionBuilder::create)
							.withDepth(properties.getReplacementDepth())
					);
		}
	}
}
