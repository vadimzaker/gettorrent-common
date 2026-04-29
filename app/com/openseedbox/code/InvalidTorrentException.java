package com.openseedbox.code;

/**
 * Thrown when a user-supplied .torrent file fails structural validation
 * (empty, not bencoded, missing the required {@code info} dictionary, or
 * exceeds the maximum allowed size).
 *
 * <p>Inherits from {@link MessageException} so existing controller-level
 * handling ({@code BaseController#onException}) and queue retry logic
 * ({@code PendingActionsJob#processAdd}) treat it as a user-facing error
 * carrying a safe-to-render message — no behaviour change for callers,
 * just a more specific type for the validation path.</p>
 *
 * <p>Pre-existing {@link MessageException} contract: the message text is
 * shown verbatim to the user, so keep messages short and non-technical.
 * Stack traces are deliberately not surfaced for this class.</p>
 */
public class InvalidTorrentException extends MessageException {
	public InvalidTorrentException(String message) {
		super(message);
	}

	public InvalidTorrentException(String message, Object... args) {
		super(message, args);
	}

	public InvalidTorrentException(Throwable t, String message, Object... args) {
		super(t, message, args);
	}
}
