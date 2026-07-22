/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Linear undo/redo history of immutable states. {@link #push} records a new current state (clearing redo);
 * the oldest entries are dropped beyond {@link #capacity()} undo steps.
 */
public final class EditHistory<T> {

    /** Default undo depth (spec requires ≥ 50). */
    public static final int DEFAULT_CAPACITY = 100;

    private final int capacity;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();
    private T current;

    public EditHistory(final T initial, final int capacity) {
        this.current = Objects.requireNonNull(initial);
        this.capacity = Math.max(1, capacity);
    }

    public EditHistory(final T initial) {
        this(initial, EditHistory.DEFAULT_CAPACITY);
    }

    public T current() {
        return this.current;
    }

    public int capacity() {
        return this.capacity;
    }

    /** Records a new state. Equal states (by {@code equals}) are ignored. Returns {@code true} if recorded. */
    public boolean push(final T state) {
        Objects.requireNonNull(state);
        if (state.equals(this.current)) {
            return false;
        }
        this.undo.push(this.current);
        while (this.undo.size() > this.capacity) {
            this.undo.removeLast();
        }
        this.redo.clear();
        this.current = state;
        return true;
    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    public int undoSize() {
        return this.undo.size();
    }

    public int redoSize() {
        return this.redo.size();
    }

    /** Steps back; returns the new current state or {@code null} if nothing to undo. */
    public T undo() {
        if (this.undo.isEmpty()) {
            return null;
        }
        this.redo.push(this.current);
        this.current = this.undo.pop();
        return this.current;
    }

    /** Steps forward; returns the new current state or {@code null} if nothing to redo. */
    public T redo() {
        if (this.redo.isEmpty()) {
            return null;
        }
        this.undo.push(this.current);
        this.current = this.redo.pop();
        return this.current;
    }
}
