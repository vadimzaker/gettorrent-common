package com.openseedbox.code;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Lightweight structural validator for .torrent (bencoded) byte payloads.
 *
 * <p>Used by both the frontend ({@code Client.addTorrent} — fail the upload
 * synchronously so the user sees a flash error instead of a silently broken
 * queue row) and the backend ({@code TransmissionBackend.getHashFromBase64}
 * — defence in depth, plus replaces the historical
 * {@code DigestUtils.shaHex(new byte[0])} fallback that produced a fake
 * "all-zero" info_hash for any unparseable input).</p>
 *
 * <p>Lives in {@code gettorrent-common} on purpose so we don't pull
 * {@code com.turn.ttorrent} (currently a backend-only dep) into the
 * frontend just to detect garbage uploads. The implementation is a
 * minimal hand-rolled bencode walker — enough to confirm:</p>
 * <ul>
 *   <li>non-empty payload, under {@link #MAX_TORRENT_BYTES}</li>
 *   <li>top-level value is a bencoded dictionary (starts with {@code 'd'})</li>
 *   <li>the dictionary contains an {@code info} key whose value is itself
 *       a dictionary containing both {@code name} (byte string) and
 *       {@code pieces} (byte string)</li>
 * </ul>
 *
 * <p>This is NOT a full bencode parser — we deliberately stop walking once
 * we have located {@code info.name} and {@code info.pieces}. Anything that
 * passes here will also be parseable by the {@code com.turn.ttorrent}
 * decoder used by the backend; anything rejected here is unambiguously a
 * non-torrent file.</p>
 *
 * <p>Java 7 compatible — no {@code String.isBlank}, no {@code var}, no
 * {@code List.of}. Verify with {@code check-java7-compatibility.sh} if
 * extending.</p>
 */
public final class TorrentValidator {

	/**
	 * Hard upper bound on the size of an uploaded .torrent file. A real
	 * torrent metainfo is usually 10–500 KB; even a 1 TB payload with
	 * 16 KB pieces tops out near 1 MB. 10 MB is generous and protects
	 * against DOS via huge bencode payloads we'd otherwise try to walk.
	 */
	public static final int MAX_TORRENT_BYTES = 10 * 1024 * 1024;

	private static final byte[] INFO_KEY = {'i', 'n', 'f', 'o'};
	private static final byte[] NAME_KEY = {'n', 'a', 'm', 'e'};
	private static final byte[] PIECES_KEY = {'p', 'i', 'e', 'c', 'e', 's'};

	private TorrentValidator() {}

	/**
	 * Throws {@link InvalidTorrentException} with a user-facing message if
	 * {@code data} is not a structurally valid torrent metainfo file.
	 * Returns silently on success.
	 */
	public static void validateOrThrow(byte[] data) {
		if (data == null || data.length == 0) {
			throw new InvalidTorrentException("Invalid torrent file: file is empty.");
		}
		if (data.length > MAX_TORRENT_BYTES) {
			throw new InvalidTorrentException(
				"Invalid torrent file: size %d bytes exceeds the %d byte limit.",
				data.length, MAX_TORRENT_BYTES);
		}
		if (data[0] != 'd') {
			throw new InvalidTorrentException(
				"Invalid torrent file: not a bencoded dictionary "
				+ "(check that the file is a real .torrent, not HTML or text).");
		}
		Cursor c = new Cursor(data);
		c.pos = 1;
		try {
			while (c.pos < c.data.length && c.data[c.pos] != 'e') {
				byte[] key = readByteString(c);
				if (Arrays.equals(key, INFO_KEY)) {
					validateInfoDict(c);
					return;
				}
				skipValue(c);
			}
		} catch (InvalidTorrentException ex) {
			// Already carries a user-facing message — re-throw verbatim.
			throw ex;
		} catch (Exception ex) {
			// Anything non-Invalid that escapes the walker (NPE, AIOOBE, ...)
			// is still a malformed-input symptom; don't leak the raw stack
			// trace at the user, just the exception class.
			throw new InvalidTorrentException(ex,
				"Invalid torrent file: bencode parse error (%s).",
				ex.getClass().getSimpleName());
		}
		throw new InvalidTorrentException(
			"Invalid torrent file: missing required 'info' dictionary.");
	}

	/**
	 * Convenience predicate. Prefer {@link #validateOrThrow(byte[])} when you
	 * want the failure reason surfaced to the user.
	 */
	public static boolean isValid(byte[] data) {
		try {
			validateOrThrow(data);
			return true;
		} catch (InvalidTorrentException ex) {
			return false;
		}
	}

	private static void validateInfoDict(Cursor c) {
		if (c.pos >= c.data.length || c.data[c.pos] != 'd') {
			throw new InvalidTorrentException(
				"Invalid torrent file: 'info' is not a dictionary.");
		}
		c.pos++;
		boolean hasName = false;
		boolean hasPieces = false;
		while (c.pos < c.data.length && c.data[c.pos] != 'e') {
			byte[] key = readByteString(c);
			if (Arrays.equals(key, NAME_KEY)) {
				byte[] name = readByteString(c);
				if (name.length == 0) {
					throw new InvalidTorrentException(
						"Invalid torrent file: 'info.name' is empty.");
				}
				hasName = true;
			} else if (Arrays.equals(key, PIECES_KEY)) {
				byte[] pieces = readByteString(c);
				// pieces is the concatenation of 20-byte SHA-1 hashes — anything
				// else is a structurally broken torrent.
				if (pieces.length == 0 || pieces.length % 20 != 0) {
					throw new InvalidTorrentException(
						"Invalid torrent file: 'info.pieces' is empty or not a multiple of 20 bytes.");
				}
				hasPieces = true;
			} else {
				skipValue(c);
			}
			if (hasName && hasPieces) return;
		}
		if (!hasName) {
			throw new InvalidTorrentException(
				"Invalid torrent file: 'info' dictionary is missing 'name'.");
		}
		throw new InvalidTorrentException(
			"Invalid torrent file: 'info' dictionary is missing 'pieces'.");
	}

	/** Reads a bencoded byte string (e.g. {@code "4:spam"}) and advances the cursor past it. */
	private static byte[] readByteString(Cursor c) {
		int colon = -1;
		int start = c.pos;
		for (int i = c.pos; i < c.data.length; i++) {
			byte b = c.data[i];
			if (b == ':') {
				colon = i;
				break;
			}
			if (b < '0' || b > '9') {
				throw new InvalidTorrentException(
					"Invalid torrent file: expected bencoded byte string at offset %d.", start);
			}
		}
		if (colon < 0 || colon == start) {
			throw new InvalidTorrentException(
				"Invalid torrent file: malformed byte-string length at offset %d.", start);
		}
		int len;
		try {
			len = Integer.parseInt(new String(c.data, start, colon - start, StandardCharsets.US_ASCII));
		} catch (NumberFormatException nfe) {
			throw new InvalidTorrentException(
				"Invalid torrent file: byte-string length not an integer at offset %d.", start);
		}
		if (len < 0 || colon + 1 + len > c.data.length) {
			throw new InvalidTorrentException(
				"Invalid torrent file: byte-string length %d overruns file at offset %d.", len, start);
		}
		byte[] out = new byte[len];
		System.arraycopy(c.data, colon + 1, out, 0, len);
		c.pos = colon + 1 + len;
		return out;
	}

	/**
	 * Advances the cursor past a single bencoded value of any type
	 * (byte string, integer, list, or dictionary). Used to skip dictionary
	 * entries we don't care about while hunting for {@code info}.
	 */
	private static void skipValue(Cursor c) {
		if (c.pos >= c.data.length) {
			throw new InvalidTorrentException("Invalid torrent file: unexpected end of data.");
		}
		byte tag = c.data[c.pos];
		if (tag == 'i') {
			c.pos++;
			while (c.pos < c.data.length && c.data[c.pos] != 'e') c.pos++;
			if (c.pos >= c.data.length) {
				throw new InvalidTorrentException("Invalid torrent file: unterminated integer.");
			}
			c.pos++;
		} else if (tag == 'l' || tag == 'd') {
			c.pos++;
			while (c.pos < c.data.length && c.data[c.pos] != 'e') {
				// Dictionary keys must be byte strings per the bencode spec; the
				// value following each key is a generic bencoded value.
				if (tag == 'd') readByteString(c);
				skipValue(c);
			}
			if (c.pos >= c.data.length) {
				throw new InvalidTorrentException(
					"Invalid torrent file: unterminated %s.", tag == 'l' ? "list" : "dictionary");
			}
			c.pos++;
		} else if (tag >= '0' && tag <= '9') {
			readByteString(c);
		} else {
			throw new InvalidTorrentException(
				"Invalid torrent file: unexpected bencode tag '%s' at offset %d.",
				(char) tag, c.pos);
		}
	}

	private static final class Cursor {
		final byte[] data;
		int pos;
		Cursor(byte[] data) { this.data = data; }
	}
}
