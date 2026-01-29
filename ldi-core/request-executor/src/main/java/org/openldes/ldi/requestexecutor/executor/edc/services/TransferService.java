package org.openldes.ldi.requestexecutor.executor.edc.services;

import org.openldes.ldi.requestexecutor.valueobjects.Response;

public interface TransferService {

	/**
	 * Sets the transfer string for the request body and sends the request
	 *
	 * @param transfer string representation of the transfer
	 * @return the response from the transfer request
	 */
	Response startTransfer(String transfer);

	void refreshTransfer();

}
