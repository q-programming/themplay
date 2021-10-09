package pl.qprogramming.themplay.settings;

import com.reactiveandroid.Model;
import com.reactiveandroid.annotation.Column;
import com.reactiveandroid.annotation.PrimaryKey;
import com.reactiveandroid.annotation.Table;
import com.reactiveandroid.query.Select;

import java.io.Serializable;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import pl.qprogramming.themplay.playlist.ThemPlayDatabase;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "properties", database = ThemPlayDatabase.class)
public class Property extends Model implements Serializable {

    public static final String DARK_MODE = "app.dark.mode";
    public static final String SHUFFLE_MODE = "app.shuffle";
    public static final String FADE_DURATION = "app.fade";
    public static final String LANGUAGE = "app.lang";

    @PrimaryKey
    private Long id;

    @Column
    private String name;

    @Column
    private String value;


    public static Property getProperty(String name) {
        return Optional.ofNullable(Select.from(Property.class).where("name = ?", name).fetchSingle()).orElse(Property.builder().name(name).build());
    }
}
