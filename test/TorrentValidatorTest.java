import com.openseedbox.code.InvalidTorrentException;
import com.openseedbox.code.TorrentValidator;
import java.io.ByteArrayOutputStream;
import org.junit.Test;
import play.test.UnitTest;

/**
 * Unit tests for the structural .torrent validator.
 *
 * <p>Builds bencoded payloads by hand instead of pulling
 * {@code com.turn.ttorrent} (a backend-only dep) into this module — keeps
 * the test self-contained and forces us to think about the on-the-wire
 * bytes the parser sees.</p>
 */
public class TorrentValidatorTest extends UnitTest {

	/** Helper: 20-byte fake SHA-1 piece hash so we satisfy the "multiple of 20" rule. */
	private static final String ONE_PIECE = "12345678901234567890";

	@Test
	public void rejects_null_payload() {
		try {
			TorrentValidator.validateOrThrow(null);
			fail("expected InvalidTorrentException for null payload");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("empty"));
		}
	}

	@Test
	public void rejects_empty_payload() {
		try {
			TorrentValidator.validateOrThrow(new byte[0]);
			fail("expected InvalidTorrentException for empty payload");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("empty"));
		}
	}

	@Test
	public void rejects_text_file_with_torrent_extension() {
		byte[] data = "This is just a text file, not a torrent.\n".getBytes();
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for text file");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("bencoded"));
		}
	}

	@Test
	public void rejects_dictionary_without_info() {
		// d8:announce4:hostee  ->  {"announce": "host"} but no "info"
		byte[] data = bytes("d8:announce4:hoste");
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for missing info");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("info"));
		}
	}

	@Test
	public void rejects_info_without_name() {
		// d4:infod6:pieces20:<20 bytes>ee  -> info has pieces but no name
		byte[] data = bytes("d4:infod6:pieces20:" + ONE_PIECE + "ee");
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for info without name");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("name"));
		}
	}

	@Test
	public void rejects_info_without_pieces() {
		byte[] data = bytes("d4:infod4:name5:helloee");
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for info without pieces");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("pieces"));
		}
	}

	@Test
	public void rejects_info_pieces_not_multiple_of_20() {
		// pieces length 19 -> not a multiple of 20
		byte[] data = bytes("d4:infod4:name5:hello6:pieces19:1234567890123456789ee");
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for pieces not multiple of 20");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("pieces"));
		}
	}

	@Test
	public void rejects_oversize_payload() {
		// Build a huge bencoded-looking blob — first byte 'd' so we get past
		// the cheap header check and into the size guard.
		byte[] data = new byte[TorrentValidator.MAX_TORRENT_BYTES + 1];
		data[0] = 'd';
		try {
			TorrentValidator.validateOrThrow(data);
			fail("expected InvalidTorrentException for oversize payload");
		} catch (InvalidTorrentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("limit"));
		}
	}

	@Test
	public void accepts_minimal_single_file_torrent() {
		// d4:infod6:lengthi42e4:name8:test.bin6:pieces20:<20 bytes>ee
		byte[] data = bytes("d4:infod6:lengthi42e4:name8:test.bin6:pieces20:" + ONE_PIECE + "ee");
		TorrentValidator.validateOrThrow(data); // should not throw
		assertTrue(TorrentValidator.isValid(data));
	}

	@Test
	public void accepts_torrent_with_announce_before_info() {
		byte[] data = bytes(
			"d8:announce20:http://tracker/annc"
			+ "4:infod6:lengthi42e4:name8:test.bin6:pieces20:" + ONE_PIECE + "ee"
		);
		assertTrue(TorrentValidator.isValid(data));
	}

	@Test
	public void accepts_torrent_with_multi_file_info() {
		// d4:infod5:filesld6:lengthi10e4:pathl1:aeed6:lengthi20e4:pathl1:beee
		//        4:name3:dir6:pieces40:<40 bytes>ee
		byte[] data = bytes(
			"d4:infod"
			+ "5:filesl"
			+ "d6:lengthi10e4:pathl1:aee"
			+ "d6:lengthi20e4:pathl1:bee"
			+ "e"
			+ "4:name3:dir6:pieces40:" + ONE_PIECE + ONE_PIECE + "ee"
		);
		assertTrue(TorrentValidator.isValid(data));
	}

	@Test
	public void isValid_returns_false_instead_of_throwing() {
		assertFalse(TorrentValidator.isValid(null));
		assertFalse(TorrentValidator.isValid(new byte[0]));
		assertFalse(TorrentValidator.isValid("not a torrent".getBytes()));
	}

	private static byte[] bytes(String s) {
		// Direct byte cast is fine here — the test payloads are all ASCII
		// (lengths, keys, the synthetic 20-byte piece hash), no multi-byte
		// chars to worry about.
		ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
		for (int i = 0; i < s.length(); i++) {
			out.write((byte) s.charAt(i));
		}
		return out.toByteArray();
	}
}
