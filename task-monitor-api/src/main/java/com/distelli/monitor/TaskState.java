package com.distelli.monitor;

public enum TaskState {
    QUEUED(false),
    RUNNING(false),
    WAITING_FOR_INTERVAL(false),
    WAITING_FOR_PREREQUISITE(false),
    WAITING_FOR_LOCK(false),
    FAILED(true),
    SUCCESS(true),
    CANCELED(true);

    private boolean terminal;
    private TaskState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
