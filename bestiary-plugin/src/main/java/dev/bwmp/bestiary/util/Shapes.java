package dev.bwmp.bestiary.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Point generators for the shape mechanics.
 * <p>
 * Shared with the location targeters on purpose: shapes are usable both as
 * effect painters and as location targeters, and that only stays
 * true without duplication if one generator serves both. A {@code ring} that
 * paints particles and a {@code @ring} that places targets must produce the
 * same points, or a telegraph will not line up with the attack it telegraphs.
 */
public final class Shapes {

    private Shapes() {
    }

    public static List<Location> ring(Location centre, double radius, int points, double height) {
        List<Location> locations = new ArrayList<>(Math.max(1, points));
        for (int index = 0; index < points; index++) {
            double angle = 2 * Math.PI * index / points;
            locations.add(centre.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
        }
        return locations;
    }

    /** A filled horizontal disc, points spread evenly by area rather than by radius. */
    public static List<Location> disc(Location centre, double radius, int points, double height) {
        List<Location> locations = new ArrayList<>(Math.max(1, points));
        for (int index = 0; index < points; index++) {
            double distance = radius * Math.sqrt((index + 0.5d) / points);
            // The golden angle keeps successive points from lining up into
            // spokes, which is what a naive uniform angle produces.
            double angle = index * 2.399963229728653d;
            locations.add(centre.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance));
        }
        return locations;
    }

    /** A hollow sphere, points placed on a Fibonacci lattice. */
    public static List<Location> sphere(Location centre, double radius, int points) {
        List<Location> locations = new ArrayList<>(Math.max(1, points));
        for (int index = 0; index < points; index++) {
            double y = 1 - (index / (double) Math.max(1, points - 1)) * 2;
            double ringRadius = Math.sqrt(Math.max(0.0d, 1 - y * y));
            double angle = index * 2.399963229728653d;
            locations.add(centre.clone().add(Math.cos(angle) * ringRadius * radius,
                    y * radius, Math.sin(angle) * ringRadius * radius));
        }
        return locations;
    }

    public static List<Location> cone(Location origin, Vector direction, double length, double angleDegrees,
                                      int points) {
        List<Location> locations = new ArrayList<>(Math.max(1, points));
        Vector forward = direction.clone().normalize();
        Vector side = pickPerpendicular(forward);
        Vector up = forward.clone().crossProduct(side).normalize();
        double spread = Math.tan(Math.toRadians(angleDegrees));

        for (int index = 0; index < points; index++) {
            double progress = (index + 1) / (double) points;
            double distance = length * progress;
            double angle = index * 2.399963229728653d;
            double offset = spread * distance * Math.sqrt(progress);
            Vector point = forward.clone().multiply(distance)
                    .add(side.clone().multiply(Math.cos(angle) * offset))
                    .add(up.clone().multiply(Math.sin(angle) * offset));
            locations.add(origin.clone().add(point));
        }
        return locations;
    }

    public static List<Location> line(Location from, Location to, double spacing) {
        List<Location> locations = new ArrayList<>();
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 1.0e-6d) {
            locations.add(from.clone());
            return locations;
        }
        Vector step = delta.clone().normalize().multiply(Math.max(0.05d, spacing));
        int count = (int) Math.floor(length / Math.max(0.05d, spacing));
        Location cursor = from.clone();
        for (int index = 0; index <= count; index++) {
            locations.add(cursor.clone());
            cursor.add(step);
        }
        return locations;
    }

    public static List<Location> spiral(Location centre, double radius, double height, int points, double turns) {
        List<Location> locations = new ArrayList<>(Math.max(1, points));
        for (int index = 0; index < points; index++) {
            double progress = index / (double) Math.max(1, points - 1);
            double angle = 2 * Math.PI * turns * progress;
            locations.add(centre.clone().add(Math.cos(angle) * radius, height * progress,
                    Math.sin(angle) * radius));
        }
        return locations;
    }

    /** Two spirals half a turn apart. */
    public static List<Location> helix(Location centre, double radius, double height, int points, double turns,
                                       int strands) {
        List<Location> locations = new ArrayList<>(Math.max(1, points) * Math.max(1, strands));
        for (int strand = 0; strand < Math.max(1, strands); strand++) {
            double phase = 2 * Math.PI * strand / Math.max(1, strands);
            for (int index = 0; index < points; index++) {
                double progress = index / (double) Math.max(1, points - 1);
                double angle = 2 * Math.PI * turns * progress + phase;
                locations.add(centre.clone().add(Math.cos(angle) * radius, height * progress,
                        Math.sin(angle) * radius));
            }
        }
        return locations;
    }

    /** The twelve edges of an axis-aligned cube, sampled at {@code spacing}. */
    public static List<Location> cube(Location centre, double size, double spacing) {
        List<Location> locations = new ArrayList<>();
        double half = size / 2.0d;
        double step = Math.max(0.1d, spacing);
        for (double offset = -half; offset <= half; offset += step) {
            for (int corner = 0; corner < 4; corner++) {
                double a = (corner & 1) == 0 ? -half : half;
                double b = (corner & 2) == 0 ? -half : half;
                locations.add(centre.clone().add(offset, a, b));
                locations.add(centre.clone().add(a, offset, b));
                locations.add(centre.clone().add(a, b, offset));
            }
        }
        return locations;
    }

    /** A solid box of points, for {@code particle_box} and {@code blocks_in_radius}. */
    public static List<Location> box(Location centre, double size, double spacing) {
        List<Location> locations = new ArrayList<>();
        double half = size / 2.0d;
        double step = Math.max(0.25d, spacing);
        for (double x = -half; x <= half; x += step) {
            for (double y = -half; y <= half; y += step) {
                for (double z = -half; z <= half; z += step) {
                    locations.add(centre.clone().add(x, y, z));
                }
            }
        }
        return locations;
    }

    public static Location randomNear(Location centre, double radius, boolean flat) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = radius * Math.sqrt(random.nextDouble());
        double height = flat ? 0.0d : (random.nextDouble() - 0.5d) * radius;
        return centre.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
    }

    private static Vector pickPerpendicular(Vector forward) {
        Vector candidate = Math.abs(forward.getY()) > 0.9d ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        return forward.clone().crossProduct(candidate).normalize();
    }
}
