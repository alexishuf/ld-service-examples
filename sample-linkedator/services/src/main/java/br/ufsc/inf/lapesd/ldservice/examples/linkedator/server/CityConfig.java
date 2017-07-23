package br.ufsc.inf.lapesd.ldservice.examples.linkedator.server;

import br.ufsc.inf.lapesd.ld_jaxrs.jena.JenaProviders;
import br.ufsc.inf.lapesd.ldservice.LDEndpoint;
import br.ufsc.inf.lapesd.ldservice.linkedator.DescriptionGenerator;
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
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public class CityConfig extends ResourceConfig {
    private static final String CITY = "http://city.example.org/ns#";

    public CityConfig() throws IOException {
        InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/cidades.csv"), StandardCharsets.UTF_8);
        CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
        CSVInMemoryDataSource citiesRawSource = new CSVInMemoryDataSource(parser,
                Arrays.asList("uf", "name"));
        Property coState = createProperty(CITY + "state");
        Property coName = createProperty(CITY + "name");
        TabularExplicitMapping cityMapping = TabularExplicitMapping
                .builder("http://city.example.org/data#%d")
                .map("uf", coState)
                .map("name", coName)
                .withRule("[ (?x <" + CITY + "name> ?y) -> (?x rdf:type <" + CITY + "City>)]")
                .incomplete().build();
        TabularRDFSource citiesSource = new TabularRDFSource(citiesRawSource, cityMapping);

        Model statesModel = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getResourceAsStream("/states.ttl")) {
            RDFDataMgr.read(statesModel, in, Lang.TURTLE);
        }

        LDEndpoint endpoint = new LDEndpoint();
        endpoint.addMapping(new Mapping.Builder()
                .addRewrite(new UriRewrite(new RxActivator("http://city.example.org/data#(.*)"), "/city/${1}"))
                .addSelector(new PathTemplateActivator("cities"),
                        TabularSelector.from(citiesSource).selectList())
                .addSelector(new PathTemplateActivator("city/{row}"),
                        TabularSelector.from(citiesSource)
                                .withRow("row")
                                .selectSingle())
                .addSelector(new PathTemplateActivator("city/{uf}/{name}"),
                        TabularSelector.from(citiesSource)
                                .with("uf", coState)
                                .with("name", coName)
                                .selectSingle()
                                .addProperty(new LinkedatorPathVariableProperty("uf", coState.getURI()))
                                .addProperty(new LinkedatorPathVariableProperty("name", coName.getURI()))
                                .addProperty(new ResourceType(true, CITY+"City")))
                .build());
        register(endpoint);
        register(new LinkedadorWriterInterceptor(new LinkedadorApi().loadConfig()));
        JenaProviders.getProviders().forEach(this::register);
    }
}
