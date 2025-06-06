package pl.qprogramming.themplay.preset;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ExportResult {
    private boolean success;
    private String errorLog;

}
