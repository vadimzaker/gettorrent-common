package com.openseedbox.backend;

import com.openseedbox.code.Util;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractFile implements IFile {

	private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"mp4", "webm", "mkv", "avi", "mov", "m4v", "wmv", "flv", "ogv"
	));

	private static final Set<String> AUDIO_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"mp3", "flac", "m4a", "wav", "aac", "ogg", "opus", "m4b", "wma"
	));

	/**
	 * Single source of truth for "is this filename playable in the HLS player?".
	 * Used by torrent-file rows (via instance isVideo()) AND by DirectDownload
	 * (which is not an IFile) — both must agree on the predicate so a Direct
	 * Download with a .mkv ends up with the same Play affordance as the same
	 * .mkv inside a torrent. Do NOT inline the extension list anywhere else.
	 */
	public static boolean isVideoExtension(String name) {
		if (name == null) return false;
		int i = name.lastIndexOf('.');
		if (i < 0 || i >= name.length() - 1) return false;
		return VIDEO_EXTENSIONS.contains(name.substring(i + 1).toLowerCase());
	}

	/**
	 * Single source of truth for "is this filename an audio-only file playable
	 * in the HLS player?". Mirror of isVideoExtension — same contract: torrent
	 * rows AND DirectDownload must agree, do NOT inline the extension list
	 * anywhere else.
	 */
	public static boolean isAudioExtension(String name) {
		if (name == null) return false;
		int i = name.lastIndexOf('.');
		if (i < 0 || i >= name.length() - 1) return false;
		return AUDIO_EXTENSIONS.contains(name.substring(i + 1).toLowerCase());
	}

	/**
	 * True if the filename is playable in the HLS player by any rendition
	 * (video or audio-only). Use this for the unified "Watch/Listen" button
	 * predicate; callers that need to branch on chrome (audio-only modifier)
	 * should call isVideoExtension / isAudioExtension directly.
	 */
	public static boolean isPlayableExtension(String name) {
		return isVideoExtension(name) || isAudioExtension(name);
	}

	public double getPercentComplete() {
		return ((double) getBytesCompleted() / getFileSizeBytes());	
	}
	
	public boolean isCompleted() {
		return (getBytesCompleted() == getFileSizeBytes());
	}
	
	public String getNiceFileSize() {
		return Util.getBestRate(getFileSizeBytes());
	}
	
	public String getNicePercentComplete() {
		return Util.formatPercentage(getPercentComplete() * 100) + "%";
	}

	public String getPlayLink() {
		return null;
	}

	public boolean isVideo() {
		return isVideoExtension(getName());
	}

	/**
	 * True for audio-only files playable in the HLS player (single audio
	 * rendition, no video variants). Mirror of isVideo() — both share the
	 * player chrome with is-audio-only modifier.
	 */
	public boolean isAudio() {
		return isAudioExtension(getName());
	}

	public boolean isPlayable() {
		return isPlayableExtension(getName());
	}

}
