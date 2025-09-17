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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides functions defined in the Connect module.
 */
public class Connect_util {
	
	private static final Logger logger = LoggerFactory.getLogger(Connect_util.class);
	
	public static final int START_MSG_MLLP = 0x0B; // MLLP Start Block
    public static final int END_MSG_MLLP = 0x1C;   // MLLP End Block
    public static final int CARRIAGE_RETURN = 0x0D; // Final Carriage Return

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

        String receivedHL7 = messageBuffer.toString(StandardCharsets.UTF_8).trim();
        
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
	public static String send_hl7_msg(Analyzer analyzer, String url_upstream, String hl7_msg) {
		try {
			@SuppressWarnings("deprecation")
			URL url = new URL(url_upstream);

			// Open Http or Https connection
			HttpURLConnection connection;
			
			if (url_upstream.toLowerCase().startsWith("https")) {
				/* By deactivating this block, self-signed certificates are not authorised.
			    // Disables certificate verification so that a self-signed certificate can be used 
			    TrustManager[] trustAllCerts = new TrustManager[] {
			    		new X509TrustManager() {
			    		    @Override
			    		    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
			    		        // Trust all clients
			    		    }

			    		    @Override
			    		    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
			    		        // Trust all servers
			    		    }

			    		    @Override
			    		    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			    		        return new java.security.cert.X509Certificate[0];
			    		    }
			    		}
		            };
		            
		            SSLContext sc = SSLContext.getInstance("TLS");
		            sc.init(null, trustAllCerts, new SecureRandom());
		            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		            HostnameVerifier allHostsValid = (hostname, session) -> true;
		            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		            */

		            connection = (HttpsURLConnection) url.openConnection();
			} else {
			    connection = (HttpURLConnection) url.openConnection();
			}

			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/hl7-v2");

			String payload = hl7_msg;
			
			logger.info("Sending HL7 payload:\n{}", hl7_msg);
			
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = payload.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}

			// Read response
			try (InputStream is = connection.getInputStream()) {
	            ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
	            byte[] buffer = new byte[1024];
	            int len;
	            while ((len = is.read(buffer)) != -1) {
	                responseBytes.write(buffer, 0, len);
	            }

	            String response = responseBytes.toString(StandardCharsets.UTF_8.name());
	            logger.info("send_hl7_msg upstream Response: {}", response);

	            return response;
	        } finally {
	            connection.disconnect();
	        }
		} catch (IOException e) {
		    logger.error("Network error while sending HL7 message: {}", e.getMessage(), e);
		    return "ERROR send_hl7_msg Network : " + e.getMessage();
		} catch (Exception e) {
			logger.error("General Error during HL7 message transmission: {}", e.getMessage(), e);			
			return "ERROR send_hl7_msg HTTP connection : " + e.getMessage();
		}
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
}
