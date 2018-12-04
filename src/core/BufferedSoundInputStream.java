package core;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import static core.SoundConfig.DEFAULT_TIMEOUT;

/**
 * BufferedSoundInputStream lets to read data from single controlled source using multiple threads
 *
 * @author cybor97
 */
public abstract class BufferedSoundInputStream extends InputStream {
    private byte[] buffer;
    private long lastLineId;
    private HashMap<Long, Boolean> linesActualization = new HashMap<>();

    private int bytesRead;
    private Thread manager = null;

    public void init(SoundConfig soundConfig, int desiredBufferLength) throws InterruptedException {
        if (manager == null || manager.isInterrupted()) {
            initAudioSource(soundConfig);
            buffer = new byte[desiredBufferLength];
            manager = new Thread(() -> {
                while (!Thread.interrupted()) {
                    try {
                        BufferedSoundInputStream.this.bytesRead = read(buffer, 0, desiredBufferLength);
                        for (long key : linesActualization.keySet()) {
                            linesActualization.replace(key, true);
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
                BufferedSoundInputStream.this.closeAudioSource();
            });
            manager.run();
        } else {
            manager.interrupt();
            manager.join();
            init(soundConfig, desiredBufferLength);
        }
    }

    public void destroy() throws InterruptedException {
        manager.interrupt();
        manager.join();
    }

    protected abstract void initAudioSource(SoundConfig soundConfig);

    protected abstract void closeAudioSource();

    /**
     * Subscribe to get actual data
     *
     * @return lastLineId
     */
    public long subscribe() {
        linesActualization.put(++lastLineId, false);
        return lastLineId;
    }

    public void unsubscribe(long lineId) {
        linesActualization.remove(lineId);
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public byte[] getBuffer(long lineId) throws InterruptedException, TimeoutException {
        return getBuffer(lineId, DEFAULT_TIMEOUT);
    }

    public byte[] getBuffer(long lineId, long timeout) throws InterruptedException, TimeoutException {
        if (manager == null || !manager.isAlive()) {
            throw new IllegalStateException("You cannot get buffer of uninitialized stream");
        }

        long timeSpent = 0;
        final int step = 10;

        while (!linesActualization.get(lineId)) {
            //TODO: Find better way to actualize data
            Thread.sleep(step);
            timeSpent += step;
            if (timeSpent > timeout) {
                throw new TimeoutException("Timed out actualizing data from source");
            }
        }

        linesActualization.replace(lineId, false);
        return buffer;
    }
}
