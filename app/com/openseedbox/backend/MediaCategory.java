package com.openseedbox.backend;

/**
 * Coarse media-type bucket used by the torrent file UI to GROUP files into
 * sections (Videos / Audio / Subtitles / Other) instead of presenting one
 * flat tree.
 *
 * The {@link #getLabel() label} is the user-visible section header rendered
 * by torrentPage.html — English-only per project convention; if/when these
 * strings get translated they must move into the existing I18N pipeline,
 * not be hand-localized here.
 *
 * Membership is decided by {@link AbstractFile#getMediaCategory()} which
 * delegates to the existing isVideo / isAudio / isSubtitle predicates so
 * there is exactly ONE source of truth per category.
 */
public enum MediaCategory {

	VIDEO("Videos"),
	AUDIO("Audio"),
	SUBTITLE("Subtitles"),
	OTHER("Other");

	private final String label;

	MediaCategory(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
