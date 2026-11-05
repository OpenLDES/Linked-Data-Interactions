package org.openldes.ldi;

import org.apache.jena.irix.IRIx;

/**
 * Tree spec constants
 */
public class Tree {
	private Tree() {
	}

	private static final String NAMESPACE = "https://w3id.org/tree#";
	/**
	 * Recommended prefix for the TREE namespace: "tree"
	 */
	public static final String PREFIX = "tree";

	public static final IRIx MEMBER = IRIx.create(NAMESPACE + "member");
}
