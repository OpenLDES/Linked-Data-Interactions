package org.openldes.ldi.converter;

import org.openldes.ldi.WktConverter;
import org.openldes.ldi.WktResult;
import org.apache.jena.rdf.model.*;

import java.util.List;

public class GeoJsonToWktConverter implements GeoJsonConverter {

    private final WktConverter wktConverter = new WktConverter();

    public List<Statement> createNewGeometryStatements(Statement oldStatement, Model geometryModel) {
        final WktResult wktResult = wktConverter.getWktFromModel(geometryModel);
        final Literal wktLiteral = wktConverter.getWktLiteral(wktResult);
        final Property geometryPredicate = ResourceFactory.createProperty("http://www.w3.org/ns/locn#geometry");
        return List.of(ResourceFactory.createStatement(oldStatement.getSubject(), geometryPredicate, wktLiteral));
    }

}
