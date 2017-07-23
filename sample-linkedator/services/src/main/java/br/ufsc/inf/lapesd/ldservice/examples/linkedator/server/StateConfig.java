package br.ufsc.inf.lapesd.ldservice.examples.linkedator.server;

import br.ufsc.inf.lapesd.ld_jaxrs.jena.JenaProviders;
import br.ufsc.inf.lapesd.ldservice.LDEndpoint;
import br.ufsc.inf.lapesd.ldservice.linkedator.LinkedatorAutoSetup;
import br.ufsc.inf.lapesd.ldservice.linkedator.properties.LinkedatorPathVariableProperty;
import br.ufsc.inf.lapesd.ldservice.model.Mapping;
import br.ufsc.inf.lapesd.ldservice.model.UriRewrite;
import br.ufsc.inf.lapesd.ldservice.model.impl.PathTemplateActivator;
import br.ufsc.inf.lapesd.ldservice.model.impl.RxActivator;
import br.ufsc.inf.lapesd.ldservice.model.impl.SPARQLSelector;
import br.ufsc.inf.lapesd.ldservice.model.properties.ResourceType;
import br.ufsc.inf.lapesd.ldservice.tabular.TabularRDFSource;
import br.ufsc.inf.lapesd.ldservice.tabular.TabularSelector;
import br.ufsc.inf.lapesd.ldservice.tabular.impl.TabularExplicitMapping;
import br.ufsc.inf.lapesd.ldservice.tabular.raw.impl.CSVInMemoryDataSource;
import br.ufsc.inf.lapesd.linkedator.jersey.LinkedadorApi;
import br.ufsc.inf.lapesd.linkedator.jersey.LinkedadorWriterInterceptor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class StateConfig extends ResourceConfig {
    private static final String STATE = "http://state.example.org/ns#";

    public StateConfig() throws IOException {
        Model statesModel = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getResourceAsStream("/states.ttl")) {
            RDFDataMgr.read(statesModel, in, Lang.TURTLE);
        }

        LDEndpoint endpoint = new LDEndpoint();
        endpoint.addMapping(new Mapping.Builder()
                .addSelector(new PathTemplateActivator("state/{uf}"),
                        SPARQLSelector.fromModel(statesModel).selectSingle(
                                "PREFIX so: <"+STATE+">\n" +
                                "SELECT ?s WHERE {\n" +
                                "  ?s a so:State; so:code \"${uf}\".\n" +
                                "}"))
                .build());
        register(endpoint);
        register(new LinkedadorWriterInterceptor(new LinkedadorApi().loadConfig()));
        JenaProviders.getProviders().forEach(this::register);
    }
}
