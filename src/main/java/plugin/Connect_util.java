package plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This class provides functions defined in the Connect module.
 */
public class Connect_util {

	/**
	 * Send hl7 message to an upstream.
	 *
	 * @param analyzer the analyzer loaded.
	 * @param url_upstream url to reach service.
	 * @return ack, message in hl7 format if OK.
	 */
	public static String send_hl7_msg(Analyzer analyzer, String url_upstream, String hl7_msg) {
		try {
			@SuppressWarnings("deprecation")
			URL url = new URL(url_upstream);

			// Open HTTP connection
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			connection.setRequestProperty("Content-Type", "application/hl7-v2");

			String payload = hl7_msg.toString();
			System.out.println("DEBUG payload = " + payload);
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = payload.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			// Read response
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				System.out.println("DEBUG send_hl7_msg upstream Response : " + response.toString());
				
				connection.disconnect();
				
				return response.toString();
			}
		} catch (Exception e) {
			System.out.println("ERROR send_hl7_msg HTTP connection e : " + e);
			
			return "ERROR send_hl7_msg HTTP connection e : " + e;
		}
	}
}
