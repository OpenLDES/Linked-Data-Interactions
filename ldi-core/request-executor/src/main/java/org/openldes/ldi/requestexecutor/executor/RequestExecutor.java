package org.openldes.ldi.requestexecutor.executor;

import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

/**
 * Custom interface to make it more manageable to deal with HttpRequests and HttpResponses
 */
public interface RequestExecutor {

	Response execute(Request request);

}
