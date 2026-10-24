package tw.mmunki.escape;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Escape {
    public static final String MOD_ID = "escape";
    public static final String VERSION = "1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Escape() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
