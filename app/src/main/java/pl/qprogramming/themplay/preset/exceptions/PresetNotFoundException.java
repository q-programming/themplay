package pl.qprogramming.themplay.preset.exceptions;

public class PresetNotFoundException extends RuntimeException {

    public PresetNotFoundException(){
        super("Preset doesn't  exists");
    }

    public PresetNotFoundException(String message){
        super(message);
    }
}
