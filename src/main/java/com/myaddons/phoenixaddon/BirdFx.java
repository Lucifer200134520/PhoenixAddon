package com.myaddons.phoenixaddon;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class BirdFx {

    private static final Particle.DustOptions AMBER = new Particle.DustOptions(Color.fromRGB(255, 165, 30), 1.15f);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 140, 15), 1.1f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 208, 60), 1.1f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 244, 215), 1.0f);

    private BirdFx() {
    }

    private static Vector[] basis(Vector forward) {
        Vector fwd = forward.clone();
        if (fwd.lengthSquared() < 1.0E-6) {
            fwd = new Vector(0, 0, 1);
        }
        fwd.normalize();

        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(fwd).normalize();
        return new Vector[]{fwd, right, up};
    }

    private static void dust(World world, Vector p, Particle.DustOptions options) {
        world.spawnParticle(Particle.REDSTONE, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, options);
    }

    private static void flame(World world, Vector p) {
        world.spawnParticle(Particle.FLAME, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0.004);
    }

    private static void mist(World world, Vector p, int count, double spread) {
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY(), p.getZ(), count, spread, spread * 0.6, spread, 0.003);
    }

    private static Vector at(Vector origin, Vector fwd, Vector right, Vector up, double f, double r, double u) {
        return origin.clone()
                .add(fwd.clone().multiply(f))
                .add(right.clone().multiply(r))
                .add(up.clone().multiply(u));
    }

    public static void drawMini(World world, Vector pos, Vector forward, int age, int index) {
        render(world, pos, forward, age, index, 1.2, 1);
    }

    public static void drawBig(World world, Vector pos, Vector forward, int age) {
        render(world, pos, forward, age, 0, 4.0, 2);
    }

    public static void drawBird(World world, Vector pos, Vector forward, int age, int index, double scale) {
        render(world, pos, forward, age, index, scale, 1);
    }

    public static void drawBigBird(World world, Vector pos, Vector forward, int age, double scale) {
        render(world, pos, forward, age, 0, scale, 2);
    }

    public static void drawFireOval(World world, Vector pos, Vector forward, int age, int index, double s) {
        if (world == null) return;

        Vector[] b = basis(forward);
        Vector fwd = b[0];
        Vector right = b[1];
        Vector up = b[2];

        double a = 0.95 * s;
        double w = 0.4 * s;
        double h = 0.33 * s;

        int shell = 6;
        double spin = age * 0.42 + index * 1.9;
        for (int i = 0; i < shell; i++) {
            double t = (i + 0.5) / shell;
            double polar = Math.acos(1.0 - 2.0 * t);
            double azim = i * 2.399963 + spin;
            double sp = Math.sin(polar);
            Vector p = at(pos, fwd, right, up, a * Math.cos(polar), w * sp * Math.cos(azim), h * sp * Math.sin(azim));
            if (i % 3 == 0) {
                flame(world, p);
            } else {
                dust(world, p, i % 2 == 0 ? AMBER : GOLD);
            }
        }

        double flap = Math.sin(age * 0.55 + index * 1.3);
        for (int side = -1; side <= 1; side += 2) {
            Vector inner = at(pos, fwd, right, up, -0.05 * s, side * 0.7 * s, (0.1 + flap * 0.3) * s);
            Vector outer = at(pos, fwd, right, up, -0.35 * s, side * 1.15 * s, (0.1 + flap * 0.6) * s);
            dust(world, inner, ORANGE);
            if (age % 2 == 0) {
                flame(world, outer);
            } else {
                dust(world, outer, AMBER);
            }
        }

        Vector tailTip = at(pos, fwd, right, up, -1.4 * s, Math.sin(age * 0.4 + index) * 0.1 * s, -0.05 * s);
        if (age % 2 == 0) {
            flame(world, tailTip);
        }

        mist(world, pos, 1, 0.4 * s);
        if (age % 2 == 0) {
            mist(world, at(pos, fwd, right, up, -1.1 * s, 0, 0), 1, 0.3 * s);
        }
    }

    private static void render(World world, Vector pos, Vector forward, int age, int index, double s, int detail) {
        if (world == null) return;

        Vector[] b = basis(forward);
        Vector fwd = b[0];
        Vector right = b[1];
        Vector up = b[2];

        double flap = Math.sin(age * 0.5 + index * 1.7);

        int spine = 3 + detail * 2;
        for (int i = 0; i < spine; i++) {
            double t = -0.4 + 0.95 * i / (spine - 1);
            double lift = Math.sin(t * 2.2 + 0.6) * 0.05;
            flame(world, at(pos, fwd, right, up, t * s, 0, lift * s));
        }
        if (detail > 1) {
            for (int side = -1; side <= 1; side += 2) {
                flame(world, at(pos, fwd, right, up, 0.1 * s, side * 0.13 * s, 0));
                dust(world, at(pos, fwd, right, up, 0.35 * s, side * 0.1 * s, 0.02 * s), AMBER);
            }
        }

        Vector head = at(pos, fwd, right, up, 0.62 * s, 0, 0.08 * s);
        dust(world, head, GOLD);
        dust(world, at(head, fwd, right, up, 0.2 * s, 0, -0.04 * s), WHITE);
        flame(world, at(head, fwd, right, up, -0.1 * s, 0, 0.16 * s));
        dust(world, at(head, fwd, right, up, -0.3 * s, 0, 0.27 * s), ORANGE);

        Vector shoulder = at(pos, fwd, right, up, 0.18 * s, 0, 0.08 * s);
        int edge = 3 + detail;
        int feathers = detail > 1 ? 5 : 3;
        int perFeather = detail > 1 ? 3 : 2;

        for (int side = -1; side <= 1; side += 2) {
            for (int k = 1; k <= edge; k++) {
                double f = (double) k / edge;
                Vector p = at(shoulder, fwd, right, up, -0.28 * f * f * s, side * f * s * 1.05, (flap * 0.55 * f + 0.1 * f) * s);
                if (k % 2 == 0) {
                    dust(world, p, f < 0.6 ? ORANGE : GOLD);
                } else {
                    flame(world, p);
                }
            }

            for (int j = 0; j < feathers; j++) {
                double fa = 0.3 + 0.7 * j / (feathers - 1);
                Vector anchor = at(shoulder, fwd, right, up, -0.28 * fa * fa * s, side * fa * s * 1.05, (flap * 0.55 * fa + 0.1 * fa) * s);
                Vector dir = fwd.clone().multiply(-0.95).add(right.clone().multiply(side * (0.1 + 0.3 * fa))).add(up.clone().multiply(flap * 0.12 - 0.05));
                dir.normalize();
                double len = (0.45 + 0.55 * fa) * s;
                for (int m = 1; m <= perFeather; m++) {
                    double g = (double) m / perFeather;
                    Vector p = anchor.clone().add(dir.clone().multiply(len * g));
                    if (m == perFeather) {
                        dust(world, p, GOLD);
                    } else {
                        flame(world, p);
                    }
                }
            }
        }

        Vector tailBase = at(pos, fwd, right, up, -0.4 * s, 0, 0);
        int tailSteps = 1 + detail;
        for (int lane = -1; lane <= 1; lane++) {
            for (int m = 1; m <= tailSteps; m++) {
                double wave = Math.sin(age * 0.4 - m * 0.9 + lane) * 0.05;
                Vector p = at(tailBase, fwd, right, up, -0.38 * m * s, lane * 0.2 * m * s * 0.6, wave * s - 0.02 * m * s);
                if (m == tailSteps) {
                    dust(world, p, GOLD);
                } else {
                    flame(world, p);
                }
            }
        }

        Vector tip = at(tailBase, fwd, right, up, -0.38 * tailSteps * s, 0, 0);
        world.spawnParticle(Particle.FLAME, tip.getX(), tip.getY(), tip.getZ(), detail + 1, 0.08 * s, 0.08 * s, 0.08 * s, 0.015);

        if (age % 2 == 0) {
            mist(world, pos, 1 + detail, 0.3 * s);
        }
        if (age % 3 == 0) {
            world.spawnParticle(Particle.SMOKE_NORMAL, tip.getX(), tip.getY(), tip.getZ(), 1, 0.08 * s, 0.08 * s, 0.08 * s, 0.01);
        }
    }
}
