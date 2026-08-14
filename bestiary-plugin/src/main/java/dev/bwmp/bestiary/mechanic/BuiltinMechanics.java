package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.MechanicType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BuiltinMechanics {

    private BuiltinMechanics() {
    }

    public static Map<String, MechanicType> all(Engine engine) {
        Map<String, MechanicType> types = new LinkedHashMap<>();
        DamageMechanics.register(types, engine);
        MovementMechanics.register(types, engine);
        SummonMechanics.register(types, engine);
        ProjectileMechanics.register(types, engine);
        ShapeMechanics.register(types, engine);
        PresentationMechanics.register(types, engine);
        BlockMechanics.register(types, engine);
        StateMechanics.register(types, engine);
        FlowMechanics.register(types, engine);
        PlayerMechanics.register(types, engine);
        ProgressionMechanics.register(types, engine);
        return types;
    }
}
