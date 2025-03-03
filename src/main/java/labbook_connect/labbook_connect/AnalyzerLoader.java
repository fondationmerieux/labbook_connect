package labbook_connect.labbook_connect;

import java.io.IOException;
//import java.lang.reflect.InvocationTargetException;
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

import com.moandjiezana.toml.Toml;

import plugin.Analyzer;

public class AnalyzerLoader {

	private List<Analyzer> analyzers = new ArrayList<Analyzer>();

	public List<Analyzer> loadAnalyzerClasses() {
		System.out.println("DEBUG loadAnalyzer()");

		String directoryPath = "/storage/resource/connect/analyzer/plugin";

		Path directory = Paths.get(directoryPath);

		try {
			Files.walk(directory)
			.filter(Files::isRegularFile)
			.forEach(file -> loadPlugin(file));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return analyzers;
	}
	
	@SuppressWarnings("deprecation")
	private void loadPlugin(Path file)
	{
		Path plugin_name = file.getFileName();
		
		URL url = null ;
		try {
			System.out.println("DEBUG Init URL");
			url = new URL("file:" + file.toAbsolutePath().toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		try (URLClassLoader pluginClassLoader = new URLClassLoader(
	            new URL[]{url}, App.class.getClassLoader())) { //ClassLoader.getSystemClassLoader())) {

	        String pluginClassName = plugin_name.toString().replace(".jar", "");
	        String className = "plugin." + pluginClassName;

	        try {
	            System.out.println("DEBUG className: " + className);
	            Class<?> loadedClass = pluginClassLoader.loadClass(className);
	            Object instance = loadedClass.getDeclaredConstructor().newInstance();

	            // Check if the class implements `Analyzer`.
	            if (Analyzer.class.isAssignableFrom(loadedClass)) {
	                System.out.println("DEBUG: " + className + " implements Analyzer.");
	                Analyzer analyzer = (Analyzer) instance; // Only possible if classloaders are compatible.
	                analyzers.add(analyzer);
	                System.out.println("DEBUG: Added analyzer " + analyzer.test());
	            } else {
	                System.out.println("ERROR: " + className + " does not implement Analyzer.");
	            }
	        } catch (Exception e) {
	            System.out.println("ERROR loading class " + className + ": " + e.getMessage());
	            e.printStackTrace();
	        }
		} catch (IOException e) {
			System.out.println("DEBUG ERROR e :" + e);
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
				System.out.println("DEBUG analyzers_classes is not empty");
				for (Analyzer analyzer : App.analyzers_classes) {
					System.out.println("DEBUG analyzer.test() = " + analyzer.test());
				}
			}
			else
				System.out.println("analyzers_classes is empty");
		} catch(Exception e) {
			System.out.println("ERROR Load plugins :"+e.toString());
		}

		// LOAD analyzers settings and set corresponding analyzers_loaded
		String settingPath = "/storage/resource/connect/analyzer/setting";

		Path directory = Paths.get(settingPath);

		try {
			Files.walk(directory)
			.filter(Files::isRegularFile)
			.forEach(file -> {
				System.out.println("DEBUG: Processing settings file -> " + file.toAbsolutePath());
				parse_setting(file);	
			});
		} catch (IOException e) {
			System.out.println("ERROR parse_setting :"+e.toString());
		}

		int nb_analyzers_loaded = 0;

		if (App.analyzers_loaded != null)
			nb_analyzers_loaded = App.analyzers_loaded.size();

		return nb_analyzers_loaded;
	}

	private static void parse_setting(Path file)
	{
		System.out.println("DEBUG parse setting file : " + file.getFileName());

		Toml setting = new Toml().read(file.toFile());

		String id_analyzer = setting.getString("analyzer.id") ;
		String plugin_name = setting.getString("analyzer.plugin") ;
		String url_lab27   = setting.getString("analyzer.lab27") ;
		String url_lab29   = setting.getString("analyzer.lab29") ;
		String type_cnx    = setting.getString("analyzer.type_cnx") ;
		String type_msg    = setting.getString("analyzer.type_msg") ;
		String mode        = "";
		String ip_analyzer = "";
		int port_analyzer  = 0;

		System.out.println("DEBUG id_analyzer=" + id_analyzer + " plugin_name=" + plugin_name);
		System.out.println("DEBUG url_upstream_lab27 = " + url_lab27);
		System.out.println("DEBUG url_upstream_lab29 = " + url_lab29);
		System.out.println("DEBUG type_cnx = " + type_cnx);
		System.out.println("DEBUG type_msg = " + type_msg);

		if (type_cnx.equals("socket") || type_cnx.equals("MLLP")) {
			mode 		   = setting.getString("analyzer.socket.mode") ;
			ip_analyzer    = setting.getString("analyzer.socket.ip") ;
			port_analyzer  = setting.getLong("analyzer.socket.port").intValue() ;

			System.out.println("DEBUG mode = " + mode);
			System.out.println("DEBUG ip_analyzer = " + ip_analyzer);
			System.out.println("DEBUG port_analyzer = " + port_analyzer);
		}

		if (id_analyzer != null && !id_analyzer.isEmpty() && 
				plugin_name != null && !plugin_name.isEmpty() && 
				!type_cnx.isEmpty() && !type_msg.isEmpty() &&
				( ((type_cnx.equals("socket") || type_cnx.equals("MLLP")) && mode.equals("server") && port_analyzer > 0) ||
						((type_cnx.equals("socket")  || type_cnx.equals("MLLP")) && mode.equals("client") && !ip_analyzer.isEmpty() && port_analyzer > 0) )
				)
		{
			System.out.println("DEBUG id_analyzer, plugin_name ... are not empty");

			for (Analyzer analyzer : App.analyzers_classes) {
				System.out.println("DEBUG analyzer.test()=" + analyzer.test());
				System.out.println("DEBUG plugin_name=" + plugin_name);

				if ( analyzer.test().equals(plugin_name))
				{
					try {
						System.out.println("DEBUG test() == plugin_name");

						// Check if `id_analyzer` is already loaded
						Analyzer existingAnalyzer = null;
						for (Analyzer loadedAnalyzer : App.analyzers_loaded) {
							if (loadedAnalyzer.getId_analyzer().equals(id_analyzer)) {
								existingAnalyzer = loadedAnalyzer;
								break;
							}
						}

						if (existingAnalyzer != null) {
							System.out.println("DEBUG Updating existing analyzer " + plugin_name);
							existingAnalyzer.setUrl_upstream_lab27(url_lab27);
							existingAnalyzer.setUrl_upstream_lab29(url_lab29);
							existingAnalyzer.setType_cnx(type_cnx);
							existingAnalyzer.setType_msg(type_msg);
							existingAnalyzer.setMode(mode);
							existingAnalyzer.setIp_analyzer(ip_analyzer);
							existingAnalyzer.setPort_analyzer(port_analyzer);
							
							System.out.println("DEBUG: via parser type_cnx = " + type_cnx);
							System.out.println("DEBUG: via parser type_mode = " + mode);
							
							if (!existingAnalyzer.isListening()) {
					            System.out.println("DEBUG: Restarting listenDevice() for " + analyzer.getId_analyzer());
					            analyzer.listenDevice();
					        }
						} else {
							System.out.println("DEBUG Creating new analyzer " + plugin_name);
							Analyzer newAnalyzer = analyzer.copy();
							newAnalyzer.setId_analyzer(id_analyzer);
							newAnalyzer.setUrl_upstream_lab27(url_lab27);
							newAnalyzer.setUrl_upstream_lab29(url_lab29);
							newAnalyzer.setType_cnx(type_cnx);
							newAnalyzer.setType_msg(type_msg);
							newAnalyzer.setMode(mode);
							newAnalyzer.setIp_analyzer(ip_analyzer);
							newAnalyzer.setPort_analyzer(port_analyzer);

							// Creating associated directories
							createAnalyzerDirectories(id_analyzer);

							App.analyzers_loaded.add(newAnalyzer);
							newAnalyzer.listenDevice();

							System.out.println("DEBUG " + newAnalyzer.test() + " with id=" + newAnalyzer.getId_analyzer() + " added to analyzers_loaded");
						}
					} catch (Exception e) {
						System.out.println("ERROR copy and add newAnalyzer :"+e.toString());
					}
				}
			}
		}
		else {
			System.out.println("ERROR lack of settings" );
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
	        createDirectoryWithPermissions(dir_archive29, permissions);
	    } catch (IOException e) {
	        System.out.println("ERROR creating directories for " + id_analyzer + " : " + e);
	    }
	}
	
	/**
	 * Function to create a directory with specific permissions
	 */
	private static void createDirectoryWithPermissions(Path dir, Set<PosixFilePermission> permissions) throws IOException {
	    if (!Files.exists(dir)) {
	        Files.createDirectories(dir);
	        System.out.println("DEBUG directory created : " + dir);
	        Files.setPosixFilePermissions(dir, permissions);
	    } else {
	        System.out.println("DEBUG directory already exists : " + dir);
	    }
	}
}
