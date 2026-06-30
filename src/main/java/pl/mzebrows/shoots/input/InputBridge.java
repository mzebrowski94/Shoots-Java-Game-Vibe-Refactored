// pl/mzebrows/shoots/input/InputBridge.java
package pl.mzebrows.shoots.input;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe bridge between AWT EDT key events and the game-loop thread.
 * The EDT writes raw key state via {@link KeyListener}; the game loop reads mapped
 * {@link GameAction}s after calling {@link #poll()} once per frame.
 */
public final class InputBridge implements KeyListener {

    private final Map<Integer, EnumSet<GameAction>> keyMap;

    // Written exclusively by EDT; read by game-loop thread only via poll()
    private final EnumSet<GameAction> edtHeld = EnumSet.noneOf(GameAction.class);

    // Game-loop thread only – never touched by EDT
    private EnumSet<GameAction> current = EnumSet.noneOf(GameAction.class);
    private EnumSet<GameAction> previous = EnumSet.noneOf(GameAction.class);

    // Text-entry capture for menu fields (host IP / port). The EDT appends typed characters via
    // keyTyped; the game-loop thread drains them once per frame. Off by default so normal play is
    // unaffected (digits/'.' are not bound to any GameAction, so they only flow through here).
    private final StringBuilder edtTyped = new StringBuilder();
    private volatile boolean capturingText = false;

    public InputBridge(Map<Integer, EnumSet<GameAction>> keyMap) {
        var copy = new HashMap<Integer, EnumSet<GameAction>>();
        keyMap.forEach((k, v) -> copy.put(k, EnumSet.copyOf(v)));
        this.keyMap = Collections.unmodifiableMap(copy);
    }

    /** Builds an {@link InputBridge} with key bindings that match the legacy layout. */
    public static InputBridge withDefaultKeyMap() {
        var map = new HashMap<Integer, EnumSet<GameAction>>();
        // Player 1 – arrow keys; VK_UP/LEFT/RIGHT double as menu navigation
        map.put(KeyEvent.VK_LEFT,  EnumSet.of(GameAction.P1_ROTATE_LEFT,  GameAction.NAVIGATE_LEFT));
        map.put(KeyEvent.VK_RIGHT, EnumSet.of(GameAction.P1_ROTATE_RIGHT, GameAction.NAVIGATE_RIGHT));
        map.put(KeyEvent.VK_UP,    EnumSet.of(GameAction.P1_SHOOT,        GameAction.NAVIGATE_UP));
        map.put(KeyEvent.VK_DOWN,  EnumSet.of(GameAction.NAVIGATE_DOWN));
        // Player 2 – WASD
        map.put(KeyEvent.VK_A, EnumSet.of(GameAction.P2_ROTATE_LEFT));
        map.put(KeyEvent.VK_D, EnumSet.of(GameAction.P2_ROTATE_RIGHT));
        map.put(KeyEvent.VK_W, EnumSet.of(GameAction.P2_SHOOT));
        // Player 3 – numpad
        map.put(KeyEvent.VK_NUMPAD4, EnumSet.of(GameAction.P3_ROTATE_LEFT));
        map.put(KeyEvent.VK_NUMPAD6, EnumSet.of(GameAction.P3_ROTATE_RIGHT));
        map.put(KeyEvent.VK_NUMPAD8, EnumSet.of(GameAction.P3_SHOOT));
        // Player 4 – IJL
        map.put(KeyEvent.VK_L, EnumSet.of(GameAction.P4_ROTATE_LEFT));
        map.put(KeyEvent.VK_J, EnumSet.of(GameAction.P4_ROTATE_RIGHT));
        map.put(KeyEvent.VK_I, EnumSet.of(GameAction.P4_SHOOT));
        // System / menu
        map.put(KeyEvent.VK_ENTER,  EnumSet.of(GameAction.CONFIRM));
        map.put(KeyEvent.VK_ESCAPE, EnumSet.of(GameAction.PAUSE));
        return new InputBridge(map);
    }

    /**
     * Snapshots the EDT-written key state into the game-loop thread's view.
     * Must be called once per frame on the game-loop thread before querying actions.
     */
    public void poll() {
        previous = current;
        synchronized (edtHeld) {
            current = edtHeld.isEmpty()
                    ? EnumSet.noneOf(GameAction.class)
                    : EnumSet.copyOf(edtHeld);
        }
    }

    /** @return {@code true} while the action's key is held down. */
    public boolean isHeld(GameAction action) {
        return current.contains(action);
    }

    /** @return {@code true} only on the first frame the action's key is pressed. */
    public boolean isJustPressed(GameAction action) {
        return current.contains(action) && !previous.contains(action);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        var actions = keyMap.get(e.getKeyCode());
        if (actions != null) {
            synchronized (edtHeld) {
                edtHeld.addAll(actions);
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        var actions = keyMap.get(e.getKeyCode());
        if (actions != null) {
            synchronized (edtHeld) {
                edtHeld.removeAll(actions);
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (!capturingText) {
            return;
        }
        char c = e.getKeyChar();
        if (Character.isDigit(c) || c == '.' || c == '\b') {
            synchronized (edtTyped) {
                edtTyped.append(c);
            }
        }
    }

    /** Enables/disables raw text capture (menu IP/port fields); disabling also clears any pending text. */
    public void setTextCapture(boolean on) {
        capturingText = on;
        if (!on) {
            synchronized (edtTyped) {
                edtTyped.setLength(0);
            }
        }
    }

    /** Drains characters typed since the last call -- digits, {@code '.'}, and {@code '\b'} (backspace). */
    public String drainTypedText() {
        synchronized (edtTyped) {
            if (edtTyped.isEmpty()) {
                return "";
            }
            String text = edtTyped.toString();
            edtTyped.setLength(0);
            return text;
        }
    }

    /**
     * Human-readable name of the first key bound to {@code action} (e.g. {@code "Left"}, {@code "W"},
     * {@code "Num 4"}), or {@code "-"} if nothing is bound. Used by the menu's controls screen so the
     * displayed bindings stay truthful to the actual key map.
     */
    public String keyNameFor(GameAction action) {
        return keyMap.entrySet().stream()
                .filter(e -> e.getValue().contains(action))
                .map(Map.Entry::getKey)
                .min(Integer::compareTo)
                .map(KeyEvent::getKeyText)
                .orElse("-");
    }
}
