package com.openseedbox.backend;

import com.openseedbox.code.Util;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractFile implements IFile {

	private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>(Arrays.asList(
		"mp4", "webm", "mkv", "avi", "mov", "m4v", "wmv", "flv", "ogv"
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
	
}
