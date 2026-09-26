package net.beehivesys.link;

/** The core logs through whatever the host engine provides. */
public interface LinkLog {

    void info(String message);

    void warn(String message);

    LinkLog NONE = new LinkLog() {
        @Override
        public void info(final String message) {
        }

        @Override
        public void warn(final String message) {
        }
    };
}
