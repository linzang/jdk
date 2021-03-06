/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package nsk.jvmti.GetObjectMonitorUsage;

import nsk.share.Wicket;
import java.io.PrintStream;

public class objmonusage006 {
    private final static String AGENT_LIB = "objmonusage006";
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test

    final static int JCK_STATUS_BASE = 95;
    final static int WAIT_TIME = 100;

    static {
        try {
            System.loadLibrary(AGENT_LIB);
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load " + AGENT_LIB + " library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    public static Wicket startingBarrier;
    static Object lockCheck = new Object();

    native static void check(Object obj);
    native static int getRes();

    public static void main(String args[]) {
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        // produce JCK-like exit status.
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String args[], PrintStream out) {
        int timeMax = 0;
        if (args.length == 0) {
            timeMax = DEF_TIME_MAX;
        } else {
            try {
                timeMax = Integer.parseUnsignedInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("'" + args[0] + "': invalid timeMax value.");
                usage();
            }
        }

        System.out.println("About to execute for " + timeMax + " seconds.");

        long count = 0;
        int res = -1;
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;

            // Original objmonusage005 test block starts here:
            //
            LockingThread thr = new LockingThread();
            startingBarrier = new Wicket();
            thr.start();
            startingBarrier.waitFor();
            check(lockCheck);
            thr.letItGo();
            //
            // Original objmonusage005 test block ends here.

            res = getRes();
            if (res != 0) {
                return res;
            }

            // The worker thread is free to exit after the letItGo()
            // call above. This loop stresses the use of JVM/TI
            // GetObjectMonitorUsage() at thread exit time.
            synchronized (lockCheck) {
                // lockCheck needs to be locked for JVM/TI
                // GetObjectMonitorUsage() to cover the right
                // code paths.
                while (true) {
                    check(lockCheck);
                    res = getRes();
                    if (res != 0) {
                        break;
                    }

                    if (!thr.isAlive()) {
                        // Done with JVM/TI GetObjectMonitorUsage() calls
                        // since thread is not alive.
                        break;
                    }
                }
            }

            try {
                thr.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        return res;
    }

    public static void usage() {
        System.err.println("Usage: " + AGENT_LIB + " [time_max]");
        System.err.println("where:");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }

    static class LockingThread extends Thread {
        private volatile boolean flag = true;

        public synchronized void run() {
            synchronized (lockCheck) {
                startingBarrier.unlock();
                int i = 0;
                int n = 1000;
                while (flag) {
                    if (n <= 0) {
                        n = 1000;
                    }
                    if (i > n) {
                        i = 0;
                        n--;
                    }
                    i++;
                }
            }
        }

        public void letItGo() {
            flag = false;
        }
    }
}
