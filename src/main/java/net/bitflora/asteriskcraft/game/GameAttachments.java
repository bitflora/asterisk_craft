package net.bitflora.asteriskcraft.game;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.director.script.InterpreterState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Persistent match state, attached to the overworld ServerLevel so it saves with it.
 *
 * <p>Note what is <em>not</em> here any more: where the bases stand. That was two attachments, one
 * singular and one plural, written once at bootstrap — so neither could see a base warped in from a
 * kit afterwards. {@link net.bitflora.asteriskcraft.building.CoreCensus} is the single answer to
 * "where are this faction's bases", enrolled from each base's own tick, and it is symmetric across
 * the two sides in a way a "the Nexus" / "the Hives" pair never was.
 */
public final class GameAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    /** True once both sides' starting bases have been placed in this world. */
    public static final Supplier<AttachmentType<Boolean>> BOOTSTRAPPED = ATTACHMENT_TYPES.register(
            "bootstrapped", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** Which side the human commands and which the computer plays; written once at bootstrap. */
    public static final Supplier<AttachmentType<MatchSetup>> MATCH_SETUP = ATTACHMENT_TYPES.register(
            "match_setup", () -> AttachmentType.builder(() -> MatchSetup.DEFAULT)
                    .serialize(MatchSetup.CODEC.fieldOf("setup")).build());

    /** Set once the match has been decided (victory or defeat) so the outcome fires exactly once. */
    public static final Supplier<AttachmentType<Boolean>> GAME_OVER = ATTACHMENT_TYPES.register(
            "game_over", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    /** The AI build-script interpreter's execution cursor (program counter, worker floor, batch). */
    public static final Supplier<AttachmentType<InterpreterState>> INTERPRETER_STATE = ATTACHMENT_TYPES.register(
            "interpreter_state", () -> AttachmentType.builder(() -> InterpreterState.INITIAL)
                    .serialize(InterpreterState.CODEC.fieldOf("state")).build());

    private GameAttachments() {
    }
}
