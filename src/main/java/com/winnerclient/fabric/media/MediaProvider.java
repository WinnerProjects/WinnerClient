package com.winnerclient.fabric.media;

public interface MediaProvider extends AutoCloseable {
    MediaSnapshot snapshot();

    void command(Command command, long positionMs);

    @Override
    void close();

    enum Command {
        PREVIOUS,
        TOGGLE,
        NEXT,
        SEEK
    }
}
