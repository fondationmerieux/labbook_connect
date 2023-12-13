package labbook_connect.labbook_connect;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import plugin.Analyzer;

/**
 * Root resource (exposed at "connect" path)
 */
@Path("/connect")
public class MyResource {
	
	public static final String APPLICATION_HL7_V2 = "application/hl7-v2";

    public static final MediaType APPLICATION_HL7_V2_TYPE = new MediaType("application", "hl7-v2");
	
	private static Logger logger = LoggerFactory.getLogger(MyResource.class);

    @GET
    @Path("test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
    	logger.info("WS test");
        return App.VERSION;
    }
    
    @GET
    @Path("load_analyzers")
    @Produces(MediaType.TEXT_PLAIN)
    public int load_analyzers() {
    	logger.info("WS load_analyzers");
    	
    	int nb_analyzers_loaded = 0;
    	
    	AnalyzerLoader analyzerLoader = new AnalyzerLoader();
    	
    	nb_analyzers_loaded = analyzerLoader.loadAnalyzers();
    	
        return nb_analyzers_loaded;
    }
    
    @GET
    @Path("list_analyzers_classes")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_classes() {
    	logger.info("WS list_analyzers_classes");
    	
    	String ret = "";
    	
    	for (Analyzer analyzer : App.analyzers_classes) {
    		ret += analyzer.test() + "\n" ;    		
    	}
    	
    	logger.debug("list_analyzers_classes = " + ret);
    	
        return ret;
    }
    
    @GET
    @Path("list_analyzers_loaded")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_loaded() {
    	logger.info("WS list_analyzers_loaded");
    	
    	String ret = "";
    	
    	for (Analyzer analyzer : App.analyzers_loaded) {
    		ret += analyzer.test() + "\n" ;    		
    	}
    	
    	logger.debug("list_analyzers_loaded = " + ret);
    	
        return ret;
    }
    
    @POST
    @Path("lab27")
    @Produces(MediaType.TEXT_PLAIN)
    public Response lab27(@PathParam("id_analyzer") String id_analyzer, String msg_from_analyzer) {
    	logger.debug("DEBUG WS lab27 id_analyzer=" + id_analyzer + ", msg_from_analyzer : " + msg_from_analyzer);
    	
    	String ret = "";
    	
    	logger.debug("DEBUG WS lab27 before return");
    	
        return Response.ok(ret).build();
    }    
    
	@POST
    @Path("lab28")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response lab28(@PathParam("id_analyzer") String id_analyzer, String hl7_msg) {
    	logger.debug("DEBUG WS lab28 id_analyzer=" + id_analyzer + ", hl7_msg : " + hl7_msg);
    	
    	String ret = "";
    	
    	// give message to plugin
    	for (Analyzer analyzer : App.analyzers_loaded) {
    		if (id_analyzer == analyzer.getId_analyzer())
    		{
    		ret += analyzer.lab28(hl7_msg) ;
    		}
    	}
    	
    	logger.debug("DEBUG WS lab28 before return");
    	
        return Response.ok(ret).build();
    }
	
	@POST
    @Path("lab29")
    @Produces(MediaType.TEXT_PLAIN)
    public Response lab29(@PathParam("id_analyzer") String id_analyzer, String msg_from_analyzer) {
    	logger.debug("DEBUG WS lab29 id_analyzer=" + id_analyzer + ", msg_from_analyzer : " + msg_from_analyzer);
    	
    	String ret = "";
    	
    	logger.debug("DEBUG WS lab29 before return");
    	
        return Response.ok(ret).build();
    }
}
