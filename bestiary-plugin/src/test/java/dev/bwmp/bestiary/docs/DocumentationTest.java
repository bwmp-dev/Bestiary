package dev.bwmp.bestiary.docs;

import dev.bwmp.bestiary.api.skill.ConditionMeta;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.ElementMeta;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.ParameterSpec;
import dev.bwmp.bestiary.api.skill.TargeterMeta;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.bestiary.condition.BuiltinConditions;
import dev.bwmp.bestiary.mechanic.BuiltinMechanics;
import dev.bwmp.bestiary.targeter.BuiltinTargeters;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference docs are generated from the metas rather than written by hand,
 * so they cannot drift from what the engine actually accepts.
 * <p>
 * By default this <em>verifies</em> the committed files match. Run with
 * {@code -Dbestiary.docs.write=true} to regenerate them after changing a
 * mechanic's parameters.
 * <p>
 * Registration is deliberately passed a null engine: every factory closes over
 * it lazily, so building the type map touches only the declarations. If that
 * ever stops being true this test is the thing that notices.
 */
class DocumentationTest {

    private static final Path DOCS = Path.of("..", "docs");

    @Test
    void mechanicsDocumentationIsCurrent() throws IOException {
        Map<String, MechanicType> types = BuiltinMechanics.all(null);
        assertTrue(types.size() > 100, "the mechanic library shrank unexpectedly: " + types.size());

        StringBuilder out = header("Mechanics",
                "What happens. One line per mechanic, generated from its declaration.",
                types.size());
        types.forEach((name, type) -> {
            MechanicMeta meta = type.meta();
            out.append("\n### `").append(name).append("`\n\n");
            if (!meta.description().isEmpty()) {
                out.append(meta.description()).append("\n\n");
            }
            out.append("Targets: `").append(meta.requires().name().toLowerCase(java.util.Locale.ROOT))
                    .append("`\n");
            parameters(out, meta);
        });
        check("mechanics.md", out.toString());
    }

    @Test
    void targetersDocumentationIsCurrent() throws IOException {
        Map<String, TargeterType> types = BuiltinTargeters.all(null);
        assertTrue(types.size() > 30, "the targeter library shrank unexpectedly: " + types.size());

        StringBuilder out = header("Targeters",
                "To whom, or where. Every targeter also accepts `limit`, `sort` and `filter`, "
                        + "which the engine applies rather than the targeter — so a third-party "
                        + "targeter gets them free and cannot forget the `max_targets` cap.",
                types.size());
        types.forEach((name, type) -> {
            TargeterMeta meta = type.meta();
            out.append("\n### `@").append(name).append("`\n\n");
            if (!meta.description().isEmpty()) {
                out.append(meta.description()).append("\n\n");
            }
            out.append("Produces: `").append(meta.produces().name().toLowerCase(java.util.Locale.ROOT))
                    .append("`");
            out.append(meta.acceptsSource() ? "  ·  composes with `of`\n" : "\n");
            parameters(out, meta);
        });
        check("targeters.md", out.toString());
    }

    @Test
    void conditionsDocumentationIsCurrent() throws IOException {
        Map<String, ConditionType> types = BuiltinConditions.all(null, () -> null);
        assertTrue(types.size() > 40, "the condition library shrank unexpectedly: " + types.size());

        StringBuilder out = header("Conditions",
                "Only if. Numeric conditions take an optional comparator prefix — `<=`, `>=`, "
                        + "`<`, `>`, `=`, `!=` — followed by an expression; a bare number means `=`. "
                        + "Prefix a condition with `!` to negate it.",
                types.size());
        types.forEach((name, type) -> {
            ConditionMeta meta = type.meta();
            out.append("\n### `?").append(name).append("`\n\n");
            if (!meta.description().isEmpty()) {
                out.append(meta.description()).append("\n\n");
            }
            out.append("Evaluates against: `")
                    .append(meta.evaluates().name().toLowerCase(java.util.Locale.ROOT)).append("`\n");
            parameters(out, meta);
        });
        check("conditions.md", out.toString());
    }

    private static StringBuilder header(String title, String blurb, int count) {
        return new StringBuilder()
                .append("# ").append(title).append("\n\n")
                .append("<!-- Generated by DocumentationTest. Do not edit by hand:\n")
                .append("     run `mvn test -Dbestiary.docs.write=true` after changing a declaration. -->\n\n")
                .append(blurb).append("\n\n")
                .append(count).append(" built in.\n");
    }

    private static void parameters(StringBuilder out, ElementMeta meta) {
        List<ParameterSpec> parameters = meta.parameters();
        if (parameters.isEmpty()) {
            out.append("\nNo parameters.\n");
            return;
        }
        out.append("\n| Parameter | Aliases | Default | Description |\n");
        out.append("|---|---|---|---|\n");
        for (ParameterSpec parameter : parameters) {
            out.append("| `").append(parameter.name()).append("` | ")
                    .append(parameter.aliases().isEmpty() ? "—"
                            : "`" + String.join("`, `", parameter.aliases()) + "`")
                    .append(" | ")
                    .append(parameter.isRequired() ? "**required**" : "`" + parameter.defaultValue() + "`")
                    .append(" | ")
                    .append(parameter.description().isEmpty() ? "—" : parameter.description())
                    .append(" |\n");
        }
    }

    private static void check(String fileName, String generated) throws IOException {
        Path file = DOCS.resolve(fileName);
        if (Boolean.getBoolean("bestiary.docs.write") || !Files.exists(file)) {
            Files.createDirectories(DOCS);
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            return;
        }
        String committed = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(normalize(committed), normalize(generated),
                fileName + " is out of date; run mvn test -Dbestiary.docs.write=true");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
