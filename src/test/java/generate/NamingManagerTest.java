package generate;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by manuel on 09.02.17.
 */
public class NamingManagerTest {

    NamingManager namingManager;

    @Before
    public void setup() {
        namingManager = new NamingManager();
    }

    @Test
    public void testRenamingDot() {
        assertEquals("some_test", namingManager.getClassNameFor("some.test"));
    }

    @Test
    public void testRenamingSpace1() {
        assertEquals("someTest", namingManager.getClassNameFor("some test"));
    }

    @Test
    public void testRenamingSpace2() {
        assertEquals("someMoreTests", namingManager.getClassNameFor(" some more tests "));
    }
}
