package pl.radar;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Pole tekstowe w stylu GUI moda (zaokraglone, akcent przy fokusie). */
public class RField extends TextFieldWidget {
    public RField(TextRenderer tr, int x, int y, int w, int h) {
        super(tr, x, y, w, h, Text.empty());
        setDrawsBackground(false);
        setEditableColor(0xFFE8EAF0);
    }

    @Override
    public void renderWidget(DrawContext c, int mx, int my, float delta) {
        int accent = RadarConfig.I.accent();
        Ui.rrect(c, getX(), getY(), getWidth(), getHeight(), 4, isFocused() ? Ui.solid(accent) : 0xFF3A3F4D);
        Ui.rrect(c, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 3, 0xFF14161B);
        int ox = getX(), oy = getY(), ow = getWidth();
        setX(ox + 6);
        setY(oy + (getHeight() - 8) / 2);
        setWidth(ow - 12);
        super.renderWidget(c, mx, my, delta);
        setX(ox);
        setY(oy);
        setWidth(ow);
    }
}
