package org.openldes.ldi.factory;

import org.openldes.ldi.valueobjects.InsertFunction;

public class InsertFunctionBuilder {
	private static final String BASE_TEMPLATE = "INSERT DATA { %s }";
	private final String query;

	private InsertFunctionBuilder(String query) {
		this.query = query;
	}

	public static InsertFunctionBuilder create() {
		return new InsertFunctionBuilder(BASE_TEMPLATE);
	}

	public static InsertFunctionBuilder withGraph(String graph) {
		final String graphBase = "GRAPH <%s> { %%s }".formatted(graph);
		return new InsertFunctionBuilder(String.format(BASE_TEMPLATE, graphBase));
	}

	public InsertFunction build() {
		return new InsertFunction(query);
	}
}
