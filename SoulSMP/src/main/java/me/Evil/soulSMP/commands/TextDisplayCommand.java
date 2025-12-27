package me.Evil.soulSMP.commands;

import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Simple in-memory edit session for a TextDisplay.
 *
 * - Stores lines as a List<String>
 * - Rebuilds display text via joining with "\n"
 * - Not persisted (intended for spawn-building workflows)
 */
public class TextDisplayCommand {

    private final UUID playerId;
    private final TextDisplay display;
    private final List<String> lines = new ArrayList<>();

    public TextDisplayCommand(UUID playerId, TextDisplay display) {
        this.playerId = playerId;
        this.display = display;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public TextDisplay getDisplay() {
        return display;
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    /**
     * Load current display text into editable lines (splits on \n).
     */
    public void loadFromCurrentText() {
        lines.clear();
        String current = display.getText();
        if (current == null || current.isEmpty()) return;
        lines.addAll(Arrays.asList(current.split("\\n", -1)));
    }

    public void setLine(int index, String text) {
        ensureSize(index + 1);
        lines.set(index, text == null ? "" : text);
        rebuild();
    }

    public void clearLine(int index) {
        if (index < 0) return;
        ensureSize(index + 1);
        lines.set(index, "");
        trimTrailingEmpty();
        rebuild();
    }

    private void ensureSize(int size) {
        while (lines.size() < size) lines.add("");
    }

    private void trimTrailingEmpty() {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String s = lines.get(i);
            if (s != null && !s.isEmpty()) break;
            lines.remove(i);
        }
    }

    private void rebuild() {
        display.setText(String.join("\n", lines));
    }
}
