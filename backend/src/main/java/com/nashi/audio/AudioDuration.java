package com.nashi.audio;

import org.jaudiotagger.audio.AudioFileIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Length of an uploaded audio file, read from its header with jaudiotagger. */
public final class AudioDuration {

    private AudioDuration() {
    }

    /**
     * @param ext file extension without the dot ({@code mp3}, {@code m4a}); jaudiotagger picks the reader by it
     * @return the length in whole seconds, rounded
     * @throws IllegalArgumentException when the bytes are not readable audio of that type
     */
    public static int seconds(byte[] audio, String ext) {
        Path file = null;
        try {
            file = Files.createTempFile("nashi-audio-", "." + ext.toLowerCase(java.util.Locale.ROOT));
            Files.write(file, audio);
            double seconds = AudioFileIO.read(file.toFile()).getAudioHeader().getPreciseTrackLength();
            return (int) Math.round(seconds);
        } catch (IOException e) {
            throw new IllegalStateException("could not buffer the audio", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("not readable audio: " + e.getClass().getSimpleName(), e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // the temp directory is cleaned by the OS
                }
            }
        }
    }
}
