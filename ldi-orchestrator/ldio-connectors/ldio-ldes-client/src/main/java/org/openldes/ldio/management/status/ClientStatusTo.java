package org.openldes.ldio.management.status;

import ldes.client.treenodesupplier.domain.valueobject.ClientStatus;

public record ClientStatusTo(String pipeline, ClientStatus status) {
}
