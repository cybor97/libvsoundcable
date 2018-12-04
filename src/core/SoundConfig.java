package core;

//TODO: Review with android-specific props and implement as factory or static presets
public class SoundConfig {
    //2 seconds, to prevent any chance to dead hanging
    //Just find a way to deal with it
    public static final int DEFAULT_TIMEOUT = 2000;

    private int encoding;
    private int sampleRate;
    private int sampleSizeInBits;
    private int channels;
    private int frameSize;
    private int frameRate;
    private boolean bigEndian;
}
