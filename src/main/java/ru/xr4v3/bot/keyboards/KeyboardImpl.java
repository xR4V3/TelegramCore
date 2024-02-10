package ru.xr4v3.bot.keyboards;

import com.pengrad.telegrambot.model.request.KeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class KeyboardImpl {
    private final List<KeyboardButton[]> rows = new ArrayList<>();
    private KeyboardButton[] currentRow = new KeyboardButton[0];
    private boolean resizeKeyboard;
    private boolean oneTimeKeyboard;

    public KeyboardImpl resizeKeyboard(boolean resize) {
        this.resizeKeyboard = resize;
        return this;
    }

    public KeyboardImpl oneTimeKeyboard(boolean oneTime) {
        this.oneTimeKeyboard = oneTime;
        return this;
    }

    public KeyboardImpl addButton(String buttonText) {
        KeyboardButton[] newRow = new KeyboardButton[currentRow.length + 1];
        System.arraycopy(currentRow, 0, newRow, 0, currentRow.length);
        newRow[currentRow.length] = new KeyboardButton(buttonText);
        currentRow = newRow;
        return this;
    }

    public KeyboardImpl completeRow() {
        rows.add(currentRow);
        currentRow = new KeyboardButton[0];
        return this;
    }
}
