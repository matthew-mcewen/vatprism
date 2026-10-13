package net.marvk.fs.vatsim.map.data.airspace;

/**
 * The parts of an online controller that are needed to match it to airspace data.
 */
public interface AtcStation {
    String getCallsign();

    String getFrequency();
}
