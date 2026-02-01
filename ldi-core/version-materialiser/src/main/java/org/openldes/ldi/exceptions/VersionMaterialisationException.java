package org.openldes.ldi.exceptions;

public class VersionMaterialisationException extends RuntimeException {

	public VersionMaterialisationException(Exception e) {
		super(e);
	}

	@Override
	public String getMessage() {
		return "Couldn't apply version materialisation on FlowFile. " + this.getCause().getMessage();
	}

}
