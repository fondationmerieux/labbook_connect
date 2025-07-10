package labbook_connect.labbook_connect;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moandjiezana.toml.Toml;

import plugin.Analyzer;

public class AnalyzerLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(AnalyzerLoader.class);
	
	private static final String END_POINT_LAB27 = "/services/device/analyzer/lab27";
	private static final String END_POINT_LAB29 = "/services/device/analyzer/lab29";

	private List<Analyzer> analyzers = new ArrayList<Analyzer>();

	public List<Analyzer> loadAnalyzerClasses() {
		String directoryPath = "/storage/resource/connect/analyzer/plugin";
		
		logger.info("DEBUG: Checking for analyzer JARs in {}", directoryPath);

		Path directory = Paths.get(directoryPath);

		try {
			Files.walk(directory)
			.filter(Files::isRegularFile)
			.filter(file -> !file.getFileName().toString().startsWith("."))
			.forEach(file -> {
				logger.info("Found plugin JAR: {}", file.getFileName());
				loadPlugin(file);
			});
		} catch (IOException e) {
			logger.error("ERROR: Unable to scan plugin directory {}", directoryPath, e);
		}

		return analyzers;
	}
	
	@SuppressWarnings("deprecation")
	private void loadPlugin(Path file)
	{
		Path plugin_name = file.getFileName();
		
		URL url = null ;
		try {
			logger.info("DEBUG Init URL");
			url = new URL("file:" + file.toAbsolutePath().toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		try (URLClassLoader pluginClassLoader = new URLClassLoader(
	            new URL[]{url}, App.class.getClassLoader())) { //ClassLoader.getSystemClassLoader())) {

	        String pluginClassName = plugin_name.toString().replace(".jar", "");
	        String className = "plugin." + pluginClassName;

	        try {
	            logger.info("DEBUG className: " + className);
	            Class<?> loadedClass = pluginClassLoader.loadClass(className);
	            Object instance = loadedClass.getDeclaredConstructor().newInstance();

	            // Check if the class implements `Analyzer`.
	            if (Analyzer.class.isAssignableFrom(loadedClass)) {
	                logger.info("DEBUG: " + className + " implements Analyzer.");
	                Analyzer analyzer = (Analyzer) instance; // Only possible if classloaders are compatible.
	                analyzers.add(analyzer);
	                logger.info("DEBUG: Added analyzer " + analyzer.test());
	            } else {
	                logger.error(": " + className + " does not implement Analyzer.");
	            }
	        } catch (ClassNotFoundException e) {
	            logger.error("ERROR Analyzer class not found in JAR: {}", e.getMessage(), e);
	        } catch (ReflectiveOperationException e) {
	            logger.error("ERROR instantiating analyzer class: {}", e.getMessage(), e);
	        } catch (Exception e) {
	            logger.error("ERROR Unexpected loading analyzer plugin: {}", e.getMessage(), e);
	        }
		} catch (IOException e) {
			logger.info("ERROR loadPlugin :" + e);
		}
	}
	
	public int loadAnalyzers()
	{
		// LOAD all classes of analyzers jar plugins
		try {
			AnalyzerLoader analyzerLoader = new AnalyzerLoader();

			App.analyzers_classes = analyzerLoader.loadAnalyzerClasses();

			if ( !App.analyzers_classes.isEmpty() )
			{
				logger.info("DEBUG analyzers_classes is not empty");
				for (Analyzer analyzer : App.analyzers_classes) {
					logger.info("DEBUG analyzer.test() = " + analyzer.test());
				}
			}
			else
				logger.info("analyzers_classes is empty");
		} catch(Exception e) {
			logger.error("ERROR Load plugins :"+e.toString());
		}

		// LOAD analyzers settings and set corresponding analyzers_loaded
		String settingPath = "/storage/resource/connect/analyzer/setting";

		Path directory = Paths.get(settingPath);

		try {
		    Files.walk(directory)
		        .filter(Files::isRegularFile)
		        .filter(file -> !file.getFileName().toString().startsWith("."))
		        .forEach(file -> {
		            try {
		                logger.info("Processing settings file: {}", file.toAbsolutePath());
		                parse_setting(file);
		            } catch (Exception e) {
		                logger.error("ERROR processing settings file {}: {}", file, e.getMessage(), e);
		            }
		        });
		} catch (IOException e) {
		    logger.error("ERROR loading analyzer settings: {}", e.getMessage(), e);
		}

		int nb_analyzers_loaded = 0;

		if (App.analyzers_loaded != null)
			nb_analyzers_loaded = App.analyzers_loaded.size();

		return nb_analyzers_loaded;
	}

	private static void parse_setting(Path file)
	{
		logger.info("DEBUG parse setting file : " + file.getFileName());

		Toml setting = new Toml().read(file.toFile());

		String version     = setting.getString("version");
		String id_analyzer = setting.getString("analyzer.id") ;
		String plugin_name = setting.getString("analyzer.plugin") ;
		String url_lis     = setting.getString("analyzer.url_lis") ;
		String type_cnx    = setting.getString("analyzer.type_cnx") ;
		String type_msg    = setting.getString("analyzer.type_msg") ;
		String archive_msg = setting.getString("analyzer.archive_msg") ;
		String operation_mode = setting.getString("analyzer.operation_mode", "batch");
		String mode        = "";
		String ip_analyzer = "";
		int port_analyzer  = 0;

		logger.info("DEBUG version=" + version);
		logger.info("DEBUG id_analyzer=" + id_analyzer + " plugin_name=" + plugin_name);
		logger.info("DEBUG url_lis = " + url_lis);
		logger.info("DEBUG type_cnx = " + type_cnx);
		logger.info("DEBUG type_msg = " + type_msg);
		logger.info("DEBUG operation_mode = " + operation_mode);

		if (type_cnx.equals("socket") || type_cnx.equals("MLLP") || type_cnx.equals("socket_E1381")) {
			mode 		   = setting.getString("analyzer.socket.mode") ;
			ip_analyzer    = setting.getString("analyzer.socket.ip") ;
			port_analyzer  = setting.getLong("analyzer.socket.port").intValue() ;

			logger.info("DEBUG mode = " + mode);
			logger.info("DEBUG ip_analyzer = " + ip_analyzer);
			logger.info("DEBUG port_analyzer = " + port_analyzer);
		}

		if (id_analyzer != null && !id_analyzer.isEmpty() && 
				plugin_name != null && !plugin_name.isEmpty() && 
				!type_cnx.isEmpty() && !type_msg.isEmpty() &&
				( ((type_cnx.equals("socket") || type_cnx.equals("MLLP") || type_cnx.equals("socket_E1381")) && mode.equals("server") && port_analyzer > 0) ||
						((type_cnx.equals("socket")  || type_cnx.equals("MLLP") || type_cnx.equals("socket_E1381")) && mode.equals("client") && !ip_analyzer.isEmpty() && port_analyzer > 0) )
				)
		{
			logger.info("DEBUG id_analyzer, plugin_name ... are not empty");

			for (Analyzer analyzer : App.analyzers_classes) {
				logger.info("DEBUG analyzer.test()=" + analyzer.test());
				logger.info("DEBUG plugin_name=" + plugin_name);

				if ( analyzer.test().equals(plugin_name))
				{
					try {
						logger.info("DEBUG test() == plugin_name");

						// Check if `id_analyzer` is already loaded
						Analyzer existingAnalyzer = null;
						for (Analyzer loadedAnalyzer : App.analyzers_loaded) {
							if (loadedAnalyzer.getId_analyzer().equals(id_analyzer)) {
								existingAnalyzer = loadedAnalyzer;
								break;
							}
						}

						if (existingAnalyzer != null) {
							logger.info("DEBUG Updating existing analyzer " + plugin_name);
							existingAnalyzer.setVersion(version);
							existingAnalyzer.setUrl_upstream_lab27(url_lis + END_POINT_LAB27 + "/" + id_analyzer);
							existingAnalyzer.setUrl_upstream_lab29(url_lis + END_POINT_LAB29 + "/" + id_analyzer);
							existingAnalyzer.setType_cnx(type_cnx);
							existingAnalyzer.setType_msg(type_msg);
							existingAnalyzer.setArchive_msg(archive_msg);
							existingAnalyzer.setOperationMode(operation_mode);
							existingAnalyzer.setMode(mode);
							existingAnalyzer.setIp_analyzer(ip_analyzer);
							existingAnalyzer.setPort_analyzer(port_analyzer);
							
							logger.info("DEBUG: via parser type_cnx = " + type_cnx);
							logger.info("DEBUG: via parser type_mode = " + mode);
							
							if (!existingAnalyzer.isListening()) {
					            logger.info("DEBUG: Restarting listenDevice() for " + existingAnalyzer.getId_analyzer());
					            existingAnalyzer.listenDevice();
					        }
						} else {
							logger.info("DEBUG Creating new analyzer " + plugin_name);
							Analyzer newAnalyzer = analyzer.copy();
							newAnalyzer.setVersion(version);
							logger.info("DEBUG: Set version={} for analyzer {}", version, id_analyzer);
							newAnalyzer.setId_analyzer(id_analyzer);
							newAnalyzer.setUrl_upstream_lab27(url_lis + END_POINT_LAB27 + "/" + id_analyzer);
							newAnalyzer.setUrl_upstream_lab29(url_lis + END_POINT_LAB29 + "/" + id_analyzer);
							newAnalyzer.setType_cnx(type_cnx);
							newAnalyzer.setType_msg(type_msg);
							newAnalyzer.setArchive_msg(archive_msg);
							newAnalyzer.setOperationMode(operation_mode);
							newAnalyzer.setMode(mode);
							newAnalyzer.setIp_analyzer(ip_analyzer);
							newAnalyzer.setPort_analyzer(port_analyzer);

							// Creating associated directories
							createAnalyzerDirectories(id_analyzer);

							App.analyzers_loaded.add(newAnalyzer);
							newAnalyzer.listenDevice();

							logger.info("DEBUG " + newAnalyzer.test() + " with id=" + newAnalyzer.getId_analyzer() + " added to analyzers_loaded");
						}
					} catch (Exception e) {
						logger.error(" copy and add newAnalyzer :"+e.toString());
					}
				}
			}
		}
		else {
			logger.error(" lack of settings" );
		}
	}
	
	/**
	 * Creating directories for a specific analyzer
	 */
	private static void createAnalyzerDirectories(String id_analyzer) {
	    Path dir_analyzer = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer);
	    Path dir_mapping = Paths.get(dir_analyzer + "/mapping");
	    Path dir_lab27 = Paths.get(dir_analyzer + "/lab27");
	    Path dir_lab29 = Paths.get(dir_analyzer + "/lab29");
	    Path dir_archive27 = Paths.get(dir_analyzer + "/archive_lab27");
	    Path dir_archive28 = Paths.get(dir_analyzer + "/archive_lab28");
	    Path dir_archive29 = Paths.get(dir_analyzer + "/archive_lab29");

	    Set<PosixFilePermission> permissions = new HashSet<>();
	    permissions.add(PosixFilePermission.OWNER_READ);
	    permissions.add(PosixFilePermission.OWNER_WRITE);
	    permissions.add(PosixFilePermission.OWNER_EXECUTE);
	    permissions.add(PosixFilePermission.GROUP_READ);
	    permissions.add(PosixFilePermission.GROUP_WRITE);
	    permissions.add(PosixFilePermission.GROUP_EXECUTE);
	    permissions.add(PosixFilePermission.OTHERS_READ);
	    permissions.add(PosixFilePermission.OTHERS_WRITE);
	    permissions.add(PosixFilePermission.OTHERS_EXECUTE);

	    try {
	        createDirectoryWithPermissions(dir_analyzer, permissions);
	        createDirectoryWithPermissions(dir_mapping, permissions);
	        createDirectoryWithPermissions(dir_lab27, permissions);
	        createDirectoryWithPermissions(dir_lab29, permissions);
	        createDirectoryWithPermissions(dir_archive27, permissions);
	        createDirectoryWithPermissions(dir_archive28, permissions);
	        createDirectoryWithPermissions(dir_archive29, permissions);
	    } catch (IOException e) {
	        logger.error(" creating directories for " + id_analyzer + " : " + e);
	    }
	}
	
	/**
	 * Function to create a directory with specific permissions
	 */
	private static void createDirectoryWithPermissions(Path dir, Set<PosixFilePermission> permissions) throws IOException {
	    if (!Files.exists(dir)) {
	        Files.createDirectories(dir);
	        logger.info("DEBUG directory created : " + dir);
	        Files.setPosixFilePermissions(dir, permissions);
	    } else {
	        logger.info("DEBUG directory already exists : " + dir);
	    }
	}
}
