package functional.tests.core.utils;

import functional.tests.core.enums.OSType;
import functional.tests.core.log.LoggerBase;
import functional.tests.core.settings.Settings;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Utils for host operating system.
 */
public class OSUtils {

    private static final String[] WIN_RUNTIME = {"cmd.exe", "/C"};
    private static final String[] OS_LINUX_RUNTIME = {"/bin/bash", "-l", "-c"};
    private static final LoggerBase LOGGER_BASE = LoggerBase.getLogger("OSUtils");

    private static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Get screenshot of host OS.
     *
     * @param fileName Name of file where screenshot will be saved.
     * @param settings Settings object.
     */
    public static void getScreenshot(String fileName, Settings settings) {
        File file = new File(settings.screenshotOutDir + File.separator + fileName + ".png");
        try {
            BufferedImage image = new Robot()
                    .createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            LOGGER_BASE.debug("Save Picture: " + file.getAbsolutePath());
            ImageIO.write(image, "png", file);
            LOGGER_BASE.error("Save screenshot of host OS: " + file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER_BASE.error("Faield to take host OS screenshot.");
        }
    }

    /**
     * Run command (start process).
     *
     * @param waitFor Wait for process to finish.
     * @param timeOut Timeout for process.
     * @param command Command to be executed.
     * @return Output of command execution.
     */
    public static String runProcess(boolean waitFor, int timeOut, String... command) {
        String[] allCommand = null;

        String finalCommand = "";
        for (String s : command) {
            finalCommand = finalCommand + s;
        }

        try {
            if (Settings.os == OSType.Windows) {
                allCommand = concat(WIN_RUNTIME, command);
            } else {
                allCommand = concat(OS_LINUX_RUNTIME, command);
            }
            ProcessBuilder pb = new ProcessBuilder(allCommand);
            Process p = pb.start();

            if (waitFor) {
                StringBuffer output = new StringBuffer();

                // Note: No idea why reader should be before p.waitFor(),
                //       but when it is after p.waitFor() execution of
                //       some adb command freeze on Windows
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));

                String line = "";
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }

                p.waitFor(timeOut, TimeUnit.SECONDS);

                LOGGER_BASE.debug("Execute command: " + finalCommand);
                LOGGER_BASE.trace("Result: " + output.toString());

                return output.toString();
            } else {
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Execute command (start process).
     *
     * @param command Command.
     * @return Output of command execution.
     */
    public static String runProcess(String... command) {
        return runProcess(true, 10 * 60, command); // Might be we should .trim()
    }

    /**
     * Execute command (start process).
     *
     * @param timeOut Timeout for command execution in secconds.
     * @param command Command.
     * @return Output of command execution.
     */
    public static String runProcess(int timeOut, String... command) {
        return runProcess(true, timeOut, command);
    }

    /**
     * Stop process.
     *
     * @param name Name of running process.
     */
    public static void stopProcess(String name) {
        try {
            if (Settings.os == OSType.Windows) {
                String command = "taskkill /F /IM " + name;
                runProcess(command);
            } else {
                String stopCommand = "ps -A | grep '" + name + "'";

                String processes = runProcess(stopCommand);
                String lines[] = processes.split("(\r\n|\n)");

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    String procId = line.split("\\s+")[0];
                    runProcess("kill -9 " + procId);
                }
            }
        } catch (Exception e) {
            LOGGER_BASE.debug("Failed to fullStop process: " + name);
        }
    }

    /**
     * This is a convenience method that calls find(File, String, boolean) with
     * the last parameter set to "false" (does not match directories).
     *
     * @see #find(File, String, boolean)
     */
    public static File find(File contextRoot, String fileName) {
        return find(contextRoot, fileName, false);
    }

    /**
     * Searches through the directory tree under the given context directory and
     * finds the first file that matches the file name. If the third parameter is
     * true, the method will also try to match directories, not just "regular"
     * files.
     *
     * @param contextRoot      The directory to start the search from.
     * @param fileName         The name of the file (or directory) to search for.
     * @param matchDirectories True if the method should try and match the name against directory
     *                         names, not just file names.
     * @return The java.io.File representing the <em>first</em> file or
     * directory with the given name, or null if it was not found.
     */
    public static File find(File contextRoot, String fileName, boolean matchDirectories) {
        if (contextRoot == null) {
            throw new NullPointerException("NullContextRoot");
        }

        if (fileName == null) {
            throw new NullPointerException("NullFileName");
        }

        if (!contextRoot.isDirectory()) {
            Object[] filler = {contextRoot.getAbsolutePath()};
            String message = "NotDirectory";
            throw new IllegalArgumentException(message);
        }

        File[] files = contextRoot.listFiles();

        //
        // for all children of the current directory...
        //
        for (int n = 0; n < files.length; ++n) {
            String nextName = files[n].getName();

            //
            // if we find a directory, there are two possibilities:
            //
            // 1. the names match, AND we are told to match directories.
            // in this case we're done
            //
            // 2. not told to match directories, so recurse
            //
            if (files[n].isDirectory()) {
                if (nextName.equals(fileName) && matchDirectories) {
                    return files[n];
                }

                File match = find(files[n], fileName);

                if (match != null) {
                    return match;
                }
            } else if (nextName.equals(fileName)) {
                // in the case of regular files, just check the names
                return files[n];
            }
        }

        return null;
    }
}
