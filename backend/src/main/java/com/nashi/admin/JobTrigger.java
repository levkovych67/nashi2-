package com.nashi.admin;

/** A long-running job an administrator can start from the group. Registered as a bean named after the command. */
public interface JobTrigger {

    /** Starts the job in the background; false when it is already running. */
    boolean startInBackground();
}
