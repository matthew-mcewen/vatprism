package net.marvk.fs.vatsim.map.data.airspace;

import javafx.scene.paint.Color;

/**
 * A {@link VgSubSector} which is currently controlled by an online position.
 */
public class ActiveSubSector {
    private final VgSubSector subSector;
    private final VgPosition owner;
    private final String ownerCallsign;
    private final Color colour;

    ActiveSubSector(final VgSubSector subSector, final VgPosition owner, final String ownerCallsign, final Color colour) {
        this.subSector = subSector;
        this.owner = owner;
        this.ownerCallsign = ownerCallsign;
        this.colour = colour;
    }

    public VgSubSector getSubSector() {
        return subSector;
    }

    public VgPosition getOwner() {
        return owner;
    }

    /**
     * @return the logon callsign of the controller working the owning position
     */
    public String getOwnerCallsign() {
        return ownerCallsign;
    }

    public Color getColour() {
        return colour;
    }
}
