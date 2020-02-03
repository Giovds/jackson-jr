package com.fasterxml.jackson.jr.ob.impl;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.TestBase;
import com.fasterxml.jackson.jr.ob.api.ReaderWriterProvider;
import com.fasterxml.jackson.jr.ob.api.ValueReader;

public class CustomValueReadersTest extends TestBase
{
    static class CustomValue {
        public int value;

        // 2nd arg just to avoid discovery
        public CustomValue(int v, boolean b) {
            // and to ensure it goes through constructor, add 1
            value = v + 1;
        }
    }

    static class CustomValueBean {
        public CustomValue custom;

        protected CustomValueBean() { }
        public CustomValueBean(int v) {
            custom = new CustomValue(v, false);
        }
    }

    enum ABC {
        A, B, C, DEF;
    }
    
    static class CustomValueReader extends ValueReader {
        private final int delta;

        public CustomValueReader(int d) {
            super(CustomValue.class);
            delta = d;
        }

        @Override
        public Object read(JSONReader reader, JsonParser p) throws IOException {
            return new CustomValue(p.getIntValue() + delta, true);
        }

        // Base class impl should be fine, although we'd use this for optimal
        /*
        @Override
        public Object readNext(JSONReader reader, JsonParser p) throws IOException {
            return new CustomValue(p.nextIntValue(-1), true);
        }
        */
    }

    static class ABCValueReader extends ValueReader {
        public ABCValueReader() {
            super(ABC.class);
        }

        @Override
        public Object read(JSONReader reader, JsonParser p) throws IOException {
            final String str = p.getText();
            if ("n/a".equals(str)) {
                return ABC.DEF;
            }
            return ABC.valueOf(str);
        }
    }

    static class CapStringReader extends ValueReader {
        public CapStringReader() {
            super(String.class);
        }

        @Override
        public Object read(JSONReader reader, JsonParser p) throws IOException {
            return p.getText().toUpperCase();
        }
    }

    static class CustomReaders extends ReaderWriterProvider {
        final int delta;

        public CustomReaders(int d) {
            delta = d;
        }

        @Override
        public ValueReader findValueReader(JSONReader readContext, Class<?> type) {
            if (type.equals(CustomValue.class)) {
                return new CustomValueReader(delta);
            } else if (type.equals(ABC.class)) {
                return new ABCValueReader();
            }
            return null;
        }
    }

    static class StringReaderProvider extends ReaderWriterProvider {
        @Override
        public ValueReader findValueReader(JSONReader readContext, Class<?> type) {
            if (type.equals(String.class)) {
                return new CapStringReader();
            }
            return null;
        }
    }

    static class Point {
        public int _x, _y;

        public Point(int x, int y) {
            _x = x;
            _y = y;
        }
    }

    static class PointReader extends ValueReader {
        public PointReader() { super(Point.class); }

        @Override
        public Object read(JSONReader reader, JsonParser p) throws IOException {
            Map<String, Object> map = reader.readMap();
            return new Point((Integer) map.get("x"), (Integer) map.get("y"));
        }
    }

    static class PointReaderProvider extends ReaderWriterProvider {
        @Override
        public ValueReader findValueReader(JSONReader readContext, Class<?> type) {
            if (type == Point.class) {
                return new PointReader();
            }
            return null;
        }
    }

    /*
    /**********************************************************************
    /* Test methdods
    /**********************************************************************
     */

    public void testCustomBeanReader() throws Exception
    {
        // First: without handler, will fail to map
        try {
            JSON.std.beanFrom(CustomValue.class, "123");
            fail("Should not pass");
        } catch (JSONObjectException e) {
            verifyException(e, ".CustomValue");
            verifyException(e, "constructor to use");
        }

        // then with custom, should be fine
        JSON json = jsonWithProvider(new CustomReaders(0));
        CustomValue v = json.beanFrom(CustomValue.class, "123");
        assertEquals(124, v.value);

        // similarly with wrapper
        CustomValueBean bean = json.beanFrom(CustomValueBean.class,
                aposToQuotes("{ 'custom' : 137 }"));
        assertEquals(138, bean.custom.value);

        // but also ensure we can change registered handler(s)
        JSON json2 = jsonWithProvider(new CustomReaders(100));
        v = json2.beanFrom(CustomValue.class, "123");
        assertEquals(224, v.value);
    }

    public void testCustomEnumReader() throws Exception
    {
        // First: without handler, will fail to map
        try {
            JSON.std.beanFrom(ABC.class, quote("n/a"));
            fail("Should not pass");
        } catch (JSONObjectException e) {
            verifyException(e, "Failed to find Enum of type");
        }

        // then with custom, should be fine
        JSON json = jsonWithProvider(new CustomReaders(0));
        ABC v = json.beanFrom(ABC.class, quote("n/a"));
        assertEquals(ABC.DEF, v);

        // but if we remove, again error
        JSON json2 = jsonWithProvider((ReaderWriterProvider) null);
        try {
            json2.beanFrom(ABC.class, quote("n/a"));
            fail("Should not pass");
        } catch (JSONObjectException e) {
            verifyException(e, "Failed to find Enum of type");
        }
    }

    // Even more fun, override default String deserializer!
    public void testCustomStringReader() throws Exception
    {
        String allCaps = jsonWithProvider(new StringReaderProvider())
                .beanFrom(String.class, quote("Some text"));
        assertEquals("SOME TEXT", allCaps);
    }

    // But also can use methods from "JSONReader" for convenience
    public void testCustomDelegatingReader() throws Exception
    {
        // First: without handler, will fail to map
        final String doc = "{\"y\" : 3, \"x\": 2 }";
        try {
            JSON.std.beanFrom(Point.class, doc);
            fail("Should not pass");
        } catch (JSONObjectException e) {
            verifyException(e, "$Point");
            verifyException(e, "constructor to use");
        }

        // then with custom, should be fine
        JSON json = jsonWithProvider(new PointReaderProvider());
        Point v = json.beanFrom(Point.class, doc);
        assertEquals(2, v._x);
        assertEquals(3, v._y);
    }
}
