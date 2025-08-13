package club.adstream.skyjumper;

import android.graphics.Color;

public class Skin {
    public final int id;
    public final String name;
    public final int color;
    public final int price;
    public final boolean free;

    public Skin(int id, String name, int color, int price, boolean free) {
        this.id = id; this.name = name; this.color = color; this.price = price; this.free = free;
    }

    public static Skin[] catalog() {
        return new Skin[]{
                new Skin(0, "Classic", Color.rgb(255,140,0),   0,  true),
                new Skin(1, "Lime",    Color.rgb(140,255,100), 50, false),
                new Skin(2, "Aqua",    Color.rgb(80,220,255),  50, false),
                new Skin(3, "Rose",    Color.rgb(255,120,160), 75, false),
                new Skin(4, "Royal",   Color.rgb(120,105,255), 100,false),
                new Skin(5, "Gold",    Color.rgb(255,215,0),   150,false)
        };
    }
}
