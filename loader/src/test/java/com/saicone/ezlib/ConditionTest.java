package com.saicone.ezlib;

import com.saicone.ezlib.EzlibLoader.Condition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionTest {

    private static final Condition<String> STRING_CONDITION = Condition.valueOf(s -> s.equals("value1") || s.equals("value2"));
    private static final Condition<Integer> INTEGER_CONDITION = Condition.valueOfInteger(() -> 10);

    @Test
    public void testCondition() {
        assertTrue(STRING_CONDITION.eval(Condition.EQUAL, "value1"));
        assertTrue(STRING_CONDITION.eval(Condition.EQUAL, "value2"));
        assertFalse(STRING_CONDITION.eval(Condition.EQUAL, "asd"));
        assertFalse(STRING_CONDITION.eval(Condition.EQUAL, "value3"));

        assertTrue(INTEGER_CONDITION.eval(Condition.LESS, "5"));
        assertTrue(INTEGER_CONDITION.eval(Condition.LESS_OR_EQUAL, "5"));
        assertTrue(INTEGER_CONDITION.eval(Condition.LESS_OR_EQUAL, "10"));
        assertTrue(INTEGER_CONDITION.eval(Condition.EQUAL, "10"));
        assertTrue(INTEGER_CONDITION.eval(Condition.GREATER_OR_EQUAL, "10"));
        assertTrue(INTEGER_CONDITION.eval(Condition.GREATER_OR_EQUAL, "15"));
        assertTrue(INTEGER_CONDITION.eval(Condition.GREATER, "15"));

        assertFalse(INTEGER_CONDITION.eval(Condition.LESS, "15"));
        assertFalse(INTEGER_CONDITION.eval(Condition.LESS_OR_EQUAL, "15"));
        assertFalse(INTEGER_CONDITION.eval(Condition.LESS_OR_EQUAL, "11"));
        assertFalse(INTEGER_CONDITION.eval(Condition.EQUAL, "20"));
        assertFalse(INTEGER_CONDITION.eval(Condition.GREATER_OR_EQUAL, "9"));
        assertFalse(INTEGER_CONDITION.eval(Condition.GREATER_OR_EQUAL, "5"));
        assertFalse(INTEGER_CONDITION.eval(Condition.GREATER, "5"));
    }

    @Test
    public void testConditions() {
        final EzlibLoader loader = new EzlibLoader(false);
        loader.condition("isPresent", Condition.valueOf(true));
        loader.condition("isNotPresent", Condition.valueOf(false));
        loader.condition("text", STRING_CONDITION);
        loader.condition("number", INTEGER_CONDITION);

        assertNull(loader.eval("this condition does not exist"));
        assertNull(loader.eval("this condition does not exist = value"));
        assertNull(loader.eval("this condition does not exist >= value"));

        assertEquals(Boolean.TRUE, loader.eval("isPresent"));
        assertEquals(Boolean.TRUE, loader.eval("isPresent=true"));
        assertEquals(Boolean.FALSE, loader.eval("!isPresent"));
        assertEquals(Boolean.FALSE, loader.eval("isPresent=false"));

        assertEquals(Boolean.FALSE, loader.eval("isNotPresent"));
        assertEquals(Boolean.FALSE, loader.eval("isNotPresent=true"));
        assertEquals(Boolean.TRUE, loader.eval("!isNotPresent"));
        assertEquals(Boolean.TRUE, loader.eval("isNotPresent=false"));

        assertEquals(Boolean.TRUE, loader.eval("text=value1"));
        assertEquals(Boolean.TRUE, loader.eval("text == value2"));
        assertEquals(Boolean.FALSE, loader.eval("text=asd"));
        assertEquals(Boolean.FALSE, loader.eval("text == value3"));

        assertEquals(Boolean.TRUE, loader.eval("number < 5"));
        assertEquals(Boolean.TRUE, loader.eval("number <= 5"));
        assertEquals(Boolean.TRUE, loader.eval("number <= 10"));
        assertEquals(Boolean.TRUE, loader.eval("number == 10"));
        assertEquals(Boolean.TRUE, loader.eval("number >= 10"));
        assertEquals(Boolean.TRUE, loader.eval("number >= 15"));
        assertEquals(Boolean.TRUE, loader.eval("number > 15"));

        assertEquals(Boolean.FALSE, loader.eval("number < 15"));
        assertEquals(Boolean.FALSE, loader.eval("number <= 15"));
        assertEquals(Boolean.FALSE, loader.eval("number <= 11"));
        assertEquals(Boolean.FALSE, loader.eval("number == 20"));
        assertEquals(Boolean.FALSE, loader.eval("number >= 9"));
        assertEquals(Boolean.FALSE, loader.eval("number >= 5"));
        assertEquals(Boolean.FALSE, loader.eval("number > 5"));
    }
}
