package org.open2jam.parsers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.open2jam.parsers.utils.ByteBufferInputStream;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.parsers.utils.SampleData;

/**
 * OJN chart format parser.
 * <p>
 * Note: Does not override equals()/hashCode() because Chart parent implementation
 * already provides proper value-based equality using title, artist, level, keys, players.
 * OJN-specific fields (noteOffset, coverOffset, etc.) are implementation details
 * for parsing binary data, not part of chart identity.
 */
@SuppressWarnings("java:S2160") // Equals/hashCode provided by parent Chart class
public class OJNChart extends Chart {

    int noteOffset;
    int noteOffsetEnd;
    int coverOffset;
    int coverSize;
    int thumbnailSize;  // Size of embedded thumbnail image (added for SQLite caching)

    public OJNChart() {
	type = TYPE.OJN;
    }

    @Override
    public File getSource() {
	return source;
    }

    @Override
    public int getLevel() {
	return level;
    }

    @Override
    public int getKeys() {
	return keys;
    }

    @Override
    public int getPlayers() {
	return players;
    }

    @Override
    public String getTitle() {
	return title;
    }

    @Override
    public String getArtist() {
	return artist;
    }

    @Override
    public String getGenre() {
	return genre;
    }

    @Override
    public String getNoter() {
	return noter;
    }

    File sampleFile;
    @Override
    public Map<Integer, SampleData> getSamples() {
	return OJMParser.parseFile(sampleFile);
    }

    @Override
    public Map<Integer, String> getSampleIndex() {
	if (sampleIndex.isEmpty()) {
	    for(Entry<Integer, SampleData> entry : getSamples().entrySet()) {
		    sampleIndex.put(entry.getKey(), entry.getValue().getName());
		try {
		    entry.getValue().dispose();
		} catch (IOException ex) {
		    Logger.global.log(Level.WARNING, "As if I care about it :/");
		}
	    }
	}
	return sampleIndex;
    }

    @Override
    public double getBPM() {
	return bpm;
    }

    @Override
    public int getNoteCount() {
	return notes;
    }

    @Override
    public int getDuration() {
	return duration;
    }

    @Override
    public String getCoverName() {
	if(!hasCover()) return null;
	return "OJN_"+this.title+"_"+this.level;
    }

    @Override
    public boolean hasCover() {
	return coverSize > 0;
    }

    @Override
    public BufferedImage getCover() {
	if (!hasCover()) {
	    return getNoImage();
	}
	try {
	    ByteBuffer buffer = OJNFileReader.read(source, coverOffset, coverSize);
	    ByteBufferInputStream bis = new ByteBufferInputStream(buffer);
	    return ImageIO.read(bis);
	} catch (IOException e) {
	    Logger.global.log(Level.WARNING, "IO exception getting image from file {0}", source.getName());
	}
	return null;
    }

    @Override
    public EventList getEvents() {
	return OJNParser.parseChart(this);
    }

    // ===== Getters for persistence layer (ChartDatabase) =====
    
    /**
     * Get note data offset in file.
     * @return Byte offset where note data starts
     * @package-private Made public for ChartDatabase access
     */
    public int getNoteOffset() {
        return noteOffset;
    }

    /**
     * Get note data end offset in file.
     * @return Byte offset where note data ends
     * @package-private Made public for ChartDatabase access
     */
    public int getNoteOffsetEnd() {
        return noteOffsetEnd;
    }

    /**
     * Get cover image offset in file.
     * @return Byte offset where cover image starts
     */
    public int getCoverOffset() {
        return coverOffset;
    }

    /**
     * Get cover image size in bytes.
     * @return Size of cover image
     */
    public int getCoverSize() {
        return coverSize;
    }

    /**
     * Get thumbnail image size in bytes.
     * @return Size of embedded thumbnail
     */
    public int getThumbnailSize() {
        return thumbnailSize;
    }
}