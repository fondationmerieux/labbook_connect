package labbook_connect.labbook_connect;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
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
import plugin.Analyzer;


/**
 * Class of access points 
 * Root resource (exposed at "connect" path)
 */
@Path("/connect")
public class MyResource {
	
	public static final String APPLICATION_HL7_V2 = "application/hl7-v2";

    public static final MediaType APPLICATION_HL7_V2_TYPE = new MediaType("application", "hl7-v2");
	
    /**
     * Access point to test 
     * 
     * @return version of this App
     */
    @GET
    @Path("test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
    	System.out.println("WS test");
        return App.VERSION;
    }
    
    /**
     * Checks if an analyzer is loaded by its ID.
     *
     * @param id_analyzer The ID of the analyzer to check.
     * @return A JSON response with status "OK" if loaded, or "ERR" with an error message.
     */
    @GET
    @Path("is_analyzer_loaded/{id_analyzer}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isAnalyzerLoaded(@PathParam("id_analyzer") String id_analyzer) {
        System.out.println("WS isAnalyzerLoaded id_analyzer=" + id_analyzer);

        for (Analyzer analyzer : App.analyzers_loaded) {
            if (id_analyzer.equals(analyzer.getId_analyzer())) {
                JsonObject response = Json.createObjectBuilder()
                    .add("status", "OK")
                    .add("message", "Analyzer is loaded.")
                    .build();
                return Response.ok(response.toString()).build();
            }
        }

        // If the analyzer is not found, return an error response
        JsonObject response = Json.createObjectBuilder()
            .add("status", "ERR")
            .add("message", "Analyzer with ID '" + id_analyzer + "' is not loaded.")
            .build();
        return Response.status(Response.Status.NOT_FOUND).entity(response.toString()).build();
    }
    
    /**
     * Access point to refresh loading analyzers.
     * 
     * @return count of analyzers loaded
     */
    @GET
    @Path("load_analyzers")
    @Produces(MediaType.TEXT_PLAIN)
    public int load_analyzers() {
    	System.out.println("WS load_analyzers");
    	
    	int nb_analyzers_loaded = 0;
    	
    	AnalyzerLoader analyzerLoader = new AnalyzerLoader();
    	
    	nb_analyzers_loaded = analyzerLoader.loadAnalyzers();
    	
        return nb_analyzers_loaded;
    }
    
    /**
     * Access point to list of possible analyzers.
     * 
     * @return plugin name for each possible analyzers
     */
    @GET
    @Path("list_analyzers_classes")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_classes() {
    	System.out.println("WS list_analyzers_classes");
    	
    	String ret = "";
    	
    	for (Analyzer analyzer : App.analyzers_classes) {
    		ret += analyzer.test() + "\n" ;    		
    	}
    	
    	System.out.println("list_analyzers_classes = " + ret);
    	
        return ret;
    }
    
    /**
     * Access point to list of loaded analyzers.
     * 
     * @return plugin name for each loaded analyzers.
     */
    @GET
    @Path("list_analyzers_loaded")
    @Produces(MediaType.TEXT_PLAIN)
    public String list_analyzers_loaded() {
    	System.out.println("WS list_analyzers_loaded");
    	
    	String ret = "";
    	
    	for (Analyzer analyzer : App.analyzers_loaded) {
    		ret += analyzer.test() + "\n" ;    		
    	}
    	
    	System.out.println("list_analyzers_loaded = " + ret);
    	
        return ret;
    }  
    
    /**
     * Access point to list of loaded analyzers.
     * 
     * @param id_analyzer value of id_analyzer targeted
     * @param oml_o33 HL7 string
     * @return plugin name for each loaded analyzers.
     */
    @POST
    @Path("lab28/{id_analyzer}")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response lab28(@PathParam("id_analyzer") String id_analyzer, String oml_o33) {
    	System.out.println("DEBUG WS lab28 id_analyzer=" + id_analyzer + ", oml_o33 : " + oml_o33);

    	String orl_o34 = "";

    	try {
    		// give message to plugin
    		for (Analyzer analyzer : App.analyzers_loaded) {
    			System.out.println("DEBUG analyzer.getId_analyzer() = " + analyzer.getId_analyzer());
    			if (id_analyzer.equals(analyzer.getId_analyzer()))
    			{
    				orl_o34 = analyzer.lab28(oml_o33) ;
    				break;
    			}
    		}

    		if (orl_o34.isEmpty()) {
    			System.out.println("ERROR WS lab28 - No analyzer found for id: " + id_analyzer);
    			return Response.status(Response.Status.NOT_FOUND)
    					.entity("Analyzer not found for id: " + id_analyzer)
    					.build();
    		}

    		System.out.println("DEBUG WS lab28 orl_o34 : " + orl_o34);

    		return Response.ok(orl_o34).build();
    	} catch (Exception e) {
    		System.err.println("ERROR WS lab28: " + e.getMessage());
    		return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
    				.entity("Internal Server Error")
    				.build();
    	}
    }
	
	/**
     * Access point to received message from analyzers.
     * 
     * @param msg_hl7 HL7 string
     * @return msg_ack.
     */
	@POST
    @Path("analyzer_msg")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response analyzer_msg(String str_hl7) {
    	String msg_ack = "";
    	
    	String id_analyzer = "";
    	
    	// GET id of analyzer from messsage received
    	try {
			System.out.println("DEBUG analyzer_msg hl7_received :\n" + str_hl7);
			Parser parser = new PipeParser();
			
			Message msg = parser.parse(str_hl7);
			
			MSH msh = (MSH) msg.get("MSH");

			id_analyzer = msh.getSendingFacility().getNamespaceID().getValue();
			
			System.out.println("DEBUG analyzer_msg id_analyzer : " + id_analyzer);
		} catch (Exception e) {
			System.out.println("ERROR analyzer_msg parse hl7 received : " + e);
		}

    	// Send message to analyzer for treatment
    	for (Analyzer analyzer : App.analyzers_loaded) {
    		System.out.println("DEBUG analyzer.getId_analyzer() = " + analyzer.getId_analyzer());
    		if (id_analyzer.equals(analyzer.getId_analyzer()))
    		{    		
    		msg_ack = analyzer.msg_received_from_analyzer(str_hl7) ;
    		}
    	}
    	
    	System.out.println("DEBUG WS analyzer_msg msg_ack : " + msg_ack);
    	
        return Response.ok(msg_ack).build();
    }
	
	/**
     * Access point to simulate upstream for lab27.
     * 
     * @param qbp_q11 HL7 string
     * @return Response ok with hl7 message in payload.
     */
	@POST
    @Path("test_lab27")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response test_lab27(String qbp_q11) {
    	System.out.println("DEBUG WS test_lab27 qbp_q11 : " + qbp_q11);
    	
    	return Response.ok(qbp_q11).build();
    }
	
	/**
     * Access point to simulate upstream for lab29.
     * 
     * @param oul_r22 HL7 string
     * @return Response ok with hl7 message in payload.
     */
	@POST
    @Path("test_lab29")
    @Consumes(APPLICATION_HL7_V2)
    @Produces(MediaType.TEXT_PLAIN)
    public Response test_lab29(String oul_r22) {
    	System.out.println("DEBUG WS test_lab29 oul_r22 : " + oul_r22);
    	
    	return Response.ok(oul_r22).build();
    }
}
