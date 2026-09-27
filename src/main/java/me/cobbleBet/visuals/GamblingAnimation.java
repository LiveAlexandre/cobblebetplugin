package me.cobbleBet.visuals;

/** Deterministic animation curves shared by the render loop and its checks. */
public final class GamblingAnimation {
    private GamblingAnimation() {}

    public static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public static double easeOut(double value) {
        return 1 - Math.pow(1 - clamp(value), 3);
    }

    public static double smooth(double value) {
        double t = clamp(value);
        return t * t * (3 - 2 * t);
    }

    public static int tier(boolean won, double multiplier) {
        if (!won || !Double.isFinite(multiplier)) return 0;
        return multiplier >= 10 ? 2 : multiplier > 3 ? 1 : 0;
    }

    public static int duration(boolean won, int tier) {
        return won ? 76 + Math.max(0, Math.min(2, tier)) * 16 : 58;
    }

    public static double opacity(int age, int duration) {
        if (age < 0 || age >= duration) return 0;
        return Math.min(easeOut((age + 1) / 8.0), smooth((duration - age) / 16.0));
    }

    public static double revealScale(int age) {
        double t = clamp(age / 14.0);
        double overshoot = 1.25;
        return Math.max(0.06, 1 + (overshoot + 1) * Math.pow(t - 1, 3) + overshoot * Math.pow(t - 1, 2));
    }

    public static double countedAmount(double amount, int age) {
        if (!Double.isFinite(amount) || amount < 0) return 0;
        return amount * easeOut(age / 28.0);
    }
}

