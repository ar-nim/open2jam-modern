/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.open2jam.sound;

/**
 * Exception thrown when sound system initialization fails.
 *
 * @author dttvb
 */
public class SoundSystemInitException extends SoundSystemException {

    public SoundSystemInitException(Throwable thrwbl) {
        super(thrwbl);
    }

    public SoundSystemInitException(String message) {
        super(message);
    }

    public SoundSystemInitException(String message, Throwable thrwbl) {
        super(message, thrwbl);
    }
}
