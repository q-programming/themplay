package pl.qprogramming.themplay.domain;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Entity(tableName = Preset.PRESET_TABLE_NAME)
public class Preset implements Serializable {

    public static final String PRESET_TABLE_NAME = "presets";
    public static final String COLUMN_ID = "id";
    public static final String NAME = "name";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    @PrimaryKey
    private Long id;
    @ColumnInfo(name = NAME, index = true)
    private String name;
    @ColumnInfo(name = CREATED_AT)
    @Builder.Default
    private Date createdAt = new Date();
    @ColumnInfo(name = UPDATED_AT)
    @Builder.Default
    private Date updatedAt = new Date();
}
