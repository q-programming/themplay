package pl.qprogramming.themplay.preset;

import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;
import com.reactiveandroid.annotation.Unique;

import java.io.Serializable;
import java.util.Date;

import androidx.annotation.NonNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Table(name = "presets", database = ThemPlayDatabase.class)
public class Preset extends Model implements Serializable {

    public static final String NAME = "name";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    @PrimaryKey
    private Long id;
    @Column(name = NAME)
    @Unique
    private String name;
    @Column(name = CREATED_AT)
    private Date createdAt;
    @Column(name = UPDATED_AT)
    private Date updatedAt;


    @NonNull
    @Override
    public Long save() {
        if (id == null) {
            createdAt = new Date();
        }
        updatedAt = new Date();
        return super.save();
    }
}
