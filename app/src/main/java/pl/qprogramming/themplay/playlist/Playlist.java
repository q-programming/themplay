package pl.qprogramming.themplay.playlist;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Playlist {
    private int id;
    private String name;
    private List<String> files;
    private String currentFile;
    private int currentPosition;
    private boolean active;

}
