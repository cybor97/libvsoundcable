package core;

import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static core.SoundConfig.DEFAULT_TIMEOUT;

/**
 * BufferedSoundOutputStream lets to write data to a single controlled buffer from a multiple threads
 *
 * @author cybor97
 */
public abstract class BufferedSoundOutputStream extends OutputStream {
    private HashMap<Long, Collection<Byte>> buffers;
    private Thread manager;
    private long lastLineId;
    private HashMap<Long, Boolean> linesActualization = new HashMap<>();

    public void init(SoundConfig soundConfig, int desiredBufferLength) throws InterruptedException {
        if (manager == null || manager.isInterrupted()) {
            initAudioTarget(soundConfig);
            buffers = new HashMap<>();
            manager = new Thread(() -> {
                while (!Thread.interrupted()) {
                    try {
                        byte[] consolidatedBuffer = consolidate(buffers.values());
                        if (consolidatedBuffer != null) {
                            write(consolidatedBuffer, 0, desiredBufferLength);
                        }
                        for (long key : linesActualization.keySet()) {
                            linesActualization.replace(key, true);
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
                BufferedSoundOutputStream.this.closeAudioTarget();
            });
            manager.run();
        } else {
            manager.interrupt();
            manager.join();
            init(soundConfig, desiredBufferLength);
        }
    }

    protected abstract void initAudioTarget(SoundConfig soundConfig);

    protected abstract void closeAudioTarget();

    protected abstract byte[] consolidate(Collection<Collection<Byte>> buffers);

    public long subscribe() {
        linesActualization.put(++lastLineId, false);
        return lastLineId;
    }

    public void unsubscribe(long lineId) {
        linesActualization.remove(lineId);
    }

    //Just a way to "add a spy equipment" :D {
    public byte[] getBuffer(long lineId) throws InterruptedException, TimeoutException {
        return getBuffer(lineId, 2000);
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
        return consolidate(buffers.values());
    }
    //}

    //Write a buffer, wait for consolidation and return consolidated result
    public byte[] write(long lineId, byte[] buffer) throws TimeoutException, InterruptedException {
        return write(lineId, buffer, DEFAULT_TIMEOUT);
    }

    public byte[] write(long lineId, byte[] buffer, long timeout) throws InterruptedException, TimeoutException {
        if (manager == null || !manager.isAlive()) {
            throw new IllegalStateException("You cannot get buffer of uninitialized stream");
        }

        long timeSpent = 0;
        final int step = 10;

        buffers.put(lineId, Bytes.asList(buffer) );

        while (!linesActualization.get(lineId)) {
            //TODO: Find better way to actualize data
            Thread.sleep(step);
            timeSpent += step;
            if (timeSpent > timeout) {
                throw new TimeoutException("Timed out actualizing data from source");
            }
        }

        linesActualization.replace(lineId, false);
        return consolidate(buffers.values());
    }
}
