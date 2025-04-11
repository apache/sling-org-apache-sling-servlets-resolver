package org.apache.sling.servlets.resolver.it.resourcehiding;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NoHidingIT extends ResourceHidingITBase {

    @After
    public void checkNothingHidden() {
        assertEquals(0, hiddenResourcesCount);
    }

    @Test
    public void testExtApresent() throws Exception {
        assertTestServlet("/." + EXT_A, EXT_A);
    }

    @Test
    public void testExtBpresent() throws Exception {
        assertTestServlet("/." + EXT_B, EXT_B);
    }

    @Test
    public void testSelApresent() throws Exception {
        assertTestServlet("/." + SEL_A + "." + EXT_A, SEL_A);
    }
}
