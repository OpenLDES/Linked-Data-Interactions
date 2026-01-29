package org.openldes.ldio;

import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.dto.ColumnsDTO;
import org.openldes.ldio.dto.DataModelDTO;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.repository.DbRepository;
import org.openldes.ldio.sparqlselect.SparqlSelectService;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdioRdbOut implements LdiOutput {
    public static final String NAME = "Ldio:LdioRdbOut";
    private final Logger log = LoggerFactory.getLogger(LdioRdbOut.class);
    private final ComponentProperties config;
    private final DbRepository dbRepository;
    private final SparqlSelectService sparqlSelectService;
    private final String tableName;
    private final String sparqlSelectQuery;
    private final Boolean ignoreDuplicateKeyException;
    private ColumnsDTO columns;

    public LdioRdbOut(ComponentProperties config, DbRepository dbRepository, SparqlSelectService sparqlSelectService, String tableName, String sparqlSelectQuery, Boolean ignoreDuplicateKeyException) {
        this.config = config;
        this.dbRepository = dbRepository;
        this.sparqlSelectService = sparqlSelectService;
        this.tableName = tableName;
        this.sparqlSelectQuery = sparqlSelectQuery;
        this.ignoreDuplicateKeyException = ignoreDuplicateKeyException;
    }

    @Override
    public void accept(Model model) {
        DataModelDTO dataModelDTO = new DataModelDTO(columns);
        dataModelDTO = sparqlSelectService.execute(model, sparqlSelectQuery, dataModelDTO);
        if (columns == null) {
            this.columns = dataModelDTO.getColumns();
        }
        dbRepository.execute(dataModelDTO);
    }

    @Override
    public String toString() {
        return "LdioRdbOut{" +
                "config=" + config +
                ", dbRepository=" + dbRepository +
                ", sparqlSelectService=" + sparqlSelectService +
                ", tableName='" + tableName + '\'' +
                ", sparqlSelectQuery='" + sparqlSelectQuery + '\'' +
                ", columns=" + columns +
                '}';
    }
}
