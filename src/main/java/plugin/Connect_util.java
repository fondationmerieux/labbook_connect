package plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.net.ssl.HttpsURLConnection;
/* deactivating, self-signed certificates are not authorised.
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
 */

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moandjiezana.toml.Toml;

/**
 * This class provides functions defined in the Connect module.
 */
public class Connect_util {

	private static final Logger logger = LoggerFactory.getLogger(Connect_util.class);

	/** MLLP start block character. */
	public static final int START_MSG_MLLP = 0x0B;
	/** MLLP end block character. */
	public static final int END_MSG_MLLP = 0x1C;
	/** Carriage return delimiter used by HL7 v2 segments. */
	public static final int CARRIAGE_RETURN = 0x0D;
	
	/**
	 * Utility class; not meant to be instantiated.
	 */
	private Connect_util() {
	    // utility
	}

	/**
	 * Reads an HL7 message encapsulated in MLLP format from an InputStream.
	 * This method ensures that only valid messages are processed.
	 *
	 * @param inputStream The input stream to read from.
	 * @return The extracted HL7 message, or an empty string if no message is available.
	 * @throws IOException If an error occurs during reading.
	 */
	public static String readMLLPMessage(InputStream inputStream) throws IOException {
		ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
		int byteRead;
		boolean started = false;

		while ((byteRead = inputStream.read()) != -1) {
			if (byteRead == START_MSG_MLLP) { 
				started = true;
				continue;
			}
			if (byteRead == END_MSG_MLLP) { 
				int nextByte = inputStream.read();
				if (nextByte != CARRIAGE_RETURN) {
					logger.warn("Expected final carriage return but got: {}", nextByte);
				}
				break;
			}
			if (started) {
				messageBuffer.write(byteRead);
			}
		}

		String receivedHL7 = messageBuffer.toString(StandardCharsets.UTF_8);

		// Log the received message only if it contains valid data
		if (!receivedHL7.isEmpty()) {
			logger.info("Complete HL7 response received:\n{}", receivedHL7.replace("\r", "\n"));
		}

		return receivedHL7;
	}


	/**
	 * Encapsulates an HL7 message in MLLP format.
	 *
	 * @param hl7Message The HL7 message to encapsulate.
	 * @return The MLLP-encoded message.
	 */
	public static String encapsulateHL7Message(String hl7Message) {
		return (char) START_MSG_MLLP + hl7Message + (char) END_MSG_MLLP + "\r";
	}

	/**
	 * Send hl7 message to an upstream.
	 *
	 * @param analyzer the analyzer loaded.
	 * @param url_upstream url to reach service.
	 * @param hl7_msg      HL7 message in ER7 format
	 * @return ack, message in hl7 format if OK.
	 */
	@SuppressWarnings("deprecation")
	public static String send_hl7_msg(Analyzer analyzer, String url_upstream, String hl7_msg) {
		String currentUrl = url_upstream;               // first try: as-is
		for (int attempt = 0; attempt < 2; attempt++) { // second try: drop /external if 404
			try {
				URL url = new URL(currentUrl);
				URLConnection raw = url.openConnection();

				HttpURLConnection connection;
				if ("https".equalsIgnoreCase(url.getProtocol())) {
				    connection = (HttpsURLConnection) raw; // in-code comment: still an HttpURLConnection subtype
				} else {
				    connection = (HttpURLConnection) raw;
				}
				
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setConnectTimeout(10000);
				connection.setReadTimeout(15000);
				connection.setRequestProperty("Content-Type", "application/hl7-v2");

				logger.info("Sending HL7 payload to {}:\n{}", currentUrl, hl7_msg);

				// send body
				try (OutputStream os = connection.getOutputStream()) {
					os.write(hl7_msg.getBytes(StandardCharsets.UTF_8));
				}

				// read HTTP status and body (use error stream if non-2xx)
				int code = connection.getResponseCode();
				try (InputStream is = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream()) {


					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					if (is != null) {
						byte[] buf = new byte[2048];
						int n;
						while ((n = is.read(buf)) != -1) bout.write(buf, 0, n);
					}
					String body = bout.toString(StandardCharsets.UTF_8.name()).trim();

					logger.info("Upstream HTTP {} from {} ; first80='{}'",
							code, currentUrl, body.substring(0, Math.min(80, body.length())).replace("\r","\\r"));

					// If 404 on first attempt and URL contains /services/external/, retry once without it.
					if (code == 404 && attempt == 0 && currentUrl.contains("/services/external/")) {
					    String nextUrl = currentUrl.replace("/services/external/", "/services/");
					    logger.info("HTTP 404 — retrying without '/external' in URL → {}", nextUrl); // log target URL
					    currentUrl = nextUrl;
					    continue;
					}

					return body.isEmpty() ? ("ERROR send_hl7_msg Network : HTTP " + code) : body;

				} finally {
					connection.disconnect();
				}

			} catch (java.io.FileNotFoundException e) {
				// Some JREs throw this for 404: apply the same retry rule
				if (attempt == 0 && currentUrl.contains("/services/external/")) {
					String nextUrl = currentUrl.replace("/services/external/", "/services/");
					logger.info("FileNotFound (likely 404) — retrying without '/external' → {}", nextUrl);
					currentUrl = nextUrl;
					continue;
				}
				logger.error("Network error while sending HL7 message: {}", e.toString(), e);
				return "ERROR send_hl7_msg Network : " + currentUrl + " -> " + e.toString();

			} catch (IOException e) {
				logger.error("Network error while sending HL7 message: {}", e.getMessage(), e);
				// no special retry except the 404 case above
				return "ERROR send_hl7_msg Network : " + currentUrl + " -> " + e.getMessage();

			} catch (Exception e) {
				logger.error("General Error during HL7 message transmission: {}", e.getMessage(), e);
				return "ERROR send_hl7_msg HTTP connection : " + e.getMessage();
			}
		}

		return "ERROR send_hl7_msg HTTP connection : no response";
	}

	/**
	 * Archives the message in the specified directory based on labType and srcType.
	 *
	 * @param id_analyzer The analyzer ID.
	 * @param archive_msg The flag indicating whether to archive ("Y" to enable).
	 * @param message     The message to be archived.
	 * @param labType     The lab identifier ("LAB-27", "LAB-28", "LAB-29").
	 * @param srcType     "LIS" or "Analyzer".
	 */
	public static void archiveMessage(String id_analyzer, String archive_msg, String message, String labType, String srcType) {
		// Check if archiving is enabled
		if (!"Y".equalsIgnoreCase(archive_msg)) {
			logger.info("Archiving disabled (archive_msg != 'Y')");
			return;
		}

		try {
			labType = labType.toUpperCase().trim();

			// Determine the correct archive directory based on labType
			String archiveDirName;
			switch (labType) {
			case "LAB-27":
				archiveDirName = "archive_lab27";
				break;
			case "LAB-28":
				archiveDirName = "archive_lab28";
				break;
			case "LAB-29":
				archiveDirName = "archive_lab29";
				break;
			default:
				logger.error("Lab Archive ERROR: Invalid labType '{}'. Must be 'LAB-27', 'LAB-28', or 'LAB-29'.", labType);
				return;
			}

			Path archivePath = Paths.get("/storage/resource/connect/analyzer/" + id_analyzer + "/" + archiveDirName);

			// Ensure the directory exists
			Files.createDirectories(archivePath);

			// Generate a filename with timestamp
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			String filename = labType + "_" + srcType + "_" + timestamp + ".txt";
			Path filePath = archivePath.resolve(filename);

			// Write the HL7 message to the file
			Files.write(filePath, message.getBytes(StandardCharsets.UTF_8));

			logger.info("Archived message at {}", filePath);

		} catch (IOException e) {
			logger.error("ERROR Lab Archive : Unable to archive message due to IO issue: {}", e.getMessage(), e);
		} catch (Exception e) {
			logger.error("ERROR Lab Archive unexpected : {}", e.getMessage(), e);
		}
	}
	
	/**
	 * Loads a TOML mapping file from the given path.
	 * If the file does not exist or cannot be read, an empty TOML object is returned.
	 * @param mappingPath The mapping file path (with or without ".toml").
	 * @return The parsed TOML object.
	 */
	public static Toml loadMappingToml(String mappingPath) {
	    if (mappingPath == null || mappingPath.trim().isEmpty()) {
	        logger.info("Mapping load: mappingPath empty");
	        return new Toml();
	    }

	    try {
	        Path p = Paths.get(mappingPath);
	        if (!Files.exists(p) && !mappingPath.endsWith(".toml")) {
	            p = Paths.get(mappingPath + ".toml");
	        }

	        if (!Files.exists(p) || !Files.isRegularFile(p)) {
	            logger.info("Mapping load: file not found path={}", p.toString());
	            return new Toml();
	        }

	        logger.info("Mapping load: OK path={}", p.toString());
	        return new Toml().read(p.toFile());

	    } catch (Exception e) {
	        logger.error("Mapping load: ERROR path={} err={}", mappingPath, e.toString());
	        return new Toml();
	    }
	}
}
