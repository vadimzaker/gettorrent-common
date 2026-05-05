package controllers;

import com.openseedbox.code.MessageException;
import com.openseedbox.code.Util;
import com.openseedbox.mvc.GenericResult;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.h2.util.StringUtils;
import play.Logger;
import play.Play;
import play.Play.Mode;
import play.mvc.Before;
import play.mvc.Catch;
import play.mvc.Controller;
import play.mvc.Http;
import play.templates.Template;
import play.templates.TemplateLoader;

public abstract class BaseController extends Controller {
	
	@Before
	public static void checkRequestSecure() {
		//this is required so Play! knows if its in front of https-secured nginx or not
		Http.Header secure = request.headers.get("x-forwarded-proto");
		if (secure != null) {
			if (secure.value().toLowerCase().equals("https")) {
				request.secure = true;
			}
		}
	}	
	
	protected static void setGeneralErrorMessage(String message) {
		flash.put("error", message);
	}

	protected static void setGeneralErrorMessage(Exception ex) {
		setGeneralErrorMessage(Util.getStackTrace(ex));
	}

	protected static void setGeneralMessage(String message) {
		flash.put("message", message);
	}
	
	protected static void setGeneralWarningMessage(String message) {
		flash.put("warning", message);
	}

	protected static String renderToString(String template) {
		return renderToString(template, new HashMap<String, Object>());
	}

	protected static String renderToString(String template, Map<String, Object> args) {
		Template t = TemplateLoader.load(template);
		try {
			if (args != null) {
				renderArgs.data.putAll(args);	
			}			
			return t.render(renderArgs.data);
		} catch (Exception ex) {
			return ExceptionUtils.getStackTrace(ex);
		}
	}

	protected static void result(Object o) {
		throw new GenericResult(o);
	}

	protected static void resultTemplate(String name) {
		Template t = TemplateLoader.load(name);
		throw new GenericResult(t.render());
	}

	protected static void resultError(String message) {
		throw new GenericResult(message, true);
	}

	protected static void resultError(Exception ex) {
		resultError(Util.getStackTrace(ex));
	}

	protected static void write(String s) {
		write(s, new Object[]{});
	}

	protected static void write(String s, Object... args) {
		response.writeChunk(String.format(s, args));
	}

	protected static void writeLine(String s) {
		write(s + "<br />");
	}

	protected static void writeLine(String s, Object... args) {
		write(s + "<br />", args);
	}

	@Catch(Exception.class)
	protected static void onException(Exception ex) throws Exception {
		if (ex instanceof MessageException) {
			if (!StringUtils.equals(params.get("ext"), "html")) {
				resultError(ex);
			}
			result(Util.getStackTrace(ex));
		} else if (Play.mode == Mode.DEV) {
			ex.printStackTrace();
			throw ex;
		} else {
			// PROD non-MessageException branch.
			//
			// Historically this was a single commented-out Mails.sendError()
			// call, which made every uncaught exception silently disappear:
			// no log, no flash, no status-code change, no email. The most
			// recent symptom was "admin Delete user does silently nothing"
			// on prod (an FK violation got swallowed); but every controller
			// in gettorrent-frontend AND gettorrent-backend extends
			// BaseController, so the silent-swallow blast radius was the
			// whole app.
			//
			// Now: ALWAYS log at ERROR with a per-request id, set flash.error
			// with that id (main.html renders #flash-error from
			// flash.get('error')), return HTTP 500 + JSON body for
			// JSON-Accept clients, and best-effort attempt mail (only if
			// configured AND the Mails class is on the classpath — gettorrent-
			// common itself does NOT depend on the Mails notifier in
			// gettorrent-frontend, so we resolve it reflectively to avoid a
			// cyclic dep).
			String reqId = String.format("%x", System.nanoTime());
			Logger.error(ex,
				"[req=%s] Unhandled exception in %s: %s",
				reqId, request.action, ex.getMessage());

			// Best-effort mail. Wrapped in its own try/catch so a mail
			// failure (SMTP down, missing config, ClassNotFound for the
			// frontend-only Mails notifier when running inside the backend
			// image) never re-triggers @Catch and never masks the original
			// exception in the logs.
			try {
				String to = play.Play.configuration.getProperty(
						"openseedbox.errors.mailto", "");
				if (to != null && !to.trim().isEmpty()) {
					// Reflective lookup: gettorrent-common is shared by
					// frontend + backend, but Mails lives only in
					// gettorrent-frontend. ClassNotFoundException here is
					// expected on the backend image and is silently
					// swallowed (logged at WARN below).
					Class<?> mailsCls = Class.forName(
							"com.openseedbox.notifiers.Mails");
					Method send = mailsCls.getMethod("sendError",
							Throwable.class, Http.Request.class);
					send.invoke(null, ex, request);
				}
			} catch (Throwable mailEx) {
				Logger.warn(mailEx, "[req=%s] sendError failed", reqId);
			}

			// User-visible signal. main.html already renders flash.error
			// (see app/views/main.html lines 175-181 in gettorrent-frontend)
			// so no template change is required — the message survives the
			// redirect that the next request will perform.
			flash.put("error",
				"Something went wrong. Reference id: " + reqId);

			// For JSON clients, surface a 500 so JS / Playwright can branch
			// on res.ok. HTML form posts get the redirect target's flash on
			// the next navigation. Heuristic: ext=json query param OR
			// Accept: application/json header.
			Http.Header acceptHdr = request.headers.get("accept");
			String accept = acceptHdr != null ? acceptHdr.value() : "";
			if (StringUtils.equals(params.get("ext"), "json")
					|| (accept != null && accept.contains("application/json"))) {
				response.status = 500;
				renderJSON(Collections.singletonMap("error", "internal_error"));
			}
			// Don't rethrow — avoids leaking the stack trace to HTML
			// clients. Play 1 treats a @Catch handler that returns normally
			// as having handled the exception; the response body is empty
			// for the request that threw (the original action never reached
			// its render() / redirect() call), but the flash cookie carries
			// the Reference id forward to the user's next navigation.
		}
	}
}