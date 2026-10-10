package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;

/**
 * R9.1: the list of the server's internal classes, fields, constructors and methods the brain module relies on, and the check
 * that each exists with exactly the signature the module was compiled against. Pure: the Paper layer supplies a {@link Probe}
 * that looks them up by name in the running server, so the check runs before any class that links against them is loaded.
 * A game update that renames a member, or changes the type of one of its parameters or of what it returns, turns the module
 * off at startup instead of failing inside a villager's tick (where Paper would remove the villager). Every internal member
 * the module uses must be on {@link #REQUIRED}, with its erased types.
 */
public final class BrainSelfCheck {
    private BrainSelfCheck() {
    }

    public enum Kind {
        CLASS, FIELD, CONSTRUCTOR, METHOD
    }

    /**
     * One thing the module needs. Types are binary class names ({@code net.minecraft.world.entity.Entity}, nested classes with
     * {@code $}) or primitive names ({@code int}, {@code long}, {@code boolean}, {@code double}, {@code void}); generic types
     * are given erased, as the compiled code calls them.
     */
    public record Requirement(Kind kind, String owner, String name, List<String> parameters, String returns) {
        static Requirement type(String owner) {
            return new Requirement(Kind.CLASS, owner, "", List.of(), "");
        }

        static Requirement field(String owner, String name, String type) {
            return new Requirement(Kind.FIELD, owner, name, List.of(), type);
        }

        static Requirement constructor(String owner, String... parameters) {
            return new Requirement(Kind.CONSTRUCTOR, owner, "<init>", List.of(parameters), "void");
        }

        static Requirement method(String owner, String returns, String name, String... parameters) {
            return new Requirement(Kind.METHOD, owner, name, List.of(parameters), returns);
        }

        /** e.g. "net.minecraft.world.entity.ai.Brain#setMemory(MemoryModuleType, Object) -> void", short names for reading. */
        public String describe() {
            String params = String.join(", ", parameters.stream().map(BrainSelfCheck::shortName).toList());
            return switch (kind) {
                case CLASS -> owner;
                case FIELD -> owner + "#" + name + " : " + shortName(returns);
                case CONSTRUCTOR -> owner + "(" + params + ")";
                case METHOD -> owner + "#" + name + "(" + params + ") -> " + shortName(returns);
            };
        }
    }

    private static String shortName(String type) {
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    /**
     * Looks names up in the running server. A field or method may be declared on the class or inherited from a superclass or,
     * for a method, an interface (a default method); the types must match exactly, return type included.
     */
    public interface Probe {
        boolean hasClass(String name);

        boolean hasField(String owner, String name, String type);

        boolean hasConstructor(String owner, List<String> parameters);

        boolean hasMethod(String owner, String name, List<String> parameters, String returns);
    }

    private static final String BRAIN = "net.minecraft.world.entity.ai.Brain";
    private static final String WALK = "net.minecraft.world.entity.ai.memory.WalkTarget";
    private static final String CONTROL = "net.minecraft.world.entity.ai.behavior.BehaviorControl";
    private static final String VISITOR = "net.minecraft.world.entity.ai.Brain$Visitor";
    private static final String BEHAVIOR = "net.minecraft.world.entity.ai.behavior.Behavior";
    private static final String STATUS = "net.minecraft.world.entity.ai.behavior.Behavior$Status";
    private static final String VILLAGER = "net.minecraft.world.entity.npc.villager.Villager";
    private static final String CRAFT_VILLAGER = "org.bukkit.craftbukkit.entity.CraftVillager";
    private static final String MEMORY = "net.minecraft.world.entity.ai.memory.MemoryModuleType";
    private static final String ACTIVITY = "net.minecraft.world.entity.schedule.Activity";
    private static final String TRACKER = "net.minecraft.world.entity.ai.behavior.EntityTracker";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String SERVER_LEVEL = "net.minecraft.server.level.ServerLevel";
    private static final String LEVEL = "net.minecraft.world.level.Level";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";

    /** Everything the brain module touches, Paper 26.2. Keep in step with the module's code. */
    public static final List<Requirement> REQUIRED = List.of(
            Requirement.type(CRAFT_VILLAGER),
            Requirement.method(CRAFT_VILLAGER, VILLAGER, "getHandle"),
            Requirement.type(VILLAGER),
            Requirement.method(VILLAGER, BRAIN, "getBrain"),
            Requirement.method(VILLAGER, "boolean", "isTrading"),
            Requirement.method(VILLAGER, "boolean", "isSleeping"),
            Requirement.method(VILLAGER, LEVEL, "level"),
            Requirement.method(VILLAGER, "boolean", "isAlive"),
            Requirement.method(VILLAGER, "boolean", "isRemoved"),
            Requirement.method(VILLAGER, "double", "distanceToSqr", ENTITY),
            Requirement.type(BRAIN),
            Requirement.field(BRAIN, "availableBehaviorsByPriority", "java.util.Map"),
            Requirement.method(BRAIN, "void", "setMemory", MEMORY, "java.lang.Object"),
            Requirement.method(BRAIN, "void", "eraseMemory", MEMORY),
            Requirement.method(BRAIN, "boolean", "hasMemoryValue", MEMORY),
            Requirement.method(BRAIN, "java.util.Optional", "getActiveNonCoreActivity"),
            Requirement.method(BRAIN, "java.util.Set", "getActiveActivities"),
            Requirement.method(BRAIN, "java.util.List", "getRunningBehaviors"),
            Requirement.method(BRAIN, "void", "forEach", VISITOR),
            Requirement.type(VISITOR),
            Requirement.method(VISITOR, "void", "accept", MEMORY, "java.lang.Object"),
            Requirement.method(VISITOR, "void", "accept", MEMORY, "java.lang.Object", "long"),
            Requirement.method(VISITOR, "void", "acceptEmpty", MEMORY),
            Requirement.type(CONTROL),
            Requirement.method(CONTROL, "java.lang.String", "debugString"),
            Requirement.method(ACTIVITY, "java.lang.String", "getName"),
            Requirement.type(ENTITY),
            Requirement.method(ENTITY, "java.lang.String", "getScoreboardName"),
            Requirement.type(BEHAVIOR),
            Requirement.constructor(BEHAVIOR, "java.util.Map", "int", "int"),
            Requirement.method(BEHAVIOR, "boolean", "checkExtraStartConditions", SERVER_LEVEL, LIVING),
            Requirement.method(BEHAVIOR, "void", "start", SERVER_LEVEL, LIVING, "long"),
            Requirement.method(BEHAVIOR, "void", "tick", SERVER_LEVEL, LIVING, "long"),
            Requirement.method(BEHAVIOR, "void", "stop", SERVER_LEVEL, LIVING, "long"),
            Requirement.method(BEHAVIOR, "boolean", "canStillUse", SERVER_LEVEL, LIVING, "long"),
            Requirement.method(BEHAVIOR, "void", "doStop", SERVER_LEVEL, LIVING, "long"),
            Requirement.method(BEHAVIOR, STATUS, "getStatus"),
            Requirement.method(BEHAVIOR, "java.lang.String", "debugString"),
            Requirement.type(STATUS),
            Requirement.field(STATUS, "RUNNING", STATUS),
            Requirement.type(ACTIVITY),
            Requirement.field(ACTIVITY, "CORE", ACTIVITY),
            Requirement.field(ACTIVITY, "IDLE", ACTIVITY),
            Requirement.field(ACTIVITY, "WORK", ACTIVITY),
            Requirement.field(ACTIVITY, "MEET", ACTIVITY),
            Requirement.type(MEMORY),
            Requirement.field(MEMORY, "LOOK_TARGET", MEMORY),
            Requirement.field(MEMORY, "WALK_TARGET", MEMORY),
            Requirement.type(TRACKER),
            Requirement.constructor(TRACKER, ENTITY, "boolean"),
            Requirement.type(PLAYER),
            Requirement.method(PLAYER, "boolean", "isSpectator"),
            Requirement.method(PLAYER, "boolean", "isAlive"),
            Requirement.method(PLAYER, "boolean", "isRemoved"),
            Requirement.method(PLAYER, LEVEL, "level"),
            Requirement.type(SERVER_LEVEL),
            Requirement.method(SERVER_LEVEL, PLAYER, "getNearestPlayer", ENTITY, "double"),
            Requirement.method(SERVER_LEVEL, "long", "getGameTime"),
            Requirement.method(SERVER_LEVEL, "net.minecraft.server.MinecraftServer", "getServer"),
            Requirement.type("net.minecraft.server.MinecraftServer"),
            Requirement.method("net.minecraft.server.MinecraftServer", "int", "getTickCount"),
            Requirement.type(WALK),
            Requirement.method(WALK, "net.minecraft.world.entity.ai.behavior.PositionTracker", "getTarget"),
            Requirement.method(WALK, "float", "getSpeedModifier"),
            Requirement.method(WALK, "int", "getCloseEnoughDist"),
            Requirement.type("net.minecraft.world.entity.ai.behavior.PositionTracker"),
            Requirement.method("net.minecraft.world.entity.ai.behavior.PositionTracker", "net.minecraft.world.phys.Vec3", "currentPosition"),
            Requirement.type("net.minecraft.world.phys.Vec3"),
            Requirement.method("net.minecraft.world.phys.Vec3", "double", "x"),
            Requirement.method("net.minecraft.world.phys.Vec3", "double", "y"),
            Requirement.method("net.minecraft.world.phys.Vec3", "double", "z"));

    /** What is missing, described for the log; empty if everything is there. A probe that throws counts as missing. */
    public static List<String> missing(List<Requirement> required, Probe probe) {
        List<String> out = new ArrayList<>();
        for (Requirement r : required) {
            boolean present;
            try {
                present = probe.hasClass(r.owner()) && switch (r.kind()) {
                    case CLASS -> true;
                    case FIELD -> probe.hasField(r.owner(), r.name(), r.returns());
                    case CONSTRUCTOR -> probe.hasConstructor(r.owner(), r.parameters());
                    case METHOD -> probe.hasMethod(r.owner(), r.name(), r.parameters(), r.returns());
                };
            } catch (RuntimeException | LinkageError e) {
                present = false;
            }
            if (!present) {
                out.add(r.describe());
            }
        }
        return out;
    }
}
