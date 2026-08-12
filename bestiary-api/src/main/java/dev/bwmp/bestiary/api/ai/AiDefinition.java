package dev.bwmp.bestiary.api.ai;

import java.util.List;

/** A mob's {@code ai:} block. */
public final class AiDefinition {

    public static final AiDefinition NONE = new AiDefinition(List.of(), NavigationKind.DEFAULT, "", "");

    private final List<AiGoalNode> goals;
    private final NavigationKind navigation;
    private final String moveControl;
    private final String lookControl;

    public AiDefinition(List<AiGoalNode> goals, NavigationKind navigation, String moveControl, String lookControl) {
        this.goals = goals == null ? List.of() : List.copyOf(goals);
        this.navigation = navigation == null ? NavigationKind.DEFAULT : navigation;
        this.moveControl = moveControl == null ? "" : moveControl;
        this.lookControl = lookControl == null ? "" : lookControl;
    }

    public List<AiGoalNode> goals() {
        return goals;
    }

    public NavigationKind navigation() {
        return navigation;
    }

    /** NMS tier only. Empty means the vanilla controller is kept. */
    public String moveControl() {
        return moveControl;
    }

    public String lookControl() {
        return lookControl;
    }

    public boolean isEmpty() {
        return goals.isEmpty() && navigation == NavigationKind.DEFAULT
                && moveControl.isEmpty() && lookControl.isEmpty();
    }

    public boolean needsNms() {
        return navigation.needsNms() || !moveControl.isEmpty() || !lookControl.isEmpty();
    }
}
