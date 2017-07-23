package br.ufsc.inf.lapesd.ldservice.examples.linkedator.testbench;

import br.ufsc.inf.lapesd.ld_jaxrs.jena.JenaProviders;
import br.ufsc.inf.lapesd.ldservice.examples.linkedator.server.CityConfig;
import br.ufsc.inf.lapesd.ldservice.examples.linkedator.server.ServiceApp;
import br.ufsc.inf.lapesd.ldservice.examples.linkedator.server.StateConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL2;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class App  {
    @Option(name = "--linkedator-api-jar", required = true)
    private String linkedatorApiJar;

    @Option(name = "--run-dir")
    private File runDir = Paths.get("run").toFile().getAbsoluteFile();

    private final static String serviceBase = "http://localhost:8092/";

    private File serviceDir;
    private File linkedatorApiDir;
    private File linkedatorApiLog;
    private Process linkedatorApiProcess;
    private final Property mainEntity = createProperty("http://schema.org/mainEntity");
    private final Property inState = createProperty("http://city.example.org/ns#inState");
    private ServiceApp cityApp, stateApp;

    public static void main(String[] args) throws Exception {
        App app = new App();
        new CmdLineParser(app).parseArgument(args);
        app.run();
    }

    private void run() throws Exception {
        setupDirs();
        try {
            startLinkedatorApi();
            startServices();
            runClient();
        } finally {
            if (linkedatorApiProcess != null) {
                linkedatorApiProcess.destroy();
                if (!linkedatorApiProcess.waitFor(10, TimeUnit.SECONDS))
                    linkedatorApiProcess.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }

            if (cityApp != null && cityApp.getServer() != null)
                cityApp.getServer().shutdownNow();
        }
    }

    private void startLinkedatorApi() throws IOException, InterruptedException {
        File ymlFile = new File(linkedatorApiDir, "application.yml");
        try (InputStream in = getClass().getResourceAsStream("/linkedator-api.yml");
             FileOutputStream out = new FileOutputStream(ymlFile)) {
            IOUtils.copy(in, out);
        }

        String sep = System.getProperty("file.separator");
        String java = System.getProperty("java.home") + sep + "bin" + sep + "java";
        linkedatorApiProcess = new ProcessBuilder().command(java,
//                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
                    "-jar", linkedatorApiJar)
                .directory(linkedatorApiDir)
//                .redirectOutput(linkedatorApiLog)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true).start();
        System.out.printf("Waiting 15s for linkedator-api to start...\n");
        Thread.sleep(5000);
        System.out.printf("Waiting 10s for linkedator-api to start...\n");
        Thread.sleep(5000);
        System.out.printf("Waiting  5s for linkedator-api to start...\n");
        Thread.sleep(5000);
    }

    private void startServices() throws IOException, InterruptedException {
        try (FileOutputStream out = new FileOutputStream(new File("linkedator.config"));
            InputStream in = getClass().getResourceAsStream("/linkedator-jersey.json")) {
            IOUtils.copy(in, out);
        }
        System.out.printf("Starting city service...\n");
        cityApp = new ServiceApp();
        cityApp.start(new CityConfig(), 8092);
        System.out.printf("Starting state service...\n");
        stateApp = new ServiceApp();
        stateApp.start(new StateConfig(), 8093);
        Thread.sleep(2000);
    }

    private void runClient() {
        Client client = ClientBuilder.newClient();
        JenaProviders.getProviders().forEach(client::register);
        Model cities = client.target(serviceBase + "cities/").request("text/turtle")
                .get(Model.class);
        List<Resource> list = cities.listObjectsOfProperty(mainEntity).toList()
                .get(0).as(RDFList.class).asJavaList().stream()
                .map(RDFNode::asResource).collect(Collectors.toList());
        boolean ok = false;
        for (int i = 0; !ok && i < list.size(); i++) {
            if (i % 50 == 0)
                System.out.printf("Fetched %d/%d\n", i, list.size());
            String uri = getURI(list.get(i));
            Model model = client.target(uri).request("text/turtle").get(Model.class);
            Resource city = model.listObjectsOfProperty(mainEntity).toList().get(0).asResource();
            if (ok = city.hasProperty(inState))
                System.out.printf("Generated link after %d requests!\n", i+1);
        }
        if (!ok)
            System.out.printf("Link was not generated after %d requests\n", list.size());
    }

    private String getURI(Resource resource) {
        if (resource.isURIResource()) return resource.getURI();
        return resource.listProperties(OWL2.sameAs).toList().stream()
                .filter(s -> s.getObject().isURIResource())
                .map(s -> s.getResource().getURI())
                .findFirst().orElse(null);
    }

    private void setupDirs() throws IOException {
        if (runDir.exists() && runDir.isFile() && !runDir.delete())
            throw new IOException("Failed to delete " + runDir);
        if (runDir.exists() && runDir.isDirectory())
            FileUtils.deleteDirectory(runDir);
        if (runDir.exists() && !runDir.isDirectory())
            throw new IllegalArgumentException("--run-dir exists but is not a dir");
        if (!runDir.exists() && !runDir.mkdirs())
            throw new IOException("Failed to mkdir --run-dir");

        serviceDir = new File(runDir, "service");
        if (!serviceDir.exists() && !serviceDir.mkdirs())
            throw new IOException("Failed to mkdir " + serviceDir);

        linkedatorApiDir = new File(runDir, "linkedator-api");
        if (!linkedatorApiDir.exists() && !linkedatorApiDir.mkdirs())
            throw new IOException("Failed to mkdir " + linkedatorApiDir);
        linkedatorApiLog = new File(linkedatorApiDir, "log");

        System.setProperty("user.dir", serviceDir.getAbsolutePath());
    }
}
