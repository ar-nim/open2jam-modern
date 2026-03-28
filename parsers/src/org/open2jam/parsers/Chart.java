package org.open2jam.parsers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.open2jam.parsers.utils.ByteHelper;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.parsers.utils.SampleData;

/**
* this encapsulates a song chart.
* in case there's more than one rank(difficulty)
* for the song, the rank integer follows this pattern:
* 0 - easy, 1 - normal, 2 - hard, 3 - very hard, ...
* there's no upper bound.
*/
public abstract class Chart implements Comparable<Chart>
{
    public enum TYPE {NONE, OJN}

    protected TYPE type = TYPE.NONE;

    protected File source;
    protected int level = 0;
    protected int keys = 7;
    protected int players = 1;
    protected String title = "";
    protected String artist = "";
    protected String genre = "";
    protected String noter = "";
    protected double bpm = 130;
    protected int notes = 0;
    protected int duration = 0;

    protected String coverName = null;
    protected File imageCover = null;

    protected Map<Integer, String> sampleIndex = new HashMap<>();
    protected Map<Integer, String> bgaIndex = new HashMap<>();

    public TYPE getType() {
        return type;
    }
    
    /** the File object to the source file of this header */
    public abstract File getSource();

    /** 
    * an integer representing difficulty.
    * we _should_ have some standard here
    * maybe we could use o2jam as the default
    * and normalize the others to this rule
    */
    public abstract int getLevel();

    /** The number of keys in this chart */
    public abstract int getKeys();
    
    /** The number of player for this chart */
    public abstract int getPlayers();

    /** The title of the song */
    public abstract String getTitle();
    
    /** The artist of the song */
    public abstract String getArtist();
    
    /** The genre of the song */
    public abstract String getGenre();
    
    /** The noter of the song (Unused?) */
    public abstract String getNoter();
    
    /** The samples of the song */
    public Map<Integer, SampleData> getSamples() {
	return new HashMap<>();
    }

    /** The images of the song */
    public Map<Integer, File> getImages() {
	return new HashMap<>();
    }

    /** a bpm representing the whole song.
    *** doesn't need to be exact, just for info */
    public abstract double getBPM();

    /** the number of notes in the song */
    public abstract int getNoteCount();

    /** the duration in seconds */
    public abstract int getDuration();

    /** a image cover, representing the song */
    public abstract BufferedImage getCover();
    
    /** this should return the list of events from this chart at this rank */
    public abstract EventList getEvents();
    
    /** return the cover image name without extension or null if there is no cover name */
    public String getCoverName() {
	if(coverName == null) return null;

	int dot = coverName.lastIndexOf(".");
	return coverName.substring(0, dot);
    }

    /** Return true if the chart has a cover */
    public boolean hasCover() {
	return imageCover != null;
    }

    /** Get the sample index of the chart */
    public Map<Integer, String> getSampleIndex() {
	return sampleIndex;
    }

    /** Get the image index of the chart */
    public Map<Integer, String> getBgaIndex() {
	return bgaIndex;
    }
    
    /** Copy the sample files to another directory */
    public void copySampleFiles(File directory) throws IOException {
	Collection<SampleData> samples = getSamples().values();
	if(samples.isEmpty()) return;
	for(SampleData ad : samples) {
	    ad.copyToFolder(directory);
	}
    }

    public void copyBgaFiles(File directory) throws IOException {
	Collection<File> images = getImages().values();
	if(images.isEmpty()) return;
	for(File f : images) {
	    File out = new File(directory, f.getName());
	    if(!out.exists()) {
		try (FileInputStream fis = new FileInputStream(f);
		     FileOutputStream fos = new FileOutputStream(out)) {
		    ByteHelper.copyTo(fis, fos);
		}
	    }
	}
    }

    public int compareTo(Chart c)
    {
        return getLevel() - c.getLevel();
    }

    @Override
    public boolean equals(Object obj) {
	if (this == obj) return true;
	if (!(obj instanceof Chart)) return false;
	Chart other = (Chart) obj;
	return getLevel() == other.getLevel() &&
	       getKeys() == other.getKeys() &&
	       getPlayers() == other.getPlayers() &&
	       getTitle().equals(other.getTitle()) &&
	       getArtist().equals(other.getArtist());
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 31 * hash + getLevel();
	hash = 31 * hash + getKeys();
	hash = 31 * hash + getPlayers();
	hash = 31 * hash + getTitle().hashCode();
	hash = 31 * hash + getArtist().hashCode();
	return hash;
    }
    
    public BufferedImage getNoImage()
    {
	URL u = Chart.class.getResource("/resources/no_image.png"); //TODO Change this
	if(u == null) return null;
	
	try {
	    return ImageIO.read(new File(u.toURI()));
	} catch (Exception ex) {
	    Logger.global.log(Level.WARNING, "Someone deleted or renamed my no_image image file :_ {0}", ex.getMessage());
	}
	return null;
    }
}
