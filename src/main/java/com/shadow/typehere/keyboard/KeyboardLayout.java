package com.shadow.typehere.keyboard;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Data-driven on-screen keyboard layout.
 *
 * <p>A layout is a grid of {@link Key} instances organised in rows. Every row
 * contains one or more keys; each key has a unit-width that sums to
 * {@link #width()} across its row.  When rendered, unit widths are multiplied
 * by {@code (pixelWidth / layout.width())} to compute the pixel width of each key.
 *
 * <p>Layouts are loaded from JSON resource files at
 * {@code assets/<namespace>/keyboard_layout/<id>/<language>.json} and parsed
 * via {@link #CODEC}.
 *
 * <p>Adapted directly from Controlify's {@code KeyboardLayout}, with the
 * {@code InputBindingSupplier} (controller-shortcut) field removed so the mod
 * has no Controlify dependency.
 *
 * @param width the total unit width that every row must sum to
 * @param keys  list of rows, each row being a list of {@link Key}s
 */
public record KeyboardLayout(float width, List<List<Key>> keys) {

    /** Codec used to deserialise layout JSON files. */
    public static final Codec<KeyboardLayout> CODEC = RecordCodecBuilder.<KeyboardLayout>create(instance -> instance.group(
            Codec.floatRange(1, Float.MAX_VALUE)
                    .fieldOf("width")
                    .forGetter(KeyboardLayout::width),
            Key.CODEC
                    .listOf(1, Integer.MAX_VALUE)
                    .listOf(1, Integer.MAX_VALUE)
                    .fieldOf("keys")
                    .forGetter(KeyboardLayout::keys)
    ).apply(instance, KeyboardLayout::new)).validate(layout ->
            validateRowWidths(layout)
                    ? DataResult.success(layout)
                    : DataResult.error(() -> "Row widths don't match the declared width " + layout.width())
    );

    /** Returns {@code true} if every row's key widths sum to {@link #width()}. */
    public static boolean validateRowWidths(KeyboardLayout layout) {
        return layout.keys().stream()
                .mapToDouble(row -> row.stream().mapToDouble(Key::width).sum())
                .allMatch(w -> Math.abs(w - layout.width()) < 0.001);
    }

    /** Convenience factory — validates row widths at construction time. */
    @SafeVarargs
    public static KeyboardLayout of(float width, List<Key>... keys) {
        KeyboardLayout layout = new KeyboardLayout(width, List.of(keys));
        Validate.isTrue(validateRowWidths(layout), "Row widths don't match " + width);
        return layout;
    }

    // =========================================================================
    // Key
    // =========================================================================

    /**
     * A single key within a {@link KeyboardLayout}.
     *
     * <p>Keys have a {@link #regular} and a {@link #shifted} function.
     * If no shifted function is supplied the regular function's
     * {@link KeyFunction#createShifted()} is used.
     *
     * @param regular    function used when Shift is inactive
     * @param shifted    function used when Shift is active
     * @param width      unit width of this key (must be ≥ 0.1)
     * @param identifier optional string ID used to restore keyboard focus when
     *                   switching layouts
     */
    public record Key(KeyFunction regular, KeyFunction shifted, float width, @Nullable String identifier) {

        private static final Codec<Float> WIDTH_CODEC = Codec.floatRange(0.1f, Float.MAX_VALUE);

        /** Full record codec — supports optional fields. */
        private static final Codec<Key> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                KeyFunction.CODEC.fieldOf("regular").forGetter(Key::regular),
                KeyFunction.CODEC.optionalFieldOf("shifted")
                        .forGetter(k -> Optional.of(k.shifted)),
                WIDTH_CODEC.optionalFieldOf("width", 1f).forGetter(Key::width),
                Codec.STRING.optionalFieldOf("identifier")
                        .forGetter(k -> Optional.ofNullable(k.identifier))
        ).apply(instance, Key::fromCodec));

        /**
         * Full codec: a key can be written as a bare function, a [regular, shifted]
         * array pair, or a full record object.
         */
        public static final Codec<Key> CODEC = Codec.either(KeyFunction.CODEC, RECORD_CODEC)
                .xmap(
                        either -> either.map(Key::new, Function.identity()),
                        Either::right
                );

        // --- convenience constructors -----------------------------------------

        public Key(KeyFunction fn) {
            this(fn, fn.createShifted(), 1f, null);
        }

        public Key(KeyFunction fn, float width) {
            this(fn, fn.createShifted(), width, null);
        }

        public Key(KeyFunction regular, KeyFunction shifted) {
            this(regular, shifted, 1f, null);
        }

        // --- codec helper -----------------------------------------------------

        private static Key fromCodec(KeyFunction regular, Optional<KeyFunction> shifted, float width, Optional<String> identifier) {
            return new Key(regular, shifted.orElseGet(regular::createShifted), width, identifier.orElse(null));
        }

        /** Returns the appropriate function based on whether Shift is active. */
        public KeyFunction getFunction(boolean shifted) {
            return shifted ? this.shifted : this.regular;
        }
    }

    // =========================================================================
    // KeyFunction — sealed hierarchy
    // =========================================================================

    /**
     * Defines what a key does when activated.
     *
     * <p>The four sealed implementations are:
     * <ul>
     *   <li>{@link StringFunc}       — inserts a string of characters</li>
     *   <li>{@link CodeFunc}         — sends one or more raw key-code events</li>
     *   <li>{@link SpecialFunc}      — performs a named special action</li>
     *   <li>{@link ChangeLayoutFunc} — switches to a different keyboard layout</li>
     * </ul>
     *
     * <p>Consumers switch on the sealed type (Java 21+ pattern matching switch)
     * to handle each case.
     */
    public sealed interface KeyFunction {

        /** Text shown on the key face in the UI. */
        Component displayName();

        /**
         * Returns the shifted variant of this function.
         * Defaults to returning {@code this} (i.e. no shift behaviour).
         */
        default KeyFunction createShifted() {
            return this;
        }

        /**
         * Union codec: tries each leaf codec in order until one succeeds.
         * Encoding dispatches on the sealed subtype.
         */
        Codec<KeyFunction> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<T> encode(KeyFunction input, DynamicOps<T> ops, T prefix) {
                return switch (input) {
                    case StringFunc f       -> StringFunc.CODEC.encode(f, ops, prefix);
                    case CodeFunc f         -> CodeFunc.CODEC.encode(f, ops, prefix);
                    case SpecialFunc f      -> SpecialFunc.CODEC.encode(f, ops, prefix);
                    case ChangeLayoutFunc f -> ChangeLayoutFunc.CODEC.encode(f, ops, prefix);
                };
            }

            @Override
            public <T> DataResult<Pair<KeyFunction, T>> decode(DynamicOps<T> ops, T input) {
                return Stream.of(StringFunc.CODEC, CodeFunc.CODEC, SpecialFunc.CODEC, ChangeLayoutFunc.CODEC)
                        .<Codec<? extends KeyFunction>>map(c -> c)
                        .map(c -> c.decode(ops, input).map(p -> p.mapFirst(f -> (KeyFunction) f)))
                        .filter(DataResult::isSuccess)
                        .findFirst()
                        .orElseGet(() -> DataResult.error(() -> "No KeyFunction decoder matched"));
            }
        };

        // =====================================================================
        // StringFunc — inserts a literal string
        // =====================================================================

        /**
         * Inserts a literal string (one or more characters) when pressed.
         * {@link #createShifted()} automatically uppercases the string.
         *
         * @param string            the text to insert
         * @param manualDisplayName optional override for the key label; if null,
         *                          the string itself is used as the label
         */
        record StringFunc(String string, @Nullable Component manualDisplayName) implements KeyFunction {

            public static final Codec<StringFunc> CODEC = Codec.withAlternative(
                    RecordCodecBuilder.create(instance -> instance.group(
                            Codec.STRING.fieldOf("chars").forGetter(StringFunc::string),
                            ComponentSerialization.CODEC.optionalFieldOf("display_name")
                                    .forGetter(f -> Optional.ofNullable(f.manualDisplayName))
                    ).apply(instance, (s, dn) -> new StringFunc(s, dn.orElse(null)))),
                    Codec.STRING.xmap(StringFunc::new, StringFunc::string)
            );

            public StringFunc(String string) {
                this(string, null);
            }

            @Override
            public Component displayName() {
                return manualDisplayName != null ? manualDisplayName : Component.literal(string);
            }

            @Override
            public KeyFunction createShifted() {
                return new StringFunc(string.toUpperCase(), manualDisplayName);
            }
        }

        // =====================================================================
        // CodeFunc — sends raw key-code events
        // =====================================================================

        /**
         * Sends one or more GLFW key-code events when pressed.
         * Useful for keys like Tab, arrow keys, or multi-modifier combos.
         *
         * @param codes       list of key codes to send in order
         * @param displayName the label shown on the key
         */
        record CodeFunc(List<KeyCode> codes, Component displayName) implements KeyFunction {

            public static final Codec<CodeFunc> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    KeyCode.CODEC.listOf().fieldOf("codes").forGetter(CodeFunc::codes),
                    ComponentSerialization.CODEC.fieldOf("display_name").forGetter(CodeFunc::displayName)
            ).apply(instance, CodeFunc::new));

            /**
             * A raw GLFW key event triple.
             *
             * @param keycode  logical GLFW key code (use {@code InputConstants.KEY_*})
             * @param scancode physical scancode (usually 0)
             * @param modifier GLFW modifier bitset (e.g. {@code GLFW_MOD_SHIFT})
             */
            public record KeyCode(int keycode, int scancode, int modifier) {
                public static final Codec<KeyCode> CODEC = Codec.withAlternative(
                        RecordCodecBuilder.create(instance -> instance.group(
                                Codec.INT.fieldOf("keycode").forGetter(KeyCode::keycode),
                                Codec.INT.optionalFieldOf("scancode", 0).forGetter(KeyCode::scancode),
                                Codec.INT.optionalFieldOf("modifier", 0).forGetter(KeyCode::modifier)
                        ).apply(instance, KeyCode::new)),
                        Codec.INT.xmap(kc -> new KeyCode(kc, 0, 0), KeyCode::keycode)
                );

                public KeyCode(int keycode) {
                    this(keycode, 0, 0);
                }
            }
        }

        // =====================================================================
        // SpecialFunc — named actions
        // =====================================================================

        /**
         * Performs a named special action when pressed (Shift, Backspace, Enter, etc.).
         *
         * @param action the action to perform
         */
        record SpecialFunc(Action action) implements KeyFunction {

            public static final Codec<SpecialFunc> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    StringRepresentable.fromEnum(Action::values)
                            .fieldOf("action")
                            .forGetter(SpecialFunc::action)
            ).apply(instance, SpecialFunc::new));

            @Override
            public Component displayName() {
                return action.displayName();
            }

            /** Named actions that the keyboard widget handles. */
            public enum Action implements StringRepresentable {
                SHIFT("shift"),
                SHIFT_LOCK("shift_lock"),
                ENTER("enter"),
                BACKSPACE("backspace"),
                TAB("tab"),
                LEFT_ARROW("left_arrow"),
                RIGHT_ARROW("right_arrow"),
                UP_ARROW("up_arrow"),
                DOWN_ARROW("down_arrow"),
                COPY_ALL("copy_all"),
                PASTE("paste"),
                PREVIOUS_LAYOUT("previous_layout");

                private final String id;

                Action(String id) { this.id = id; }

                @Override
                public @NotNull String getSerializedName() { return id; }

                /** Returns the localised display name for this action's key label. */
                public Component displayName() {
                    return Component.translatable("typehere.keyboard.special." + id);
                }
            }
        }

        // =====================================================================
        // ChangeLayoutFunc — switch to another layout
        // =====================================================================

        /**
         * Switches the keyboard widget to a different named layout when pressed.
         *
         * @param layout      resource-pack identifier of the target layout
         *                    (e.g. {@code typehere:symbols})
         * @param displayName label shown on the key (e.g. "?123")
         */
        record ChangeLayoutFunc(Identifier layout, Component displayName) implements KeyFunction {

            public static final Codec<ChangeLayoutFunc> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("layout").forGetter(ChangeLayoutFunc::layout),
                    ComponentSerialization.CODEC.fieldOf("display_name").forGetter(ChangeLayoutFunc::displayName)
            ).apply(instance, ChangeLayoutFunc::new));
        }
    }
}
