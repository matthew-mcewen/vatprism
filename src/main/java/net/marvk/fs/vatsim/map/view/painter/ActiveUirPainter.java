package net.marvk.fs.vatsim.map.view.painter;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.Controller;
import net.marvk.fs.vatsim.map.data.UpperInformationRegion;
import net.marvk.fs.vatsim.map.view.map.MapVariables;

import java.util.Collection;
import java.util.List;

public class ActiveUirPainter extends CompositeMapPainter<UpperInformationRegion> {
    @MetaPainter("UIR")
    private final UirPainter uirPainter;

    public ActiveUirPainter(final MapVariables mapVariables) {
        this.uirPainter = new UirPainter(mapVariables, Color.DARKCYAN, false);
    }

    @Override
    protected Collection<? extends Painter<?>> painters() {
        return List.of(uirPainter);
    }

    @Override
    public void afterAllRender() {
        uirPainter.afterAllRender();
    }

    @Override
    public void paint(final GraphicsContext c, final UpperInformationRegion uir) {
        if (hasUncoveredControllers(uir)) {
            this.uirPainter.paint(c, uir);
        }
    }

    private static boolean hasUncoveredControllers(final UpperInformationRegion uir) {
        for (final Controller controller : uir.getControllers()) {
            if (!controller.isAirspaceBoundaryMatched()) {
                return true;
            }
        }

        return false;
    }
}
