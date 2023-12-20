package labbook_connect.labbook_connect;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
			url = new URL("file:/storage/resource/connect/analyzer/plugin/" + plugin_name);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		String plugin_class = plugin_name.toString();
		plugin_class = plugin_class.replace(".jar", "");

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{url})) {
			System.out.println("DEBUG Init classLoader url=" + url);
			// Specify full class name (including package)
			String className = "";

			className = "plugin." + plugin_class;

			try {
				System.out.println("DEBUG className : " + className);
				// load class
				Class<?> loadedClass = classLoader.loadClass(className);

				System.out.println("DEBUG Loaded className : " + className);

				// instance of class
				Object instance = loadedClass.getDeclaredConstructor().newInstance();

				System.out.println("DEBUG instance created of : " + instance.getClass().getSimpleName());

				if (plugin.Analyzer.class.isAssignableFrom(loadedClass)) {
					System.out.println("DEBUG " + className + " implements the Analyzer interface.");
				} else {
					System.out.println("DEBUG " + className + " does not implement the Analyzer interface.");
				}

				if (instance instanceof plugin.Analyzer) {
					System.out.println("DEBUG Is instance of Analyzer");
					Analyzer analyzer = (Analyzer) instance;
					System.out.println("DEBUG Try method test() = " + analyzer.test());

					analyzers.add((Analyzer) instance);
				}
			} catch (ClassNotFoundException | InstantiationException |
					IllegalAccessException | IllegalArgumentException |
					InvocationTargetException | NoSuchMethodException |
					SecurityException e) {
				System.out.println("ERROR loading class " + className + ": " + e.getMessage());
			}
		} catch (IOException e) {
			System.out.println("DEBUG ERROR e :" + e);
		}
	}
	
	public int loadAnalyzers()
	{
		// LOAD all analyzers jar plugins
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
    		.forEach(file -> parse_setting(file));
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
		
		System.out.println("DEBUG id_analyzer=" + id_analyzer + " plugin_name=" + plugin_name);
		System.out.println("DEBUG url_lis_lab27 = " + url_lab27);
		System.out.println("DEBUG url_lis_lab29 = " + url_lab29);
		
		if (!id_analyzer.isEmpty() && !plugin_name.isEmpty() && !url_lab29.isEmpty())
		{
			System.out.println("DEBUG id_analyzer, plugin_name and url_lab29 are not empty");
			
			for (Analyzer analyzer : App.analyzers_classes) {
				System.out.println("DEBUG analyzer.test()=" + analyzer.test());
				System.out.println("DEBUG plugin_name=" + plugin_name);
				
				if ( analyzer.test().equals(plugin_name))
				{
					try {
					System.out.println("DEBUG test() == plugin_name");
					
					// Create directories for this analyzer
					Path dir_analyzer  = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer);
					Path dir_mapping   = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/mapping");
					Path dir_lab27     = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/lab27");
					Path dir_lab29     = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/lab29");
					Path dir_archive27 = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/archive_lab27");
					Path dir_archive29 = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/archive_lab29");
					
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

					if (!Files.exists(dir_analyzer)) {
		                Files.createDirectories(dir_analyzer);
		                System.out.println("DEBUG directory created : " + dir_analyzer);
		                Files.setPosixFilePermissions(dir_analyzer, permissions);
		                
		                if (!Files.exists(dir_mapping)) {
			                Files.createDirectories(dir_mapping);
			                System.out.println("DEBUG directory created : " + dir_mapping);
			                Files.setPosixFilePermissions(dir_mapping, permissions);			                
		                }
		                
		                if (!Files.exists(dir_lab27)) {
			                Files.createDirectories(dir_lab27);
			                System.out.println("DEBUG directory created : " + dir_lab27);
			                Files.setPosixFilePermissions(dir_lab27, permissions);
		                }
		                
		                if (!Files.exists(dir_lab29)) {
			                Files.createDirectories(dir_lab29);
			                System.out.println("DEBUG directory created : " + dir_lab29);
			                Files.setPosixFilePermissions(dir_lab29, permissions);
		                }
		                
		                if (!Files.exists(dir_archive27)) {
			                Files.createDirectories(dir_archive27);
			                System.out.println("DEBUG directory created : " + dir_archive27);
			                Files.setPosixFilePermissions(dir_archive27, permissions);
		                }
		                
		                if (!Files.exists(dir_archive29)) {
			                Files.createDirectories(dir_archive29);
			                System.out.println("DEBUG directory created : " + dir_archive29);
			                Files.setPosixFilePermissions(dir_archive29, permissions);
		                }
		                
		            } else {
		                System.out.println("DEBUG directory already exists : " + dir_analyzer);
		            }
					
					Analyzer newAnalyzer = analyzer.copy();
					
					System.out.println("DEBUG newAnalyzer created");
					
					newAnalyzer.setId_analyzer(id_analyzer);
					newAnalyzer.setUrl_upstream_lab27(url_lab27);
					newAnalyzer.setUrl_upstream_lab29(url_lab29);
					
					App.analyzers_loaded.add(newAnalyzer);
					
					// Not all analyzers make lab27
					if (!url_lab27.isEmpty())
					{
					    Thread thread_lab27 = new Thread(() -> newAnalyzer.lab27());
					    thread_lab27.start();
					}
					
					Thread thread_lab29 = new Thread(() -> newAnalyzer.lab29());
					thread_lab29.start();
					
					System.out.println("DEBUG " + newAnalyzer.test() + " with id=" + newAnalyzer.getId_analyzer() + " added to analyzers_loaded");
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
}
