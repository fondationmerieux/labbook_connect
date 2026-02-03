package labbook_connect.labbook_connect;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugin.Analyzer;

/**
 * REST endpoints of LabBook Connect used for analyzer management and HL7 exchanges.
 */
@Path("/connect")
public class MyResource {

    private static final Logger logger = LoggerFactory.getLogger(MyResource.class);

    /** HL7 v2 payload media type (HAPI HL7 over HTTP). */
    public static final String APPLICATION_HL7_V2 = "application/hl7-v2";
    /** MediaType instance for HL7 v2 payloads. */
    public static final MediaType APPLICATION_HL7_V2_TYPE = new MediaType("application", "hl7-v2");
    
    /**
     * Default constructor.
     */
    public MyResource() {
        // default
    }

    /** Returns the current Connect version (diagnostic endpoint).
     * @return Version string.
     *  */
    @GET
    @Path("test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        logger.info("WS test called");
        return App.VERSION;
    }
    
    /** Returns information string for the specified analyzer.
     * @param id_analyzer Analyzer identifier.
     * @return Analyzer information string or an error string.
     */
    @GET
    @Path("info/{id_analyzer}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getAnalyzerInfo(@PathParam("id_analyzer") String id_analyzer) {
        logger.info("WS info called for id_analyzer={}", sanitizeForLog(id_analyzer));

        // Search for the corresponding analyzer in the list of loaded analyzers
        for (Analyzer analyzer : App.analyzers_loaded) {
            if (analyzer.getId_analyzer().equals(id_analyzer)) {
                return analyzer.info();
            }
        }

        return "ERROR: Analyzer with ID " + id_analyzer + " not found.";
    }

    /** Indicates whether the specified analyzer is currently loaded. 
     * @param id_analyzer Analyzer identifier.
     * @return JSON response indicating load status.
     * */
    @GET
    @Path("is_analyzer_loaded/{id_analyzer}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isAnalyzerLoaded(@PathParam("id_analyzer") String id_analyzer) {
        logger.info("WS isAnalyzerLoaded called with id_analyzer={}", sanitizeForLog(id_analyzer));

        for (Analyzer analyzer : App.analyzers_loaded) {
            if (id_analyzer.equals(analyzer.getId_analyzer())) {
                JsonObject response = Json.createObjectBuilder()
                        .add("status", "OK")
                        .add("message", "Analyzer is loaded.")
                        .build();
                return Response.ok(response.toString()).build();
            }
        }

        JsonObject response = Json.createObjectBuilder()
                .add("status", "ERR")
                .add("message", "Analyzer with ID '" + id_analyzer + "' is not loaded.")
                .build();
        return Response.status(Response.Status.NOT_FOUND).entity(response.toString()).build();
    }

    /**
     * Reloads analyzer plugins at runtime.
     * Intended for maintenance or diagnostic purposes only.
     * @return Number of analyzers loaded.
     */
    @GET
    @Path("load_analyzers")
    @Produces(MediaType.TEXT_PLAIN)
    public int load_analyzers() {
        logger.info("WS load_analyzers called");

        AnalyzerLoader analyzerLoader = new AnalyzerLoader();
        return analyzerLoader.loadAnalyzers();
    }

    /** Lists all available analyzer classes detected by Connect. 
     * @return One analyzer per line.
     * */
    @GET
    @Path("list_analyzers_classes")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_classes() {
        logger.info("WS list_analyzers_classes called");

        StringBuilder ret = new StringBuilder();
        for (Analyzer analyzer : App.analyzers_classes) {
            ret.append(analyzer.test()).append("\n");
        }

        logger.info("list_analyzers_classes = {}", ret);
        return ret.toString();
    }

    /** Lists all analyzer instances currently loaded in Connect. 
     * @return One analyzer per line.
     * */
    @GET
    @Path("list_analyzers_loaded")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_loaded() {
        logger.info("WS list_analyzers_loaded called");

        StringBuilder ret = new StringBuilder();
        for (Analyzer analyzer : App.analyzers_loaded) {
            ret.append(analyzer.test()).append("\n");
        }

        logger.info("list_analyzers_loaded = {}", ret);
        return ret.toString();
    }

    /**
     * Handles LAB-28 requests.
     * Expects a raw HL7 message encoded in UTF-8 and preserves segment delimiters (\r).
     * @param id_analyzer Analyzer identifier.
     * @param bodyStream Raw HL7 payload stream.
 	 * @return HL7 response payload.
     */
    @POST
    @Path("lab28/{id_analyzer}")
    @Consumes("text/plain")
    @Produces(MediaType.TEXT_PLAIN)
    public Response lab28(@PathParam("id_analyzer") String id_analyzer, InputStream bodyStream) {
        String oml_o33;

        try {
        	// Read raw HL7 message as bytes to preserve segment delimiters (\r)
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int read;
            while ((read = bodyStream.read(tmp)) != -1) {
                buffer.write(tmp, 0, read);
            }
            oml_o33 = buffer.toString(StandardCharsets.UTF_8);  // no line splitting, keeps \r intact

            logger.debug("DEBUG length of HL7 message = {}", oml_o33.length());
        } catch (IOException e) {
            logger.error("Error reading HL7 input stream", e);
            return Response.status(Response.Status.BAD_REQUEST).entity("HL7 input error").build();
        }
        
        logger.debug("WS lab28 called with id_analyzer={}, oml_o33: {}", sanitizeForLog(id_analyzer), oml_o33);

        String orl_o34 = "";

        try {
            for (Analyzer analyzer : App.analyzers_loaded) {
                logger.info("Checking analyzer.getId_analyzer() = {}", analyzer.getId_analyzer());
                if (id_analyzer.equals(analyzer.getId_analyzer())) {
                	logger.debug("Full raw HL7 message content:\n" + oml_o33.replace("\r", "\n"));
                	
                    orl_o34 = analyzer.lab28(oml_o33);
                    break;
                }
            }

            if (orl_o34.isEmpty()) {
                logger.error("WS lab28 - No analyzer found for id: {}", sanitizeForLog(id_analyzer));
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Analyzer not found for id: " + id_analyzer)
                        .build();
            }

            logger.debug("WS lab28 response orl_o34: {}", orl_o34);
            return Response.ok(orl_o34).build();

        } catch (Exception e) {
            logger.error("WS lab28 encountered an error: ", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal Server Error").build();
        }
    }

    /**
     * Endpoint intended for testing and troubleshooting HL7 messages.
     * @param qbp_q11 HL7 QBP^Q11 payload.
     * @return Echo response.
     */
    @POST
    @Path("test_lab27")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response test_lab27(String qbp_q11) {
        logger.info("WS test_lab27 called with qbp_q11: {}", qbp_q11);
        return Response.ok(qbp_q11).build();
    }

    /**
     * Endpoint intended for testing and troubleshooting HL7 messages.
     * @param oul_r22 HL7 OUL^R22 payload.
     * @return Echo response.
     */
    @POST
    @Path("test_lab29")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response test_lab29(String oul_r22) {
        logger.info("WS test_lab29 called with oul_r22: {}", oul_r22);
        return Response.ok(oul_r22).build();
    }
    
    /**
     * Sanitizes user-provided values before logging (prevents log injection and limits payload size).
     */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");

        int maxLen = 100;
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen) + "...";
        }

        return cleaned;
    }
}
