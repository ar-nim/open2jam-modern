/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.open2jam.sound;

/**
 * Exception thrown when a sound system error occurs.
 *
 * @author dttvb
 */
public class SoundSystemException extends Exception {

    public SoundSystemException(Throwable thrwbl) {
        super(thrwbl);
    }

    public SoundSystemException(String string) {
        super(string);
    }

    public SoundSystemException(String string, Throwable thrwbl) {
        super(string, thrwbl);
    }
}
