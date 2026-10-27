package labbook_connect.fhir;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moandjiezana.toml.Toml;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * HTTP client of the LabBook API, used by the FHIR facade.
 *
 * It is the only class that knows the LabBook address and the OAuth2
 * credentials. Resource providers ask it for JSON and never call LabBook
 * themselves. The access token is kept until it expires, and asked again
 * when LabBook answers 401.
 **/
public class LabBookClient {

	private static final Logger logger = LoggerFactory.getLogger(LabBookClient.class);

	/** Facade settings, absent on a site that does not use FHIR */
	private static final String SETTING_FILE = "/storage/resource/connect/fhir/fhir.toml";

	private static final String TOKEN_ENDPOINT    = "/services/oauth/token";
	private static final String EXTERNAL_ENDPOINT = "/services/external";

	/**
	 * Scopes the facade needs, over the whole project. LabBook grants only the
	 * ones its own client settings allow, so asking for all three is safe.
	 */
	private static final String SCOPES = "external/patient external/record external/result";

	/** Asked again this long before the expiry announced by LabBook */
	private static final long TOKEN_MARGIN_SEC = 60;

	private final String url;
	private final String clientId;
	private final String clientSecret;

	/** Short name LabBook keeps for the system that sends the requests */
	private final String externalSystem;

	private final HttpClient http;

	private String  token;
	private Instant tokenExpiry;

	/**
	 * Reads the settings file. A missing file is not an error: the facade
	 * then answers that it is not configured, and Connect keeps running.
	 */
	public LabBookClient() {
		File file = new File(SETTING_FILE);

		if (file.exists()) {
			Toml setting = new Toml().read(file);

			url          = removeLastSlash(setting.getString("labbook.url", ""));
			clientId     = setting.getString("labbook.client_id", "");
			clientSecret = setting.getString("labbook.client_secret", "");

			externalSystem = setting.getString("external.system_name", "");

			logger.info("FHIR facade configured for LabBook at {}, client {}", url, clientId);
		} else {
			url          = "";
			clientId     = "";
			clientSecret = "";

			externalSystem = "";

			logger.warn("FHIR facade not configured, {} is missing, LabBook will not be called", SETTING_FILE);
		}

		http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	/**
	 * Checks that the settings file gave everything needed to call LabBook.
	 * @return true when address, client id and secret are known.
	 */
	public boolean isConfigured() {
		return !url.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty();
	}

	/**
	 * Asks LabBook for a token, so the Connect management screen can show the state
	 * of the facade. The reason of a failure goes to the log, not to the answer.
	 * @return ready, not configured, or no token.
	 */
	public String checkLabBookAccess() {
		if (!isConfigured()) {
			return "not configured";
		}

		try {
			accessToken();
		} catch (Exception e) {
			logger.error("FHIR facade state, no token from LabBook at {}, client {} : {}", url, clientId,
					e.getMessage());
			return "no token";
		}

		return "ready";
	}

	/**
	 * Name LabBook keeps for the system that sends the requests, taken from the
	 * settings. A FHIR system is a URL, which the laboratory has no use for and
	 * which changes with the hosting of the calling system.
	 * @return The configured name, empty when none was given.
	 */
	public String externalSystem() {
		return externalSystem;
	}

	/**
	 * Calls one service of the LabBook API.
	 * @param path Service path under /services/external, starting with a slash.
	 * @param jsonBody Request body, JSON.
	 * @return The answer body, as sent by LabBook.
	 */
	public String postToLabBook(String path, String jsonBody) {
		Answer answer = postToLabBookWithStatus(path, jsonBody);

		if (answer.status() != 200) {
			logger.error("LabBook answered HTTP {} on {}", answer.status(), path);
			throw new InternalErrorException("LabBook answered HTTP " + answer.status() + " on " + path);
		}

		return answer.body();
	}

	/**
	 * Calls one service of the LabBook API and returns its answer as it
	 * came, status code included. Callers that have to tell a refusal from a
	 * failure use this one, record creation for instance, where LabBook answers
	 * 409 on a reference already used and 400 on incomplete data.
	 * @param path Service path under /services/external, starting with a slash.
	 * @param jsonBody Request body, JSON.
	 * @return The status code and the body sent by LabBook.
	 */
	public Answer postToLabBookWithStatus(String path, String jsonBody) {
		if (!isConfigured()) {
			throw new InternalErrorException("FHIR facade not configured, " + SETTING_FILE + " is missing or incomplete");
		}

		HttpResponse<String> response = sendToLabBook(path, jsonBody, accessToken());

		/** An expired or revoked token is asked again once, then the call is replayed */
		if (response.statusCode() == 401) {
			logger.info("LabBook answered 401 on {}, asking for a new token", path);

			synchronized (this) {
				token       = null;
				tokenExpiry = null;
			}

			response = sendToLabBook(path, jsonBody, accessToken());
		}

		return new Answer(response.statusCode(), response.body());
	}

	/**
	 * One answer of the LabBook API.
	 * @param status HTTP status code sent by LabBook.
	 * @param body Answer body, as sent by LabBook.
	 */
	public record Answer(int status, String body) {
	}

	/**
	 * Sends one request to the LabBook API with the given token.
	 */
	private HttpResponse<String> sendToLabBook(String path, String jsonBody, String bearer) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url + EXTERNAL_ENDPOINT + path))
				.header("Authorization", "Bearer " + bearer)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(30))
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
				.build();

		return send(request, path);
	}

	/**
	 * Returns a valid access token, asking LabBook for one when needed.
	 */
	private synchronized String accessToken() {
		if (token != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
			return token;
		}

		/**
		 * client_credentials, the machine to machine mode LabBook declares for
		 * Connect. The scope has to be asked for: without it Authlib delivers a
		 * token with no scope at all, and every protected service answers 403.
		 */
		StringBuilder body = new StringBuilder("grant_type=client_credentials&scope=");

		body.append(URLEncoder.encode(SCOPES, StandardCharsets.UTF_8));

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + TOKEN_ENDPOINT))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(30));

		/** Identity in the Authorization header, the client_secret_basic method */
		String basic = Base64.getEncoder().encodeToString(
				(clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

		builder.header("Authorization", "Basic " + basic);

		HttpRequest request = builder
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> response = send(request, TOKEN_ENDPOINT);

		if (response.statusCode() != 200) {
			logger.error("LabBook refused the token, HTTP {} : {}", response.statusCode(), response.body());
			throw new InternalErrorException("LabBook refused the OAuth token, HTTP " + response.statusCode());
		}

		try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
			JsonObject answer = reader.readObject();

			token = answer.getString("access_token", "");

			long expiresIn = answer.getInt("expires_in", 3600);

			tokenExpiry = Instant.now().plusSeconds(Math.max(expiresIn - TOKEN_MARGIN_SEC, 1));
		} catch (Exception e) {
			logger.error("Unreadable token answer from LabBook: ", e);
			throw new InternalErrorException("Unreadable OAuth token answer from LabBook");
		}

		if (token.isEmpty()) {
			throw new InternalErrorException("LabBook returned an empty OAuth token");
		}

		logger.info("OAuth token obtained from LabBook, valid until {}", tokenExpiry);

		return token;
	}

	/**
	 * Sends a request and turns any network failure into a FHIR error.
	 */
	private HttpResponse<String> send(HttpRequest request, String path) {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			// Restore interrupted state
			Thread.currentThread().interrupt();
			logger.error("Call to LabBook interrupted on {}: ", path, e);
			throw new InternalErrorException("Call to LabBook interrupted on " + path);
		} catch (Exception e) {
			logger.error("Call to LabBook failed on {}: ", path, e);
			throw new InternalErrorException("Call to LabBook failed on " + path + " : " + e.getMessage());
		}
	}

	/**
	 * Removes the trailing slash so paths are always joined the same way.
	 */
	private static String removeLastSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
