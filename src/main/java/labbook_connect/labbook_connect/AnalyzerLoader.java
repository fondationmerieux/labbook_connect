package labbook_connect.labbook_connect;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moandjiezana.toml.Toml;

import plugin.Analyzer;

public class AnalyzerLoader {

	private static Logger logger = LoggerFactory.getLogger(AnalyzerLoader.class);
	
	private List<Analyzer> analyzers = new ArrayList<Analyzer>();

	public List<Analyzer> loadAnalyzerClasses() {
		logger.debug("DEBUG loadAnalyzer()");

		// Spécifiez le chemin du répertoire que vous souhaitez lister
		String directoryPath = "/storage/resource/connect/analyzer/plugin";

		// Utilisez l'API Path pour représenter le chemin du répertoire
		Path directory = Paths.get(directoryPath);

		try {
			// Parcourez les fichiers avec Files.walk()
			Files.walk(directory)
			.filter(Files::isRegularFile)  // Filtrez pour obtenir uniquement les fichiers (pas les répertoires)
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
		
		// Spécifiez le chemin du répertoire contenant les fichiers .class
		URL url = null ;
		try {
			logger.debug("DEBUG Init URL");
			url = new URL("file:/storage/resource/connect/analyzer/plugin/" + plugin_name);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		String plugin_class = plugin_name.toString();
		plugin_class = plugin_class.replace(".jar", "");

		// Créez un URLClassLoader avec le chemin spécifié
		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{url})) {
			logger.debug("DEBUG Init classLoader url=" + url);
			// Spécifiez le nom complet de la classe (y compris le package)
			String className = "";

			className = "plugin." + plugin_class;

			try {
				logger.debug("DEBUG className : " + className);
				// Chargez la classe
				Class<?> loadedClass = classLoader.loadClass(className);

				logger.debug("DEBUG Loaded className : " + className);

				// Instanciez la classe
				Object instance = loadedClass.getDeclaredConstructor().newInstance();

				logger.debug("DEBUG instance created of : " + instance.getClass().getSimpleName());

				if (plugin.Analyzer.class.isAssignableFrom(loadedClass)) {
					logger.debug("DEBUG " + className + " implémente l'interface Analyzer.");
				} else {
					logger.debug("DEBUG " + className + " n'implémente pas l'interface Analyzer.");
				}

				// Utilisez la classe comme nécessaire
				if (instance instanceof plugin.Analyzer) {
					logger.debug("DEBUG Is instance of Analyzer");
					Analyzer analyzer = (Analyzer) instance;
					logger.debug("DEBUG Try method test() = " + analyzer.test());

					analyzers.add((Analyzer) instance);
				}
			} catch (ClassNotFoundException | InstantiationException |
					IllegalAccessException | IllegalArgumentException |
					InvocationTargetException | NoSuchMethodException |
					SecurityException e) {
				logger.error("Error loading class " + className + ": " + e.getMessage());
			}
		} catch (IOException e) {
			logger.debug("DEBUG ERROR e :" + e);
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
    			logger.info("analyzers is not empty");
    			for (Analyzer analyzer : App.analyzers_classes) {
    				logger.debug("DEBUG analyzer.test() = " + analyzer.test());
    			}
    		}
    		else
    			logger.info("analyzers is empty");
    	} catch(Exception e) {
    		logger.error("ERROR Load plugins :"+e.toString());
    	}

    	// LOAD analyzers settings and set corresponding analyzers_loaded
    	String settingPath = "/storage/resource/connect/analyzer/setting";

    	Path directory = Paths.get(settingPath);

    	try {
    		Files.walk(directory)
    		.filter(Files::isRegularFile)
    		.forEach(file -> parse_setting(file));
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	
    	int nb_analyzers_loaded = 0;
    	
    	if (App.analyzers_loaded != null)
    		nb_analyzers_loaded = App.analyzers_loaded.size();
    	
    	return nb_analyzers_loaded;
	}
	
	private static void parse_setting(Path file)
	{
		logger.debug("DEBUG parse setting file : " + file.getFileName());
		
		Toml setting = new Toml().read(file.toFile());
		
		String id_analyzer = setting.getString("analyzer.id") ;
		String plugin_name = setting.getString("analyzer.plugin") ;
		
		if (!id_analyzer.isEmpty() && !plugin_name.isEmpty())
		{
			for (Analyzer analyzer : App.analyzers_classes) {
				if ( analyzer.test() == plugin_name)
				{
					Analyzer newAnalyzer = analyzer.copy();
					
					newAnalyzer.setId_analyzer(id_analyzer);
					
					App.analyzers_loaded.add(newAnalyzer);
					logger.debug("DEBUG " + newAnalyzer.test() + " with id=" + newAnalyzer.getId_analyzer() + " added to analyzers_loaded");
				}
			}
		}
	}
}
