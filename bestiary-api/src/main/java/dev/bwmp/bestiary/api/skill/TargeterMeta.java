package dev.bwmp.bestiary.api.skill;

import java.util.ArrayList;
import java.util.List;

/** A targeter's identity, parameters and what kind of target it produces. */
public final class TargeterMeta extends ElementMeta {

    private final TargetKind produces;
    private final boolean acceptsSource;

    private TargeterMeta(String id, String description, List<ParameterSpec> parameters,
                         TargetKind produces, boolean acceptsSource) {
        super(id, description, parameters);
        this.produces = produces;
        this.acceptsSource = acceptsSource;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public TargetKind produces() {
        return produces;
    }

    /**
     * True when {@code @ring{radius=4} of @playersInRadius{r=10}} makes sense
     * for this targeter — that is, when it resolves relative to something.
     */
    public boolean acceptsSource() {
        return acceptsSource;
    }

    public static final class Builder {

        private final String id;
        private final List<ParameterSpec> parameters = new ArrayList<>();
        private String description = "";
        private TargetKind produces = TargetKind.ENTITY;
        private boolean acceptsSource = true;

        private Builder(String id) {
            this.id = id;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder produces(TargetKind produces) {
            this.produces = produces;
            return this;
        }

        public Builder noSource() {
            this.acceptsSource = false;
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

        public TargeterMeta build() {
            return new TargeterMeta(id, description, parameters, produces, acceptsSource);
        }
    }
}
