package dev.bwmp.bestiary.api.skill;

import java.util.ArrayList;
import java.util.List;

/** A mechanic's identity, parameters and targeting requirement. */
public final class MechanicMeta extends ElementMeta {

    private final TargetKind requires;
    private final boolean requiresMainThread;

    private MechanicMeta(String id, String description, List<ParameterSpec> parameters,
                         TargetKind requires, boolean requiresMainThread) {
        super(id, description, parameters);
        this.requires = requires;
        this.requiresMainThread = requiresMainThread;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public TargetKind requires() {
        return requires;
    }

    /** True for anything touching world state, which the async mechanic honours. */
    public boolean requiresMainThread() {
        return requiresMainThread;
    }

    public static final class Builder {

        private final String id;
        private final List<ParameterSpec> parameters = new ArrayList<>();
        private String description = "";
        private TargetKind requires = TargetKind.ANY;
        private boolean requiresMainThread = true;

        private Builder(String id) {
            this.id = id;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requires(TargetKind requires) {
            this.requires = requires;
            return this;
        }

        /** For pure computation — variable maths, flow control. */
        public Builder threadSafe() {
            this.requiresMainThread = false;
            return this;
        }

        public Builder param(String name, String description, String defaultValue, String... aliases) {
            parameters.add(ParameterSpec.of(name, description, defaultValue, aliases));
            return this;
        }

        public Builder required(String name, String description, String... aliases) {
            parameters.add(ParameterSpec.required(name, description, aliases));
            return this;
        }

        public MechanicMeta build() {
            return new MechanicMeta(id, description, parameters, requires, requiresMainThread);
        }
    }
}
