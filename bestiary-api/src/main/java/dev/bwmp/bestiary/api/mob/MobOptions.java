package dev.bwmp.bestiary.api.mob;

/**
 * Per-mob switches that are not attributes.
 * <p>
 * {@link #despawn()} maps to {@code LivingEntity#setRemoveWhenFarAway}, the
 * {@code PersistenceRequired} NBT flag — <b>not</b> {@code Entity#setPersistent},
 * which is a different flag controlling whether the entity is written to the
 * chunk on unload and whose name invites exactly the wrong reach. Setting that
 * one false would delete the mob permanently.
 */
public final class MobOptions {

    public static final MobOptions DEFAULT = builder().build();

    private final boolean despawn;
    private final boolean preventOtherDrops;
    private final boolean preventMobKillDrops;
    private final boolean silent;
    private final boolean collidable;
    private final boolean alwaysShowName;
    private final boolean glowing;
    private final boolean invulnerable;
    private final boolean gravity;
    private final boolean ai;
    private final boolean preventRandomEquipment;
    private final boolean preventSunburn;
    private final boolean digOutOfGround;

    private MobOptions(Builder builder) {
        this.despawn = builder.despawn;
        this.preventOtherDrops = builder.preventOtherDrops;
        this.preventMobKillDrops = builder.preventMobKillDrops;
        this.silent = builder.silent;
        this.collidable = builder.collidable;
        this.alwaysShowName = builder.alwaysShowName;
        this.glowing = builder.glowing;
        this.invulnerable = builder.invulnerable;
        this.gravity = builder.gravity;
        this.ai = builder.ai;
        this.preventRandomEquipment = builder.preventRandomEquipment;
        this.preventSunburn = builder.preventSunburn;
        this.digOutOfGround = builder.digOutOfGround;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Default false: a custom mob that vanishes because a player walked 40 blocks away is never what was wanted. */
    public boolean despawn() {
        return despawn;
    }

    public boolean preventOtherDrops() {
        return preventOtherDrops;
    }

    public boolean preventMobKillDrops() {
        return preventMobKillDrops;
    }

    public boolean silent() {
        return silent;
    }

    public boolean collidable() {
        return collidable;
    }

    public boolean alwaysShowName() {
        return alwaysShowName;
    }

    public boolean glowing() {
        return glowing;
    }

    public boolean invulnerable() {
        return invulnerable;
    }

    public boolean gravity() {
        return gravity;
    }

    public boolean ai() {
        return ai;
    }

    public boolean preventRandomEquipment() {
        return preventRandomEquipment;
    }

    public boolean preventSunburn() {
        return preventSunburn;
    }

    public boolean digOutOfGround() {
        return digOutOfGround;
    }

    public static final class Builder {

        private boolean despawn;
        private boolean preventOtherDrops = true;
        private boolean preventMobKillDrops;
        private boolean silent;
        private boolean collidable = true;
        private boolean alwaysShowName;
        private boolean glowing;
        private boolean invulnerable;
        private boolean gravity = true;
        private boolean ai = true;
        private boolean preventRandomEquipment = true;
        private boolean preventSunburn = true;
        private boolean digOutOfGround = true;

        public Builder despawn(boolean value) {
            this.despawn = value;
            return this;
        }

        public Builder preventOtherDrops(boolean value) {
            this.preventOtherDrops = value;
            return this;
        }

        public Builder preventMobKillDrops(boolean value) {
            this.preventMobKillDrops = value;
            return this;
        }

        public Builder silent(boolean value) {
            this.silent = value;
            return this;
        }

        public Builder collidable(boolean value) {
            this.collidable = value;
            return this;
        }

        public Builder alwaysShowName(boolean value) {
            this.alwaysShowName = value;
            return this;
        }

        public Builder glowing(boolean value) {
            this.glowing = value;
            return this;
        }

        public Builder invulnerable(boolean value) {
            this.invulnerable = value;
            return this;
        }

        public Builder gravity(boolean value) {
            this.gravity = value;
            return this;
        }

        public Builder ai(boolean value) {
            this.ai = value;
            return this;
        }

        public Builder preventRandomEquipment(boolean value) {
            this.preventRandomEquipment = value;
            return this;
        }

        public Builder preventSunburn(boolean value) {
            this.preventSunburn = value;
            return this;
        }

        public Builder digOutOfGround(boolean value) {
            this.digOutOfGround = value;
            return this;
        }

        public MobOptions build() {
            return new MobOptions(this);
        }
    }
}
