package dev.bwmp.bestiary.importer;

import dev.bwmp.bestiary.api.skill.ParameterSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * The name-mapping table the importer is mostly made of.
 * <p>
 * MythicMobs' {@code - damage{amount=10} @Target ~onTimer:20 ?health&lt;0.5} is
 * the same four-part grammar with different spellings, so the shorthand parser
 * is already most of the machinery. What is left is this table and a writer.
 * <p>
 * Anything absent here is written through unchanged and reported, because a
 * silent partial import is worse than a loud one.
 */
final class MythicNames {

    static final Map<String, String> MECHANICS = new HashMap<>();
    static final Map<String, String> TARGETERS = new HashMap<>();
    static final Map<String, String> CONDITIONS = new HashMap<>();

    static {
        // Damage and health
        put(MECHANICS, "damage", "damage");
        put(MECHANICS, "d", "damage");
        put(MECHANICS, "percentdamage", "percent_damage");
        put(MECHANICS, "heal", "heal");
        put(MECHANICS, "healpercent", "heal_percent");
        put(MECHANICS, "sethealth", "set_health");
        put(MECHANICS, "ignite", "ignite");
        put(MECHANICS, "extinguish", "extinguish");
        put(MECHANICS, "kill", "kill");
        put(MECHANICS, "suicide", "suicide");
        put(MECHANICS, "feed", "feed");
        put(MECHANICS, "damagearmor", "damage_armor");

        // Movement
        put(MECHANICS, "velocity", "velocity");
        put(MECHANICS, "leap", "leap");
        put(MECHANICS, "throw", "throw");
        put(MECHANICS, "pull", "pull");
        put(MECHANICS, "push", "push");
        put(MECHANICS, "teleport", "teleport");
        put(MECHANICS, "tp", "teleport");
        put(MECHANICS, "teleportto", "teleport_to");
        put(MECHANICS, "randomteleport", "random_teleport");
        put(MECHANICS, "jump", "jump");
        put(MECHANICS, "look", "look");
        put(MECHANICS, "lookat", "look");
        put(MECHANICS, "mount", "mount");
        put(MECHANICS, "dismount", "dismount");
        put(MECHANICS, "setspeed", "set_speed");
        put(MECHANICS, "setgravity", "set_gravity");

        // Summoning
        put(MECHANICS, "summon", "summon");
        put(MECHANICS, "remove", "remove");
        put(MECHANICS, "doppleganger", "doppelganger");
        put(MECHANICS, "doppelganger", "doppelganger");
        put(MECHANICS, "setowner", "set_owner");
        put(MECHANICS, "setparent", "set_parent");
        put(MECHANICS, "totem", "totem");

        // Projectiles
        put(MECHANICS, "projectile", "projectile");
        put(MECHANICS, "missile", "missile");
        put(MECHANICS, "shootfireball", "shoot_fireball");
        put(MECHANICS, "shootpotion", "shoot_potion");
        put(MECHANICS, "shootskull", "shoot_skull");
        put(MECHANICS, "shoot", "shoot_arrow");
        put(MECHANICS, "arrowvolley", "bullet_shape");

        // Presentation
        put(MECHANICS, "effectparticles", "particle");
        put(MECHANICS, "particles", "particle");
        put(MECHANICS, "e:particles", "particle");
        put(MECHANICS, "particlering", "ring");
        put(MECHANICS, "particlesphere", "sphere");
        put(MECHANICS, "particleline", "line");
        put(MECHANICS, "particleorbital", "particle_orbital");
        put(MECHANICS, "particletornado", "particle_tornado");
        put(MECHANICS, "particlebox", "particle_box");
        put(MECHANICS, "sound", "sound");
        put(MECHANICS, "e:sound", "sound");
        put(MECHANICS, "stopsound", "stop_sound");
        put(MECHANICS, "explosion", "explosion");
        put(MECHANICS, "fakeexplosion", "fake_explosion");
        put(MECHANICS, "lightning", "lightning");
        put(MECHANICS, "fakelightning", "fake_lightning");
        put(MECHANICS, "firework", "firework");
        put(MECHANICS, "hologram", "hologram");
        put(MECHANICS, "equip", "equip");
        put(MECHANICS, "blockmask", "block_mask");
        put(MECHANICS, "blockunmask", "block_unmask");
        put(MECHANICS, "blockwave", "block_wave");
        put(MECHANICS, "setblock", "set_block");
        put(MECHANICS, "breakblock", "break_block");

        // Status
        put(MECHANICS, "potion", "potion");
        put(MECHANICS, "removepotion", "remove_potion");
        put(MECHANICS, "aura", "aura");
        put(MECHANICS, "removeaura", "remove_aura");
        put(MECHANICS, "setstance", "set_stance");
        put(MECHANICS, "setai", "set_ai");
        put(MECHANICS, "setfaction", "set_faction");
        put(MECHANICS, "setlevel", "set_level");
        put(MECHANICS, "setname", "set_name");
        put(MECHANICS, "settarget", "set_target");
        put(MECHANICS, "taunt", "taunt");
        put(MECHANICS, "threat", "modify_threat");
        put(MECHANICS, "setvariable", "set_variable");
        put(MECHANICS, "variablemath", "variable_math");

        // Flow
        put(MECHANICS, "skill", "skill");
        put(MECHANICS, "metaskill", "skill");
        put(MECHANICS, "delay", "delay");
        put(MECHANICS, "repeat", "repeat");
        put(MECHANICS, "randomskill", "random_skill");
        put(MECHANICS, "cancelevent", "cancel_event");
        put(MECHANICS, "stop", "stop");
        put(MECHANICS, "signal", "signal");

        // Player-facing and progression
        put(MECHANICS, "message", "message");
        put(MECHANICS, "m", "message");
        put(MECHANICS, "actionmessage", "actionbar");
        put(MECHANICS, "title", "title");
        put(MECHANICS, "sendtitle", "title");
        put(MECHANICS, "command", "console_command");
        put(MECHANICS, "givitem", "give_item");
        put(MECHANICS, "giveitem", "give_item");
        put(MECHANICS, "dropitem", "drop_item");
        put(MECHANICS, "droptable", "drop_table");
        put(MECHANICS, "giveexp", "give_exp");
        put(MECHANICS, "currency", "currency");

        // Targeters
        put(TARGETERS, "self", "self");
        put(TARGETERS, "caster", "self");
        put(TARGETERS, "target", "target");
        put(TARGETERS, "t", "target");
        put(TARGETERS, "trigger", "trigger");
        put(TARGETERS, "nearestplayer", "nearest_player");
        put(TARGETERS, "playersinradius", "players_in_radius");
        put(TARGETERS, "pir", "players_in_radius");
        put(TARGETERS, "entitiesinradius", "entities_in_radius");
        put(TARGETERS, "eir", "entities_in_radius");
        put(TARGETERS, "mobsinradius", "mobs_in_radius");
        put(TARGETERS, "playersinring", "players_in_ring");
        put(TARGETERS, "playersincone", "players_in_cone");
        put(TARGETERS, "playersinworld", "players_in_world");
        put(TARGETERS, "owner", "owner");
        put(TARGETERS, "parent", "parent");
        put(TARGETERS, "children", "children");
        put(TARGETERS, "mount", "mount");
        put(TARGETERS, "passengers", "passengers");
        put(TARGETERS, "threattable", "threat_table");
        put(TARGETERS, "threattabletarget", "threat_table_top");
        put(TARGETERS, "selflocation", "self_location");
        put(TARGETERS, "targetlocation", "target_location");
        put(TARGETERS, "triggerlocation", "trigger_location");
        put(TARGETERS, "origin", "origin");
        put(TARGETERS, "forward", "forward");
        put(TARGETERS, "ring", "ring");
        put(TARGETERS, "sphere", "sphere");
        put(TARGETERS, "cone", "cone");
        put(TARGETERS, "line", "line");
        put(TARGETERS, "spiral", "spiral");
        put(TARGETERS, "blocksinradius", "blocks_in_radius");
        put(TARGETERS, "randomlocationnearorigin", "random_near_origin");

        // Conditions
        put(CONDITIONS, "health", "health");
        put(CONDITIONS, "healthpercent", "health_percent");
        put(CONDITIONS, "level", "level");
        put(CONDITIONS, "name", "name");
        put(CONDITIONS, "entitytype", "entity_type");
        put(CONDITIONS, "mobtype", "mob_type");
        put(CONDITIONS, "isplayer", "is_player");
        put(CONDITIONS, "isliving", "is_living");
        put(CONDITIONS, "iscaster", "is_caster");
        put(CONDITIONS, "hasaura", "has_aura");
        put(CONDITIONS, "stance", "stance");
        put(CONDITIONS, "faction", "faction");
        put(CONDITIONS, "distance", "distance");
        put(CONDITIONS, "lineofsight", "line_of_sight");
        put(CONDITIONS, "haspermission", "has_permission");
        put(CONDITIONS, "permission", "has_permission");
        put(CONDITIONS, "hasitem", "has_item");
        put(CONDITIONS, "holding", "holding");
        put(CONDITIONS, "wearing", "wearing");
        put(CONDITIONS, "food", "food");
        put(CONDITIONS, "onground", "on_ground");
        put(CONDITIONS, "inwater", "in_water");
        put(CONDITIONS, "inlava", "in_lava");
        put(CONDITIONS, "sneaking", "sneaking");
        put(CONDITIONS, "sprinting", "sprinting");
        put(CONDITIONS, "blocking", "blocking");
        put(CONDITIONS, "gliding", "gliding");
        put(CONDITIONS, "burning", "burning");
        put(CONDITIONS, "variable", "variable");
        put(CONDITIONS, "score", "score");
        put(CONDITIONS, "biome", "biome");
        put(CONDITIONS, "world", "world");
        put(CONDITIONS, "altitude", "altitude");
        put(CONDITIONS, "lightlevel", "light_level");
        put(CONDITIONS, "blocktype", "block_type");
        put(CONDITIONS, "day", "is_day");
        put(CONDITIONS, "night", "is_night");
        put(CONDITIONS, "moonphase", "moon_phase");
        put(CONDITIONS, "raining", "raining");
        put(CONDITIONS, "thundering", "thundering");
        put(CONDITIONS, "playersinradius", "players_in_radius_count");
        put(CONDITIONS, "region", "in_region");
        put(CONDITIONS, "inregion", "in_region");
        put(CONDITIONS, "chance", "chance");
    }

    private MythicNames() {
    }

    private static void put(Map<String, String> into, String mythic, String bestiary) {
        into.put(ParameterSpec.normalize(mythic), bestiary);
    }

    static String mechanic(String written) {
        return MECHANICS.get(ParameterSpec.normalize(written));
    }

    static String targeter(String written) {
        return TARGETERS.get(ParameterSpec.normalize(written));
    }

    static String condition(String written) {
        return CONDITIONS.get(ParameterSpec.normalize(written));
    }
}
