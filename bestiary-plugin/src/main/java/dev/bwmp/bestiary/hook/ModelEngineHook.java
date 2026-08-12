package dev.bwmp.bestiary.hook;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Custom models on mobs, through ModelEngine when it is installed.
 * <p>
 * Bestiary does not do models — that is ModelEngine's job — so this is a
 * two-call adapter and nothing more. ModelEngine's API has moved
 * between R3 and R4, so both shapes are probed and the whole hook disables
 * itself if neither resolves.
 */
public final class ModelEngineHook {

    private Method createActiveModel;
    private Method createModeledEntity;
    private Method addModel;
    private Method setBaseEntityVisible;
    private Method getModeledEntity;
    private Method getModels;
    private Method setScale;

    ModelEngineHook() {
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            this.createActiveModel = api.getMethod("createActiveModel", String.class);
            this.createModeledEntity = api.getMethod("createModeledEntity", Entity.class);
            Class<?> modeledEntity = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModel = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            this.addModel = findAddModel(modeledEntity, activeModel);
            this.setBaseEntityVisible = findMethod(modeledEntity, boolean.class, "setBaseEntityVisible");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.createActiveModel = null;
        }

        // Resolved separately and on its own terms: scaling is a nicety, and a
        // ModelEngine build that lacks the setter should still get its models
        // rather than disabling the whole hook over it.
        try {
            this.getModeledEntity = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI")
                    .getMethod("getModeledEntity", Entity.class);
            this.getModels = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity")
                    .getMethod("getModels");
            this.setScale = Class.forName("com.ticxo.modelengine.api.model.ActiveModel")
                    .getMethod("setScale", double.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.setScale = null;
        }
    }

    /**
     * Resizes every model already attached to an entity.
     * <p>
     * Vanilla halves a baby animal's own model, but ModelEngine draws its own
     * and knows nothing about age, so a bred baby would otherwise wear a
     * full-size model.
     */
    public void scaleModel(LivingEntity entity, double scale) {
        if (!present() || setScale == null || getModeledEntity == null || getModels == null) {
            return;
        }
        try {
            Object modeled = getModeledEntity.invoke(null, entity);
            if (modeled == null) {
                return;
            }
            for (Object model : ((java.util.Map<?, ?>) getModels.invoke(modeled)).values()) {
                setScale.invoke(model, scale);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Same posture as applyModel: a mob that renders at the wrong size
            // is a great deal better than one that fails to spawn.
        }
    }

    private static Method findMethod(Class<?> owner, Class<?> parameter, String name) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(parameter)) {
                return method;
            }
        }
        return null;
    }

    /**
     * {@code addModel} is the one member whose arity actually changed: R3 takes
     * {@code (ActiveModel)}, R4 takes {@code (ActiveModel, boolean)} and returns
     * an Optional. Matching only the one-argument form is why this hook reported
     * itself absent against ModelEngine R4.1.0 even though every other method
     * resolved. Either arity is accepted; {@link #applyModel} branches on it.
     */
    private static Method findAddModel(Class<?> owner, Class<?> activeModel) {
        Method twoArg = null;
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals("addModel")) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 0 || !parameters[0].isAssignableFrom(activeModel)) {
                continue;
            }
            if (parameters.length == 1) {
                return method;
            }
            if (parameters.length == 2 && parameters[1] == boolean.class) {
                twoArg = method;
            }
        }
        return twoArg;
    }

    public boolean present() {
        return createActiveModel != null && addModel != null;
    }

    /** Silently does nothing when the mob declares no model or ModelEngine is absent. */
    public void applyModel(LivingEntity entity, String modelId) {
        if (!present() || modelId == null || modelId.isBlank()) {
            return;
        }
        try {
            Object model = createActiveModel.invoke(null, modelId);
            if (model == null) {
                return;
            }
            Object modeled = createModeledEntity.invoke(null, entity);
            if (modeled == null) {
                return;
            }
            if (addModel.getParameterCount() == 2) {
                // R4's flag asks for the model to be applied even when one is
                // already attached, which is what a re-bind after /bestiary
                // reload needs.
                addModel.invoke(modeled, model, true);
            } else {
                addModel.invoke(modeled, model);
            }
            if (setBaseEntityVisible != null) {
                setBaseEntityVisible.invoke(modeled, false);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A model id that does not exist is a config problem; the mob still
            // works, it just looks like its base type.
        }
    }
}
