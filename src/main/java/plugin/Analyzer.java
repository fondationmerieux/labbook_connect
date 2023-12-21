package plugin;

/**
 * This interface defines the methods to be used when writing an analyzer class.
 */
public interface Analyzer {
	
	/**
	 * Variable to specify this analyzer's identifier
	 *
	 * The value is read from the configuration.
	 */
	public String id_analyzer = "";
	
	/**
	 * Variable to specify endpoint of upstream for lab27 transaction
	 *
	 * The value is read from the configuration.
	 */
	public String url_upstream_lab27 = "";
	
	/**
	 * Variable to specify endpoint of upstream for lab29 transaction
	 *
	 * The value is read from the configuration.
	 */
	public String url_upstream_lab29 = "";
	
	/**
	 * Return value of id_analyzer.
	 * 
	 * @return id_analyzer
	 */
	public String getId_analyzer();
	
	/**
	 * Defines the value of id_analyzer.
	 * 
	 * @param id_analyzer value for id_analyzer.
	 */
	public void setId_analyzer(String id_analyzer);
	
	/**
	 * Return value of url_upstream_lab27.
	 * 
	 * @return url_upstream_lab27
	 */
	public String getUrl_upstream_lab27();
	
	/**
	 * Defines the value of url_upstream_lab27.
	 * 
	 * @param url value for url_upstream_lab27.
	 */
	public void setUrl_upstream_lab27(String url);
	
	/**
	 * Return value of url_upstream_lab29.
	 * 
	 * @return url_upstream_lab29
	 */
	public String getUrl_upstream_lab29();
	
	/**
	 * Defines the value of url_upstream_lab29.
	 * 
	 * @param url value for url_upstream_lab29.
	 */
	public void setUrl_upstream_lab29(String url);
	
	/**
	 * This method creates an instance of this class
	 */
	public Analyzer copy();
	
	/**
	 * @return name of this plugin
	 */
	public String test();
	
	/**
	 * This method handles the Query for AWOS [LAB-27] IHE transaction.
	 * Some analyzers do not issue queries, in this case this method can remain empty.
	 * - waits for a query from the analyzer and converts it into an hl7 message of type OBP_Q11,
	 * - POST it over HTTP to url_upstream_lab27,
	 * - waits for the HTTP response containing the ack in hl7 format of type RSP_K11,
	 * - converts the RSP_K11 message into a message than can be understood by the analyzer,
	 * - sends the message to the analyzer.
	 *
	 * @return empty String
	 */
	public String lab27();

	/**
	 * This method handles the AWOS Broadcast [LAB-28] IHE transaction.
	 * - receives a string representing an HL7 OML_O33 message, coming from the payload of an HTTP POST request received on the lab28 url,
	 * - converts the HL7 message into a message than can be understood by the analyzer,
	 * - transmits the message to the analyzer and waits for the response,
	 * - converts the analyzer response into an hl7 message of type ORL_O34,
	 * - returns the message as a string, which is then transmitted as the payload of the HTTP POST response.
	 *
	 * @param str_OML_O33 HL7 String of type OML_O33.
	 * @return String orl_o34
	 */
	public String lab28(final String str_OML_O33);

	/**
	 * This method handles the AWOS Status Change [LAB-29] IHE transaction.
	 * - waits for a status change from the analyzer and converts it into an hl7 message of type OUL_R22,
	 * - POST it over HTTP to url_upstream_lab29,
	 * - waits for the HTTP response containing the ack in hl7 format of type ACK_R22,
	 * - converts the ACK_R22 message into a message than can be understood by the analyzer,
	 * - sends the message to the analyzer.
	 * 
	 * @return empty String
	 */
	public String lab29();
}
