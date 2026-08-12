package dev.bwmp.bestiary.api.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The declaration shared by mechanics, targeters and conditions: an id, a
 * description, and the parameters the thing accepts.
 * <p>
 * The parser reads this before building anything, which is how a parameter
 * alias gets resolved and how a misspelled key becomes a load-time warning
 * naming the parameters that do exist rather than a silent default.
 */
public abstract class ElementMeta {

    private final String id;
    private final String description;
    private final List<ParameterSpec> parameters;
    private final Map<String, String> aliasToName = new HashMap<>();

    protected ElementMeta(String id, String description, List<ParameterSpec> parameters) {
        this.id = ParameterSpec.normalize(id);
        this.description = description == null ? "" : description;
        this.parameters = List.copyOf(parameters);
        for (ParameterSpec parameter : this.parameters) {
            aliasToName.put(parameter.name(), parameter.name());
            for (String alias : parameter.aliases()) {
                aliasToName.put(alias, parameter.name());
            }
        }
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public List<ParameterSpec> parameters() {
        return parameters;
    }

    /**
     * The canonical parameter name for a written key, or the normalized key
     * itself when the parameter was not declared.
     * <p>
     * Undeclared keys are passed through rather than dropped: a mechanic is
     * free to read something it did not declare, and the load report warns
     * about it separately.
     */
    public String canonical(String writtenKey) {
        String normalized = ParameterSpec.normalize(writtenKey);
        String canonical = aliasToName.get(normalized);
        return canonical != null ? canonical : normalized;
    }

    public boolean declares(String writtenKey) {
        return aliasToName.containsKey(ParameterSpec.normalize(writtenKey));
    }

    public List<String> parameterNames() {
        List<String> names = new ArrayList<>(parameters.size());
        for (ParameterSpec parameter : parameters) {
            names.add(parameter.name());
        }
        return names;
    }

    @Override
    public String toString() {
        return id;
    }
}
