package net.beehivesys.link;

/** A transfer that cannot happen, with a message fit to show the player who asked for it. */
public final class LinkException extends Exception {

    public LinkException(final String message) {
        super(message);
    }
}
