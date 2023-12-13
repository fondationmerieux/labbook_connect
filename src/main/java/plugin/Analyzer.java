package plugin;

public interface Analyzer {
	public String id_analyzer = "";
	
	public String getId_analyzer();
	
	public void setId_analyzer(String id_analyzer);
	
	public Analyzer copy();
	
	public String test();
	
	public String lab27(final String msg_from_analyzer);

	public String lab28(final String OML_O33);

	public String lab29(final String msg_from_analyzer);
}
