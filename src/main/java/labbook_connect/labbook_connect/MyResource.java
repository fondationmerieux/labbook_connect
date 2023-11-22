package labbook_connect.labbook_connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("/connect")
public class MyResource {
	
	private static Logger logger = LoggerFactory.getLogger(MyResource.class);

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Path("test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
    	logger.error("WS test");
        return App.VERSION;
    }
    
}
