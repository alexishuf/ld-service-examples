package br.ufsc.inf.lapesd.ldservice.examples.linkedator.server;


import br.ufsc.inf.lapesd.ldservice.linkedator.LinkedatorAutoSetup;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ServiceApp {
    private HttpServer server;

    public HttpServer getServer() {
        return server;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServiceApp app = new ServiceApp();
        ResourceConfig config;
        if (args[0].equals("city"))
            config = new CityConfig();
        else
            config = new StateConfig();
        int port = Integer.parseInt(args[1]);
        app.start(config, port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> app.getServer().shutdownNow()));
        Thread.currentThread().join();
    }

    public void start(ResourceConfig configuration, int port) throws IOException {
        server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create("http://localhost:" + port + "/"), configuration, false);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));

        Model ontologies = ModelFactory.createDefaultModel();
        if (configuration instanceof CityConfig) {
            try (InputStream in = ServiceApp.class.getResourceAsStream("/city-ontology.ttl")) {
                RDFDataMgr.read(ontologies, in, Lang.TTL);
            }
        } else if (configuration instanceof StateConfig) {
            try (InputStream in = ServiceApp.class.getResourceAsStream("/states-ontology.ttl")) {
                RDFDataMgr.read(ontologies, in, Lang.TTL);
            }
        }
        LinkedatorAutoSetup.setup(port).withEndpoints(configuration).withOntology(ontologies)
                .setupAndRegister(configuration);

        server.start();
    }
}
