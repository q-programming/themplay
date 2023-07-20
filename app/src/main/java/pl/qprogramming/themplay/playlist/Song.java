package pl.qprogramming.themplay.playlist;

import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;

import java.io.Serializable;
import java.util.Objects;

import androidx.annotation.NonNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.val;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Table(name = "song", database = ThemPlayDatabase.class)
public class Song extends Model implements Serializable, Cloneable {
    public static final String CURRENT_POSITION = "currentPosition";

    @PrimaryKey
    private Long id;
    @Column
    private String filename;
    @Column
    private String fileUri;
    @Column
    private String filePath;
    @Column(name = CURRENT_POSITION)
    private int currentPosition;
    private boolean selected;


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Song song = (Song) o;
        return id.equals(song.id) &&
                filename.equals(song.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, filename);
    }

    @NonNull
    @Override
    protected Song clone() throws CloneNotSupportedException {
        val song = (Song) super.clone();
        song.setId(null);
        return song;
    }
}
