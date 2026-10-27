package labbook_connect.fhir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import jakarta.servlet.ServletException;
import labbook_connect.labbook_connect.App;

/**
 * FHIR R4 facade of LabBook Connect.
 *
 * HAPI RestfulServer in Plain Server mode: no database of its own, every
 * resource is built from the LabBook API. Mounted on /fhir, it
 * answers /fhir/metadata with its CapabilityStatement.
 **/
public class FhirServer extends RestfulServer {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(FhirServer.class);

	/** Client of the LabBook API, the same one the management screen questions */
	private final transient LabBookClient client;

	/**
	 * @param client Client of the LabBook API the resource providers call.
	 */
	public FhirServer(LabBookClient client) {
		this.client = client;
	}

	/**
	 * Sets up the FHIR context, the server identity and the resource providers.
	 */
	@Override
	protected void initialize() throws ServletException {
		setFhirContext(FhirContext.forR4());

		setServerName("LabBook Connect FHIR facade");
		setServerVersion(App.VERSION);

		/** One resource provider per FHIR resource, all sharing the same client */
		registerProvider(new PatientResourceProvider(client));
		registerProvider(new RecordTransactionProvider(client));

		/** Readable answers when the facade is called from a browser */
		registerInterceptor(new ResponseHighlighterInterceptor());

		setDefaultPrettyPrint(true);

		logger.info("FHIR facade initialized, R4, server version {}", App.VERSION);
	}
}
