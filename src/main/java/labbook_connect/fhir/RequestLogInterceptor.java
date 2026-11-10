package labbook_connect.fhir;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.ServletRequestTracing;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Writes every request the facade receives, and every answer it sends, to the log.
 *
 * The arrival is logged on the earliest point of the chain, before HAPI works out
 * which method should answer, so a request HAPI then refuses is logged all the
 * same. That is what a refusal like "does not know how to handle" needs, since
 * the answer alone never says what was asked.
 *
 * Both lines carry the request id HAPI gives every call, so an arrival and its
 * answer can be put back together in a busy log.
 **/
@Interceptor
public class RequestLogInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(RequestLogInterceptor.class);

	/** Where the start time is kept, to tell how long the answer took */
	private static final String START_TIME = RequestLogInterceptor.class.getName() + ".start";

	/**
	 * Logs the arrival of one request, with where it comes from and who it says it is.
	 * @param request Request as the servlet container received it.
	 */
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public void incoming(HttpServletRequest request) {
		request.setAttribute(START_TIME, System.currentTimeMillis());

		StringBuilder parameters = new StringBuilder();

		for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
			for (String value : parameter.getValue()) {
				if (parameters.length() > 0) {
					parameters.append("&");
				}

				parameters.append(parameter.getKey()).append("=").append(value);
			}
		}

		logger.info("Request {} received from {}, {} {} {} {}", requestId(request), caller(request),
				request.getMethod(), request.getRequestURI(),
				parameters.length() > 0 ? "?" + parameters : "", identity(request));
	}

	/**
	 * Logs the body of the request, whatever it holds, so that what was received
	 * can be read back as it came.
	 * @param details Request about to be handled.
	 */
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void body(ServletRequestDetails details) {
		/** Patient data, written only when the log level is debug */
		if (details == null || details.getServletRequest() == null || !logger.isDebugEnabled()) {
			return;
		}

		byte[] contents = details.loadRequestContents();

		if (contents == null || contents.length == 0) {
			return;
		}

		logger.debug("Request {} body {}", requestId(details.getServletRequest()),
				new String(contents, StandardCharsets.UTF_8));
	}

	/**
	 * Logs the answer, with its status and how long it took. This point is reached
	 * whether the request succeeded or was refused.
	 * @param details Request being answered, as the servlet saw it.
	 */
	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
	public void answered(ServletRequestDetails details) {
		if (details == null || details.getServletRequest() == null) {
			return;
		}

		HttpServletRequest request = details.getServletRequest();

		logger.info("Request {} answered {} in {} ms", requestId(request),
				details.getServletResponse() != null ? details.getServletResponse().getStatus() : 0,
				elapsed(request));
	}

	/**
	 * Returns the id HAPI gives every request, the one it sends back in the
	 * X-Request-ID header, so that a log line and the answer name the same call.
	 */
	private String requestId(HttpServletRequest request) {
		String id = ServletRequestTracing.getOrGenerateRequestId(request);

		return id != null ? id : "unknown";
	}

	/**
	 * Returns the time spent answering, in milliseconds.
	 */
	private long elapsed(HttpServletRequest request) {
		Object start = request.getAttribute(START_TIME);

		if (!(start instanceof Long)) {
			return 0;
		}

		return System.currentTimeMillis() - (Long) start;
	}

	/**
	 * Returns the address the request comes from, the one of the last proxy when
	 * there is one in front of the facade.
	 */
	private String caller(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");

		if (forwarded != null && !forwarded.isBlank()) {
			return request.getRemoteAddr() + " for " + forwarded;
		}

		return request.getRemoteAddr();
	}

	/**
	 * Returns who the caller says it is, from the Authorization header. Only the
	 * user name is written, never the secret that goes with it.
	 */
	private String identity(HttpServletRequest request) {
		String header = request.getHeader("Authorization");

		if (header == null || header.isBlank()) {
			return "[no Authorization header]";
		}

		if (!header.startsWith("Basic ")) {
			int space = header.indexOf(' ');

			return "[Authorization " + (space > 0 ? header.substring(0, space) : "unknown") + "]";
		}

		try {
			String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
					StandardCharsets.UTF_8);
			int    colon   = decoded.indexOf(':');

			return "[Basic, user " + (colon >= 0 ? decoded.substring(0, colon) : decoded) + "]";
		} catch (IllegalArgumentException unreadable) {
			return "[Basic, unreadable]";
		}
	}
}
