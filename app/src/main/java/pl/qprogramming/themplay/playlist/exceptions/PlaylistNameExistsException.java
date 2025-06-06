package pl.qprogramming.themplay.playlist.exceptions;


public class PlaylistNameExistsException extends Exception {
    public PlaylistNameExistsException() {
        super("Playlist with that name already exists in preset");
    }

    public PlaylistNameExistsException(String message) {
        super(message);
    }
}
