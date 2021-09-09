package pl.qprogramming.themplay.playlist;

import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Table(name = "song", database = ThemPlayDatabase.class)
public class Song extends Model implements Serializable {
    public static final String CURRENT_POSITION = "currentPosition";

    @PrimaryKey
    private Long id;
    @Column
    private String filename;
    @Column(name = CURRENT_POSITION)
    private int currentPosition;

}
