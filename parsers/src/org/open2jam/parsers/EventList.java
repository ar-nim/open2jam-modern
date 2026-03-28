package org.open2jam.parsers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.open2jam.parsers.Event.Channel;
import org.open2jam.parsers.utils.Logger;

/**
 * It's just an ArrayList<Event> extended to be able to make some funny things with it
 * @author CdK
 */
@SuppressWarnings("java:S2160")  // Collection wrapper - equality based on list contents
public class EventList extends ArrayList<Event> {

    private int playableNotes = 0;

    public enum FixMethod {NONE, O2JAM, OPEN2JAM}

    public int getPlayableNotes() {
        return playableNotes;
    }
    
    /**
     * WARNING, USING THIS CAN CHANGE THE CHART
     *
     * Use this only if you don't want to deal with broken longnotes or longnotes in the autoplay channel
     * If you want to write a Editor with this lib, don't use it because it changes a lot of things in the events
     */
    public void fixEventList(FixMethod method, boolean fixAutoplayLongnotes) {

        if (!fixAutoplayLongnotes && method == FixMethod.NONE)
            return;

        EnumSet<Event.Channel> noteChannelsSet = EnumSet.of(
            Event.Channel.NOTE_1, Event.Channel.NOTE_2, Event.Channel.NOTE_3,
            Event.Channel.NOTE_4, Event.Channel.NOTE_5, Event.Channel.NOTE_6, Event.Channel.NOTE_7
        );

        Map<Event.Channel, Event> lnchan = new EnumMap<>(Event.Channel.class);

        ListIterator<Event> it = this.listIterator();
        while (it.hasNext()) {
            Event e = it.next();

            if (noteChannelsSet.contains(e.getChannel())) {
                switch (method) {
                    case OPEN2JAM:
                        fixOPEN2JAM(it, e, lnchan);
                        break;
                    case O2JAM:
                        fixO2JAM(it, e, lnchan);
                        break;
                    default:
                        playableNotes++;
                        break;
                }
            }

            if (fixAutoplayLongnotes && e.getChannel() == Event.Channel.AUTO_PLAY) {
                if (e.getFlag() == Event.Flag.RELEASE) {
                    it.remove();
                    continue;
                }
                e.flag = Event.Flag.NONE;
            }
        }

        Logger.global.log(Level.INFO, "Total playable notes: {0}", playableNotes);
    }
    
    
    private void fixOPEN2JAM(ListIterator<Event> it, Event e, Map<Event.Channel, Event> lnchan) {
        Event.Channel c = e.getChannel();
        switch (e.getFlag()) {
            case NONE:
                if (lnchan.containsKey(c)) {
                    fixHoldNote(it, e, lnchan.get(c));
                    lnchan.remove(c);
                }
                playableNotes++;
                break;
            case HOLD:
                if (lnchan.containsKey(c)) {
                    Event evt = lnchan.get(c);
                    Logger.global.log(Level.WARNING,
                        "Broken HOLD event! @ {0} ({1}): But converted to NONE because the next event is another HOLD :/",
                        new Object[] {evt.getTotalPosition(), evt.getValue()}
                    );
                    evt.flag = Event.Flag.NONE;
                }
                playableNotes++;
                lnchan.put(c, e);
                break;
            case RELEASE:
                if (!lnchan.containsKey(c)) {
                    fixReleaseNote(it, e);
                } else {
                    playableNotes++;
                }
                lnchan.remove(c);
                break;
            default:
                playableNotes++;
                break;
        }
    }
    
    
    /* sanity check
     * now we need to check the events to see if its consistent,
     * I identified 3 types of inconsistence so far:
     *
     * 1. tap note on top of an on-going long note
     * 2. starting a long note on an on-going long note
     * 3. end long note without an on-going long note
     *
     * as far as I have tested o2mania simply ignore these 3 cases
     */
    private void fixO2JAM(ListIterator<Event> it, Event e, Map<Event.Channel, Event> lnchan) {
        Event.Channel c = e.getChannel();
        switch (e.getFlag()) {
            case NONE:
                if (lnchan.containsKey(c)) {
                    e.setChannel(Event.Channel.AUTO_PLAY);
                } else {
                    playableNotes++;
                }
                break;

            case HOLD:
                if (lnchan.containsKey(c)) {
                    e.setChannel(Event.Channel.AUTO_PLAY);
                } else {
                    lnchan.put(c, null);
                    playableNotes++;
                }
                break;

            case RELEASE:
                if (!lnchan.containsKey(c)) {
                    it.remove();
                } else {
                    lnchan.remove(c);
                    playableNotes++;
                }
                break;

            default:
                playableNotes++;
                break;
        }
    }
    
    /**
     * This method will try to fix all the broken hold events. Rules:
     * <ol>
     * <li>We will iterate forward the list from the broken hold event and:
     *	    <ol>
     *	    <li>If we found a hold value, stop and use the last iterated event.
     *		<ul>
     *		<li>The last event is a NONE, use it as a release event</li>
     *		<li>The last event isn't a NONE, use the caller event</li>
     *		</ul>
     *	    </li>
     *	    <li>If we found a event with the same value, use it as a release event</li>
     *	    <li>If not, add the event to a possible autoplay move</li>
     *	    </ol>
     * </li>
     * <li>Finally:
     *	    <ol>
     *	    <li>If it's found, move every event in the toAutoplay list to autoplay channel</li>
     *	    <li>If it's not found, use the caller event as a release event</li>
     *	    </ol>
     * </li>
     * </ol>
     */
    private void fixHoldNote(ListIterator<Event> it, Event e, Event eHold) {
        Event.Channel c = eHold.getChannel();
        double value = eHold.getValue();
        Logger.global.log(Level.WARNING, "Broken HOLD event! @ {0} ({1}): ",
            new Object[] {eHold.getTotalPosition(), value});

        EventList toAutoplay = new EventList();
        toAutoplay.add(e);

        ListIterator<Event> it2 = this.listIterator(it.nextIndex());
        Event lastEvt = e;
        boolean found = false;

        while (!found && it2.hasNext()) {
            Event evt = it2.next();
            found = processHoldEvent(evt, c, value, toAutoplay, lastEvt);
            lastEvt = evt;
        }

        if (found) {
            moveEventsToAutoplay(toAutoplay);
        } else {
            toAutoplay.clear();
            e.flag = Event.Flag.RELEASE;
        }
    }

    /**
     * Process a single event during hold note fixing.
     * @return true if the hold note has been fixed, false otherwise
     */
    private boolean processHoldEvent(Event evt, Event.Channel c, double value,
                                     EventList toAutoplay, Event lastEvt) {
        if (c != evt.getChannel()) {
            return false;
        }

        if (evt.flag == Event.Flag.HOLD) {
            handleHoldInWay(lastEvt, toAutoplay);
            return (lastEvt.flag == Event.Flag.NONE);
        } else if (value == evt.getValue()) {
            evt.flag = Event.Flag.RELEASE;
            Logger.global.log(Level.INFO, "Fixed with same value @ {0} ({1}) It''s a {2}",
                new Object[] {evt.getTotalPosition(), value, evt.flag});
            return true;
        } else {
            toAutoplay.add(evt);
            Logger.global.log(Level.INFO, "to Autoplay ({0}): {1} {2}",
                new Object[] {evt.getValue(), evt.flag, evt.getTotalPosition()});
            return false;
        }
    }

    private void handleHoldInWay(Event lastEvt, EventList toAutoplay) {
        if (lastEvt.flag == Event.Flag.NONE) {
            lastEvt.flag = Event.Flag.RELEASE;
            Logger.global.log(Level.INFO, "There is a HOLD in the way, converting to last known {0}... @ {1} OK",
                new Object[] {lastEvt.flag, lastEvt.getTotalPosition()});
            if (toAutoplay.contains(lastEvt)) {
                Logger.global.log(Level.INFO, "Removing from autoplay {0}", lastEvt.getTotalPosition());
                toAutoplay.remove(lastEvt);
            }
        } else {
            Logger.global.log(Level.INFO, "There is a HOLD in the way, converting to last known {0}... @ {1} {2} FAILED",
                new Object[] {lastEvt.flag, lastEvt.getTotalPosition(), lastEvt.flag});
        }
    }

    private void moveEventsToAutoplay(EventList toAutoplay) {
        for (Event evt : toAutoplay) {
            evt.setChannel(Event.Channel.AUTO_PLAY);
            Logger.global.log(Level.INFO, "Moving to autoplay {0}", evt.getTotalPosition());
            playableNotes--;
        }
    }
 
    /**
     * This method will try to fix all the broken release events. Rules:
     * <ol>
     * <li>We'll iterate backwards the list from the broken release event, and:
     *	    <ol>
     *	    <li>If we find a event with the same value:
     *		<ul>
     *		<li>The found event isn't a release event, use it as a hold event</li>
     *		<li>The found event is a release event, remove the broken release event form the list
     *		  because it would break other events</li>
     *		</ul>
     *	    </li>
     *	    <li>If not:
     *		<ul>
     *		<li>If the event is a hold use it, even if their values differ
     *		<li>If not, add the event to a possible autoplay move</li>
     *		</ul>
     *	    </li>
     *	    </ol>
     * </li>
     * <li>Finally:
     *	    <ol>
     *	    <li>If it's found, move every event in the toAutoplay list to autoplay channel</li>
     *	    <li>If it's not found, remove the broken release event from the list</li>
     *	    </ol>
     * </li>
     * </ol>
     */
    private void fixReleaseNote(ListIterator<Event> it, Event e) {
        Event.Channel c = e.getChannel();
        double value = e.getValue();
        Logger.global.log(Level.WARNING, "Broken RELEASE event! @ {0} ({1}): ",
            new Object[] {e.getTotalPosition(), value});

        EventList toAutoplay = new EventList();
        ListIterator<Event> it2 = this.listIterator(it.previousIndex());
        boolean found = false;

        while (!found && it2.hasPrevious()) {
            Event evt = it2.previous();
            found = processReleaseEvent(evt, c, value, toAutoplay);
        }

        if (found) {
            playableNotes++;
            moveEventsToAutoplay(toAutoplay);
        } else {
            Logger.global.log(Level.INFO, "Not fixed :(");
            it.remove();
            playableNotes--;
        }
    }

    /**
     * Process a single event during release note fixing.
     * @return true if the release note has been fixed, false otherwise
     */
    private boolean processReleaseEvent(Event evt, Event.Channel c, double value,
                                        EventList toAutoplay) {
        if (c != evt.getChannel()) {
            return false;
        }

        if (evt.getValue() == value) {
            if (evt.flag != Event.Flag.RELEASE) {
                evt.flag = Event.Flag.HOLD;
                Logger.global.log(Level.INFO, "Candidate is a {0}, fixed :D", evt.flag);
                return true;
            } else {
                Logger.global.log(Level.INFO, "Candidate is a {0}, not fixed :(", evt.flag);
                return false;
            }
        } else if (evt.flag == Event.Flag.HOLD) {
            Logger.global.log(Level.INFO, "Found a HOLD with different value, fixed :D");
            return true;
        } else {
            toAutoplay.add(evt);
            Logger.global.log(Level.INFO, "to Autoplay ({0}): {1} {2}",
                new Object[] {evt.getValue(), evt.flag, evt.getTotalPosition()});
            return false;
        }
    }
    
    /**
     * This method will return a map ordered by measures and a list of events for each measure
     * @return A map with ordered measures => list of events
     */
    public Map<Integer, EventList> getEventsPerMeasure() {
        Map<Integer, EventList> epm = new TreeMap<>();

        for (Event e : this) {
            if (!epm.containsKey(e.getMeasure()))
                epm.put(e.getMeasure(), new EventList());

            epm.get(e.getMeasure()).add(e);
        }

        return epm;
    }

    /**
     * This method will return a map with channels and a list of events for each channel
     * @return A map with channels => list of events
     */
    public Map<Event.Channel, EventList> getEventsPerChannel() {
        Map<Event.Channel, EventList> epc = new EnumMap<>(Event.Channel.class);

        for (Event e : this) {
            if (!epc.containsKey(e.getChannel()))
                epc.put(e.getChannel(), new EventList());
            epc.get(e.getChannel()).add(e);
        }

        return epc;
    }

    /**
     * This method will return only the normal notes
     * @return A list with all the normal notes
     */
    public EventList getOnlyNormalNotes() {
        EventList nn = new EventList();

        for (Event e : this) {
            if (e.getFlag().equals(Event.Flag.NONE))
                nn.add(e);
        }

        return nn;
    }

    /**
     * This method will return only the long notes
     * @return A list with all the long notes
     */
    public EventList getOnlyLongNotes() {
        EventList ln = new EventList();

        for (Event e : this) {
            if (e.getFlag().equals(Event.Flag.HOLD) || e.getFlag().equals(Event.Flag.RELEASE))
                ln.add(e);
        }

        return ln;
    }

    /**
     * This method will return all the events on the selected channel
     * @param channel The selected channel
     * @return A list of events in that channel
     */
    public EventList getEventsFromThisChannel(Channel channel) {
        EventList eftc = new EventList();

        for (Event e : this) {
            if (e.getChannel().equals(channel))
                eftc.add(e);
        }

        return eftc;
    }

    /**
     * This method will mirrorize the notes in the EventList
     * TODO ADD P2 SUPPORT
     */
    public void channelMirror() {
        Iterator<Event> it = this.iterator();

        while (it.hasNext()) {
            Event e = it.next();
            e.setChannel(Event.Channel.mirrorChannel(e.getChannel()));
        }
    }

    /**
     * This method will shuffle the notes in the EventList
     * TODO ADD P2 SUPPORT
     */
    public void channelShuffle() {
        List<Event.Channel> channelSwap = new ArrayList<>();

        Collections.addAll(channelSwap, Event.Channel.playableChannels());
        Collections.shuffle(channelSwap);

        Iterator<Event> it = this.iterator();

        while (it.hasNext()) {
            Event e = it.next();
            switch (e.getChannel()) {
                case NOTE_1: e.setChannel(channelSwap.get(0)); break;
                case NOTE_2: e.setChannel(channelSwap.get(1)); break;
                case NOTE_3: e.setChannel(channelSwap.get(2)); break;
                case NOTE_4: e.setChannel(channelSwap.get(3)); break;
                case NOTE_5: e.setChannel(channelSwap.get(4)); break;
                case NOTE_6: e.setChannel(channelSwap.get(5)); break;
                case NOTE_7: e.setChannel(channelSwap.get(6)); break;
                default: break;
            }
        }
    }

    /**
     * This method will randomize the notes in the EventList
     * o2jam randomize the pattern each measure unless a longnote is in between measures
     * This implementation keeps the randomization of the previous measure if that happens
     */
    public void channelRandom() {
        List<Event.Channel> channelSwap = new ArrayList<>();

        Collections.addAll(channelSwap, Event.Channel.playableChannels());
        Collections.shuffle(channelSwap);

        EnumMap<Event.Channel, Event.Channel> lnMap = new EnumMap<>(Event.Channel.class);

        int lastMeasure = -1;

        Iterator<Event> it = this.iterator();
        while (it.hasNext()) {
            Event e = it.next();

            if (e.getMeasure() > lastMeasure) {
                if (lnMap.isEmpty())
                    Collections.shuffle(channelSwap);
                lastMeasure = e.getMeasure();
            }

            switch (e.getChannel()) {
                case NOTE_1:
                    setRandomChannel(e, lnMap, channelSwap.get(0));
                    break;
                case NOTE_2:
                    setRandomChannel(e, lnMap, channelSwap.get(1));
                    break;
                case NOTE_3:
                    setRandomChannel(e, lnMap, channelSwap.get(2));
                    break;
                case NOTE_4:
                    setRandomChannel(e, lnMap, channelSwap.get(3));
                    break;
                case NOTE_5:
                    setRandomChannel(e, lnMap, channelSwap.get(4));
                    break;
                case NOTE_6:
                    setRandomChannel(e, lnMap, channelSwap.get(5));
                    break;
                case NOTE_7:
                    setRandomChannel(e, lnMap, channelSwap.get(6));
                    break;
                default:
                    break;
            }
        }
    }

    private void setRandomChannel(Event e, EnumMap<Event.Channel, Event.Channel> lnMap, Event.Channel random) {
        Event.Channel c = random;

        if (e.getFlag() == Event.Flag.HOLD || e.getFlag() == Event.Flag.RELEASE) {
            if (!lnMap.containsKey(e.getChannel()))
                lnMap.put(e.getChannel(), c);
            else
                c = lnMap.remove(e.getChannel());
        } else if (e.getFlag() == Event.Flag.NONE) {
            c = lnMap.containsValue(c) ? Event.Channel.NONE : c;
        }

        if (c == null) {
            Logger.global.log(Level.WARNING, "FUCK THIS RANDOMNESS! I mean... channel null :/");
            c = random;
        }

        e.setChannel(c);
    }
}
