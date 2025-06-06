package pl.qprogramming.themplay.preset.exceptions;

public class PresetAlreadyExistsException extends RuntimeException {

    public PresetAlreadyExistsException(){
        super("Preset already exists");
    }

    public PresetAlreadyExistsException(String message){
        super(message);
    }
}
