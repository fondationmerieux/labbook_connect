package labbook_connect.labbook_connect;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moandjiezana.toml.Toml;

import ch.qos.logback.classic.Level;
import labbook_connect.fhir.FhirServer;
import labbook_connect.fhir.LabBookClient;
import plugin.Analyzer;

/**
 * Class that launches the LabBook Connect module
 **/
public class App {
	
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	
	/** Current application version string. */
	public static final String VERSION  = "1.1.0";
	/** Numeric application version (used for comparisons). */
	public static final int NUM_VERSION = 10100;

	/** Connect settings, optional: without it the log level is info and the FHIR facade is not configured */
	public static final String SETTING_FILE = "/storage/resource/connect/connect.toml";

	/** Directory holding the settings of the analyzers, their plugins and their messages */
	public static final String STORAGE_ROOT = "/storage/resource/connect";

	/** Default LabBook services, used when the settings file says nothing */
	private static final String ENDPOINT_LAB27 = "/services/external/device/analyzer/lab27";
	private static final String ENDPOINT_LAB29 = "/services/external/device/analyzer/lab29";
	
	/** Loaded analyzer plugin classes available in the runtime (from plugin JARs). */
	public static List<Analyzer> analyzers_classes = new ArrayList<Analyzer>();
	/** Configured analyzer instances enabled via settings files. */
	public static List<Analyzer> analyzers_loaded = new ArrayList<Analyzer>();
	/** Client of the LabBook API used by the FHIR facade, settings read at startup. */
	public static LabBookClient labbook_client = new LabBookClient();
	
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

		setLogLevel();
		
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

		/** Since Jetty 12 the context no longer takes the server as a parameter */
		ServletContextHandler context = new ServletContextHandler("/");

		server.setHandler(context);
		
		ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        
        context.addServlet(servletHolder, "/*");

        /** FHIR facade: the longest path prefix wins over the Jersey catch-all */
        context.addServlet(new ServletHolder(new FhirServer(labbook_client)), "/fhir/*");

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
	 * Returns the LabBook URL of the settings file, empty when the file or the
	 * setting is missing. The analyzers fall back on their own url_lis then, which
	 * is how every laboratory worked before this file existed.
	 * @return The URL without its trailing slash, empty when none is set.
	 */
	public static String labBookUrl() {
		return setting("labbook.url", "");
	}

	/**
	 * Returns a setting of the file, the given default when the file or the setting
	 * is missing, trailing slashes removed.
	 * @param key Key of the setting, log.level for instance.
	 * @param byDefault Value to return when the setting is not set.
	 * @return The setting, or byDefault.
	 */
	private static String setting(String key, String byDefault) {
		File file = new File(SETTING_FILE);

		if (!file.exists()) {
			return byDefault;
		}

		String value = new Toml().read(file).getString(key, byDefault);

		if (value == null || value.trim().isEmpty()) {
			return byDefault;
		}

		value = value.trim();

		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

		return value;
	}

	/**
	 * Returns the LabBook service the analyzers of the LAB27 protocol answer to.
	 * @return Path of the service under the LabBook URL.
	 */
	public static String endpointLab27() {
		return setting("labbook.endpoint_lab27", ENDPOINT_LAB27);
	}

	/**
	 * Returns the LabBook service the analyzers of the LAB29 protocol answer to.
	 * @return Path of the service under the LabBook URL.
	 */
	public static String endpointLab29() {
		return setting("labbook.endpoint_lab29", ENDPOINT_LAB29);
	}

	/**
	 * Applies the log level of the settings file, info when the file or the setting is missing.
	 */
	private static void setLogLevel() {
		String level = "info";
		File file = new File(SETTING_FILE);

		if (file.exists()) {
			level = new Toml().read(file).getString("log.level", "info");
		}

		ch.qos.logback.classic.Logger root =
				(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.toLevel(level, Level.INFO));

		logger.info("Log level {}", root.getLevel());
	}

	/**
	 * Ensure required directories exist.
	 */
	private static void createRequiredDirectories() {
	    String root = STORAGE_ROOT;

	    String[] directories = {
	        root,
	        root + "/analyzer",
	        root + "/analyzer/mapping",
	        root + "/analyzer/plugin",
	        root + "/analyzer/setting"
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
