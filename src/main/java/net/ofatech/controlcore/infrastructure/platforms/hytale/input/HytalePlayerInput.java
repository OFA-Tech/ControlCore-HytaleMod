package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.CancelInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * Reads key signals from inbound packets: interaction chains (abilities, use, mouse buttons, hotbar, game mode
 * switch), movement states (held Ctrl, Alt, Shift, Space; a short Alt tap) and the {@code gm a}/{@code gm c} chat
 * command clients send for O without the game mode permission. Cancels a chain's game action when the listener
 * asks to, the way Hytale's packet filters are meant to be used. Runs on network threads.
 */
public final class HytalePlayerInput implements PlayerInput, PlayerPacketFilter {

    private static final long TAP_MILLIS = 500;
    private static final int MAX_CANCELLED = 256;
    private static final Set<String> GAME_MODE_COMMANDS =
            Set.of("gm a", "gm c", "gm adventure", "gm creative", "gamemode a", "gamemode c");

    private final Log log;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile Listener listener;
    private PacketFilter registration;

    public HytalePlayerInput(Log log) {
        this.log = log;
    }

    @Override
    public synchronized void listen(Listener listener) {
        this.listener = listener;
        if (registration == null) {
            registration = PacketAdapters.registerInbound(this);
        }
    }

    @Override
    public synchronized void stop() {
        listener = null;
        if (registration != null) {
            PacketAdapters.deregisterInbound(registration);
            registration = null;
        }
        states.clear();
    }

    @Override
    public boolean test(PlayerRef player, Packet packet) {
        Listener current = listener;
        if (current == null || player == null || player.getUuid() == null) {
            return false;
        }
        try {
            return switch (packet) {
                case SyncInteractionChains chains -> chains(player, current, chains);
                case ClientMovement movement -> {
                    movement(player, current, movement.movementStates);
                    yield false;
                }
                case ChatMessage chat ->
                    gameModeCommand(chat.message)
                            && current.pressed(PlayerId.of(player.getUuid()), KeySignal.GAME_MODE_SWAP);
                default -> false;
            };
        } catch (RuntimeException e) {
            log.error("Could not read key input of " + player.getUsername(), e);
            return false;
        }
    }

    /** @return whether to drop the whole packet */
    private boolean chains(PlayerRef player, Listener current, SyncInteractionChains chains) {
        SyncInteractionChain[] updates = chains.updates;
        if (updates == null || updates.length == 0) {
            return false;
        }
        PlayerId id = PlayerId.of(player.getUuid());
        State state = state(player.getUuid());
        List<SyncInteractionChain> kept = new ArrayList<>(updates.length);
        List<CancelInteractionChain> cancels = new ArrayList<>();
        for (SyncInteractionChain chain : updates) {
            if (chain == null) {
                continue;
            }
            if (state.cancelled.contains(chain.chainId)) {
                continue;
            }
            KeySignal signal = chain.initial ? signal(chain) : null;
            if (signal != null) {
                state.pressedDuringWalk = true;
                if (current.pressed(id, signal)) {
                    ForkedChainId forked = chain.forkedId == null ? null : new ForkedChainId(chain.forkedId);
                    cancels.add(new CancelInteractionChain(chain.chainId, forked));
                    if (state.cancelled.size() >= MAX_CANCELLED) {
                        state.cancelled.clear();
                    }
                    state.cancelled.add(chain.chainId);
                    continue;
                }
            }
            kept.add(chain);
        }
        if (kept.size() == updates.length) {
            return false;
        }
        cancels.forEach(cancel -> player.getPacketHandler().writeNoCache(cancel));
        chains.updates = kept.toArray(SyncInteractionChain[]::new);
        return kept.isEmpty();
    }

    private void movement(PlayerRef player, Listener current, MovementStates movement) {
        if (movement == null) {
            return;
        }
        PlayerId id = PlayerId.of(player.getUuid());
        State state = state(player.getUuid());
        Set<KeySignal> held = EnumSet.noneOf(KeySignal.class);
        if (movement.crouching) {
            held.add(KeySignal.CROUCHING);
        }
        if (movement.walking) {
            held.add(KeySignal.WALKING);
        }
        if (movement.sprinting) {
            held.add(KeySignal.SPRINTING);
        }
        if (movement.jumping) {
            held.add(KeySignal.JUMPING);
        }
        if (!movement.horizontalIdle) {
            held.add(KeySignal.MOVING);
        } else if (movement.idle) {
            held.add(KeySignal.STANDING_STILL);
        }
        boolean tapped = walkTap(state, movement);
        if (!held.equals(state.held)) {
            state.held = held;
            current.held(id, Set.copyOf(held));
        }
        if (tapped) {
            current.pressed(id, KeySignal.WALK_TAP);
        }
    }

    /** Tracks the walk key: a tap is a short hold without moving and without pressing anything else. */
    private static boolean walkTap(State state, MovementStates movement) {
        long now = System.currentTimeMillis();
        if (movement.walking) {
            if (state.walkStart < 0) {
                state.walkStart = now;
                state.pressedDuringWalk = false;
                state.movedDuringWalk = false;
            }
            state.movedDuringWalk |= !movement.horizontalIdle;
            return false;
        }
        if (state.walkStart < 0) {
            return false;
        }
        boolean tap = now - state.walkStart <= TAP_MILLIS && !state.movedDuringWalk && !state.pressedDuringWalk;
        state.walkStart = -1;
        return tap;
    }

    private static KeySignal signal(SyncInteractionChain chain) {
        if (chain.interactionType == null) {
            return null;
        }
        return switch (chain.interactionType) {
            case Primary -> KeySignal.PRIMARY;
            case Secondary -> KeySignal.SECONDARY;
            case Ability1 -> KeySignal.ABILITY_1;
            case Ability2 -> KeySignal.ABILITY_2;
            case Ability3 -> KeySignal.ABILITY_3;
            case Ability4 -> KeySignal.ABILITY_4;
            case Dodge -> KeySignal.DODGE;
            case Use -> KeySignal.USE;
            case Pick -> KeySignal.PICK;
            case GameModeSwap -> KeySignal.GAME_MODE_SWAP;
            case SwapFrom ->
                chain.data != null && chain.data.targetSlot >= 0 && chain.data.targetSlot < 9
                        ? KeySignal.hotbar(chain.data.targetSlot + 1)
                        : null;
            default -> null;
        };
    }

    private static boolean gameModeCommand(String message) {
        if (message == null) {
            return false;
        }
        String command = message.strip().toLowerCase(Locale.ROOT);
        command = command.startsWith("/") ? command.substring(1).strip() : command;
        return GAME_MODE_COMMANDS.contains(command);
    }

    private State state(UUID player) {
        return states.computeIfAbsent(player, ignored -> new State());
    }

    /** Per player; only touched by that player's network thread. */
    private static final class State {
        private final Set<Integer> cancelled = ConcurrentHashMap.newKeySet();
        private Set<KeySignal> held = Set.of();
        private long walkStart = -1;
        private boolean movedDuringWalk;
        private boolean pressedDuringWalk;
    }
}
