import clover.com.google.common.collect.Lists;
import clover.org.apache.commons.lang.StringUtils;
import com.atlassian.clover.CloverStartup;
import com.atlassian.clover.Logger;
import com.atlassian.clover.instr.aspectj.CloverAjc;
import com.atlassian.clover.reporters.html.HtmlReporter;
import com.atlassian.clover.util.FileUtils;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A high-level test for {@link com.atlassian.clover.instr.aspectj.CloverAjc}
 */
public class CloverAjcTest {

    public static final String AJC_EXAMPLE_DIR = FileUtils.getPlatformSpecificPath("src/test/resources/ajc-example");
    public static final String CLOVER_DB_PATH = FileUtils.getPlatformSpecificPath(AJC_EXAMPLE_DIR + "/target/clover/db/clover.db");
    public static final String CLOVER_REPORT_PATH = FileUtils.getPlatformSpecificPath(AJC_EXAMPLE_DIR + "/target/clover/report");
    public static final String SOURCE_DIR = FileUtils.getPlatformSpecificPath(AJC_EXAMPLE_DIR + "/src");
    public static final String TARGET_CLASSES_DIR = FileUtils.getPlatformSpecificPath(AJC_EXAMPLE_DIR + "/target/classes");

    @Test
    public void testAjcExampleWithClover() {
        // build aspectj project with clover
        final String[] compilerArgs = {
                // Clover stuff
                "--initstring", CLOVER_DB_PATH,
                "--instrlevel", "statement",
                // AJC stuff
                "-sourceroots", SOURCE_DIR,
                "-d", TARGET_CLASSES_DIR,
                "-noExit",
                "-1.6" // source/target=1.6
        };
        final List<String> failures = Lists.newArrayList();
        final List<String> errors = Lists.newArrayList();
        final List<String> warnings = Lists.newArrayList();
        final List<String> infos = Lists.newArrayList();
        final int errorsCount = CloverAjc.bareMain(compilerArgs, false, failures, errors, warnings, infos);

        printMessages("FAILURES", failures);
        printMessages("ERRORS", errors);
        printMessages("WARNINGS", warnings);
        printMessages("INFOS", infos);

        assertEquals("Instrumentation failed with " + errorsCount + " errors", 0, errorsCount);
        assertTrue(new File(TARGET_CLASSES_DIR, "introduction/CloneablePoint.class").exists());
        assertTrue(new File(TARGET_CLASSES_DIR, "instrumentation/StatementInstrumentation.class").exists());
        assertTrue(new File(CLOVER_DB_PATH).exists());

        // run classes
        final String M2 = System.getProperty("user.home") + "/.m2/repository/";
        final String CLASSPATH = FileUtils.getPlatformSpecificPath(
                M2 + "org/aspectj/aspectjrt/1.8.9/aspectjrt-1.8.9.jar" + File.pathSeparator
                + M2 + "com/atlassian/clover/clover/4.1.2/clover-4.1.2.jar" + File.pathSeparator
                + TARGET_CLASSES_DIR);
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.Point");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.CloneablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.ComparablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.HashablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "instrumentation.StatementInstrumentation");

        // generate html report
        CloverStartup.loadLicense(Logger.getInstance());
        final String [] reporterArgs = {
                "-i", CLOVER_DB_PATH,
                "-o", CLOVER_REPORT_PATH,
                "-a",
                "-e"
        };
        final int reporterExitCode = HtmlReporter.runReport(reporterArgs);
        assertEquals(0, reporterExitCode);
        assertTrue(new File(CLOVER_REPORT_PATH, "dashboard.html").exists());
    }

    private void printMessages(final String title, final List<String> messages) {
        if (!messages.isEmpty()) {
            System.out.println("=== " + title + " ===");
            System.out.println(StringUtils.join(messages, "\n"));
        }
    }
}
