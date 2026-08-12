package dev.bwmp.bestiary.api.skill;

import java.util.ArrayList;
import java.util.List;

/** A condition's identity, parameters and what it can be evaluated against. */
public final class ConditionMeta extends ElementMeta {

    private final TargetKind evaluates;

    private ConditionMeta(String id, String description, List<ParameterSpec> parameters, TargetKind evaluates) {
        super(id, description, parameters);
        this.evaluates = evaluates;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * {@link TargetKind#ENTITY} for {@code health}, {@link TargetKind#LOCATION}
     * for {@code biome}. Attaching a location condition to an entity slot is a
     * load-time error, and this is what detects it.
     */
    public TargetKind evaluates() {
        return evaluates;
    }

    public static final class Builder {

        private final String id;
        private final List<ParameterSpec> parameters = new ArrayList<>();
        private String description = "";
        private TargetKind evaluates = TargetKind.ANY;

        private Builder(String id) {
            this.id = id;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder evaluates(TargetKind evaluates) {
            this.evaluates = evaluates;
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

        public ConditionMeta build() {
            return new ConditionMeta(id, description, parameters, evaluates);
        }
    }
}
