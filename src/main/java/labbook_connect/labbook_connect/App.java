package labbook_connect.labbook_connect;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import plugin.Analyzer;

/**
 * Class that launches the LabBook Connect module
 **/
public class App {
	
	public static final String VERSION  = "1.0.0";
	public static final int NUM_VERSION = 1000;
	
	public static List<Analyzer> analyzers_classes = new ArrayList<Analyzer>();
	public static List<Analyzer> analyzers_loaded = new ArrayList<Analyzer>();

	public static void main(String[] args) {
		Date currentTimestamp = new Date();
		System.out.println("*** " + currentTimestamp + " BEGIN App main version : " + App.VERSION + " ***" );
		
		AnalyzerLoader analyzerLoader = new AnalyzerLoader();
    	
    	analyzerLoader.loadAnalyzers();
		
		String host = "0.0.0.0";
		int port = 8080;
		
		InetSocketAddress serv_host = new InetSocketAddress(host, port);

		/** Set API REST */
		final ResourceConfig config = new ResourceConfig(MyResource.class);

		/** INIT server */
		Server server = new Server(serv_host);

		ServletContextHandler context = new ServletContextHandler(server, "/");
		
		ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        
        context.addServlet(servletHolder, "/*");

		/** RUN server */
		try {
			System.out.println("START server");
			server.start();
			server.join();
		} catch(Exception e) {
			System.out.println("ERROR RUN server :"+e.toString());
		} finally {
			server.destroy();
		}
	}
}
