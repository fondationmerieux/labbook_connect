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
     * Creates a configured copy of this analyzer instance.
     * @return A copy of the current analyzer.
     */
    Analyzer copy();

    /**
     * Returns the plugin name identifier (used by settings key analyzer.plugin).
     * @return Plugin identifier string.
     */
    String test();

    /**
     * Returns a human-readable summary of the analyzer configuration and state.
     * @return Human-readable information string.
     */
    String info();

    /**
     * Handles the IHE LAB-27 query transaction.
     * Typical flow (may vary depending on analyzer implementation):
     * - Receives or builds a query and converts it into HL7 QBP^Q11.
     * - Sends it to the upstream LIS endpoint (url_upstream_lab27).
     * - Receives an HL7 RSP^K11 response.
     * - Converts the response into the analyzer-specific format and sends it to the analyzer.
     *
     * @param msg HL7 message payload.
     * @return HL7 response message.
     */
    String lab27(final String msg);

    /**
     * Handles the IHE LAB-28 order broadcast transaction.
     * Typical flow:
     * - Receives an HL7 OML^O33 message from upstream.
     * - Converts it into the analyzer-specific format and sends it to the analyzer.
     * - Receives analyzer response and converts it to HL7 ORL^O34.
     *
     * @param str_OML_O33 HL7 OML^O33 message.
     * @return HL7 ORL^O34 response message.
     */
    String lab28(final String str_OML_O33);

    /**
     * Handles the IHE LAB-29 status change transaction.
     * Typical flow:
     * - Receives a status change from the analyzer and converts it into HL7 OUL^R22.
     * - Sends it to the upstream LIS endpoint (url_upstream_lab29).
     * - Receives an HL7 acknowledgement/response.
     * - Converts it into the analyzer-specific format and sends it back to the analyzer if applicable.
     *
     * @param msg HL7 message payload.
     * @return HL7 response/acknowledgement message.
     */
    String lab29(final String msg);
    
    /**
     * Gets the mapping configuration path.
     * @return The mapping configuration path.
     */
    String getMappingPath();

    /**
     * Sets the mapping configuration path.
     * @param mappingPath The mapping configuration path.
     */
    void setMappingPath(String mappingPath);

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
    
    /**
     * Stops listening and releases sockets (server and client).
     */
    void stopListening();
}
