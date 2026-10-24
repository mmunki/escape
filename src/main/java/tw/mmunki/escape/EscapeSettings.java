package tw.mmunki.escape;

public final class EscapeSettings {
    public boolean enabled = true;
    public int cycleSeconds = 300;
    public int activeSeconds = 120;
    public int firstDelaySeconds = 60;
    public boolean disableInCreative = true;
    public boolean announce = true;
    public double maxHealth = 20.0D;
    public double movementSpeed = 0.30D;
    public double attackDamage = 9.0D;
    public double critChance = 0.35D;
    public double critMultiplier = 1.5D;
    public int retrySeconds = 30;

    public EscapeSettings normalized() {
        EscapeSettings copy = new EscapeSettings();
        copy.enabled = enabled;
        copy.cycleSeconds = clamp(cycleSeconds, 30, 3600);
        copy.activeSeconds = clamp(activeSeconds, 10, copy.cycleSeconds - 10);
        copy.firstDelaySeconds = clamp(firstDelaySeconds, 0, copy.cycleSeconds);
        copy.disableInCreative = disableInCreative;
        copy.announce = announce;
        copy.maxHealth = clamp(maxHealth, 1.0D, 100.0D);
        copy.movementSpeed = clamp(movementSpeed, 0.10D, 0.60D);
        copy.attackDamage = clamp(attackDamage, 1.0D, 40.0D);
        copy.critChance = clamp(critChance, 0.0D, 1.0D);
        copy.critMultiplier = clamp(critMultiplier, 1.0D, 4.0D);
        copy.retrySeconds = clamp(retrySeconds, 5, 600);
        return copy;
    }

    public int cycleTicks() {
        return clamp(cycleSeconds, 30, 3600) * 20;
    }

    public int activeTicks() {
        return clamp(activeSeconds, 10, 3599) * 20;
    }

    public int firstDelayTicks() {
        return clamp(firstDelaySeconds, 0, 3600) * 20;
    }

    public int retryTicks() {
        return clamp(retrySeconds, 5, 600) * 20;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
