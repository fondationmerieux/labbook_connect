package labbook_connect.labbook_connect;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import plugin.Analyzer;

/**
 * Class that launches the LabBook Connect module
 **/
public class App {
	
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	
	/** Current application version string. */
	public static final String VERSION  = "1.0.14";
	/** Numeric application version (used for comparisons). */
	public static final int NUM_VERSION = 10014;
	
	/** Loaded analyzer plugin classes available in the runtime (from plugin JARs). */
	public static List<Analyzer> analyzers_classes = new ArrayList<Analyzer>();
	/** Configured analyzer instances enabled via settings files. */
	public static List<Analyzer> analyzers_loaded = new ArrayList<Analyzer>();
	
	/**
	 * Default constructor.
	 */
	public App() {
	    // default
	}

	/**
	 * Starts the embedded HTTP server and initializes analyzers.
	 * @param args Command-line arguments (unused).
	 */
	public static void main(String[] args) {
		Date currentTimestamp = new Date();
		logger.info("*** {} BEGIN App main version: {} ***", currentTimestamp, App.VERSION);
		
		createRequiredDirectories();
		
		AnalyzerLoader analyzerLoader = new AnalyzerLoader();
    	
		/** Load analyzers plugins */
    	analyzerLoader.loadAnalyzers();
		
		String host = "0.0.0.0";
		int port = 8080;
		
		InetSocketAddress serv_host = new InetSocketAddress(host, port);

		/** Set API REST */
		final ResourceConfig config = new ResourceConfig(MyResource.class);
		
		config.register(CORSFilter.class);

		/** INIT server */
		Server server = new Server(serv_host);
		
		server.setStopAtShutdown(true);

		ServletContextHandler context = new ServletContextHandler(server, "/");
		
		ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        
        context.addServlet(servletHolder, "/*");

        /** RUN server */
        try {
            logger.info("START server on {}:{}", host, port);
            server.start();
            server.join();
        } catch (InterruptedException e) {
            // Restore interrupted state
            Thread.currentThread().interrupt();
            logger.error("Server thread interrupted: ", e);
        } catch (Exception e) {
            logger.error("ERROR RUN server: ", e);
        } finally {
        	try {
                server.stop();
            } catch (Exception e) {
                logger.error("ERROR stopping server: ", e);
            }
            server.destroy();
        }
	}
	
	/**
	 * Ensure required directories exist.
	 */
	private static void createRequiredDirectories() {
	    String[] directories = {
	        "/storage/resource/connect",
	        "/storage/resource/connect/analyzer",
	        "/storage/resource/connect/analyzer/mapping",
	        "/storage/resource/connect/analyzer/plugin",
	        "/storage/resource/connect/analyzer/setting"
	    };

	    for (String dirPath : directories) {
	        File dir = new File(dirPath);
	        if (!dir.exists()) {
	            if (dir.mkdirs()) {
	                logger.info("Created directory: " + dirPath);
	            } else {
	                logger.error("Failed to create directory: " + dirPath);
	            }
	        } else {
	            logger.info("Directory already exists: " + dirPath);
	        }
	    }
	}
}
