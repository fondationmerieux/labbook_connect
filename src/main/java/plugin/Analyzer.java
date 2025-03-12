package plugin;

/**
 * This interface defines the methods to be implemented for an analyzer.
 */
public interface Analyzer {
    
    /**
     * Gets the analyzer identifier.
     * @return The analyzer ID.
     */
    String getId_analyzer();

    /**
     * Sets the analyzer identifier.
     * @param id_analyzer The analyzer ID.
     */
    void setId_analyzer(String id_analyzer);

    /**
     * Gets the upstream URL for lab27 transactions.
     * @return The lab27 upstream URL.
     */
    String getUrl_upstream_lab27();

    /**
     * Sets the upstream URL for lab27 transactions.
     * @param url The lab27 upstream URL.
     */
    void setUrl_upstream_lab27(String url);

    /**
     * Gets the upstream URL for lab29 transactions.
     * @return The lab29 upstream URL.
     */
    String getUrl_upstream_lab29();

    /**
     * Sets the upstream URL for lab29 transactions.
     * @param url The lab29 upstream URL.
     */
    void setUrl_upstream_lab29(String url);

    /**
     * Sets the analyzer version.
     * @param version The version string.
     */
    void setVersion(String version);

    /**
     * Sets the connection type (e.g., "socket").
     * @param type_cnx The connection type.
     */
    void setType_cnx(String type_cnx);

    /**
     * Sets the message type (e.g., "HL7").
     * @param type_msg The message type.
     */
    void setType_msg(String type_msg);

    /**
     * Sets whether messages should be archived.
     * @param archive_msg "Y" to enable archiving, "N" to disable.
     */
    void setArchive_msg(String archive_msg);
    
    /**
     * Sets the operating mode of the analyzer (batch or query).
     * @param operation_mode The operating mode.
     */
    void setOperationMode(String operation_mode);

    /**
     * Sets the operating mode (e.g., "client" or "server").
     * @param mode The operating mode.
     */
    void setMode(String mode);

    /**
     * Sets the analyzer's IP address.
     * @param ip_analyzer The IP address.
     */
    void setIp_analyzer(String ip_analyzer);

    /**
     * Sets the analyzer's connection port.
     * @param port_analyzer The port number.
     */
    void setPort_analyzer(int port_analyzer);

    /**
     * Creates a new instance of this class.
     * @return A copy of the current analyzer.
     */
    Analyzer copy();

    /**
     * Returns the name of this plugin.
     * @return The plugin name.
     */
    String test();
    
    /**
     * Returns detailed information about the analyzer.
     *
     * @return String containing version, id_analyzer, connection details, and configuration.
     */
    String info();

    /**
     * Handles the Query for AWOS [LAB-27] IHE transaction.
     * Some analyzers do not issue queries; in this case, this method can remain empty.
     * - Waits for a query from the analyzer and converts it into an HL7 message (OBP_Q11).
     * - Sends it over HTTP to url_upstream_lab27.
     * - Waits for the HTTP response (RSP_K11).
     * - Converts the RSP_K11 message into a format understood by the analyzer.
     * - Sends the message to the analyzer.
     *
     * @param msg The HL7 message.
     * @return The response from the LIS.
     */
    String lab27(final String msg);

    /**
     * Handles the AWOS Broadcast [LAB-28] IHE transaction.
     * - Receives an HL7 OML_O33 message from an HTTP POST request.
     * - Converts the HL7 message into a format understood by the analyzer.
     * - Sends it to the analyzer and waits for a response.
     * - Converts the response into an HL7 ORL_O34 message.
     * - Returns the ORL_O34 message as a string.
     *
     * @param str_OML_O33 The HL7 OML_O33 message.
     * @return The ORL_O34 response message.
     */
    String lab28(final String str_OML_O33);

    /**
     * Handles the AWOS Status Change [LAB-29] IHE transaction.
     * - Waits for a status change from the analyzer and converts it into an HL7 OUL_R22 message.
     * - Sends it over HTTP to url_upstream_lab29.
     * - Waits for an HL7 ACK_R22 response.
     * - Converts the ACK_R22 message into a format understood by the analyzer.
     * - Sends the message to the analyzer.
     *
     * @param msg The HL7 message.
     * @return The LIS response.
     */
    String lab29(final String msg);

    /**
     * Starts listening for messages from the analyzer.
     * The behavior depends on the type of connection (e.g., "socket").
     */
    void listenDevice();

    /**
     * Checks if the analyzer is currently listening for incoming messages.
     * @return True if the analyzer is listening, false otherwise.
     */
    boolean isListening();
}
