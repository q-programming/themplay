package pl.qprogramming.themplay.playlist.exceptions;


public class PlaylistNotFoundException extends Exception {
    public PlaylistNotFoundException() {
        super("Playlist not found");
    }

    public PlaylistNotFoundException(String message) {
        super(message);
    }
}
