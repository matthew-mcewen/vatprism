package net.marvk.fs.vatsim.map.data;

import net.marvk.fs.vatsim.map.data.airspace.TraconBoundary;
import net.marvk.fs.vatsim.map.data.airspace.VgPosition;
import net.marvk.fs.vatsim.map.data.event.Event;

public class IcaoVisitor extends DefaultingDataVisitor<String> {
    public IcaoVisitor(final String defaultValue) {
        super(defaultValue);
    }

    @Override
    public String visit(final Airport airport) {
        return airport.getIcao();
    }

    @Override
    public String visit(final FlightInformationRegionBoundary flightInformationRegionBoundary) {
        return flightInformationRegionBoundary.getIcao();
    }

    @Override
    public String visit(final UpperInformationRegion upperInformationRegion) {
        return upperInformationRegion.getIcao();
    }

    @Override
    public String visit(final TraconBoundary traconBoundary) {
        return traconBoundary.getId();
    }

    @Override
    public String visit(final VgPosition vatglassesPosition) {
        return vatglassesPosition.getDisplayName();
    }

    @Override
    public String visit(final Event event) {
        return event.getName();
    }
}

