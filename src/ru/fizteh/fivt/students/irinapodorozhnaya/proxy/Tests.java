package ru.fizteh.fivt.students.irinapodorozhnaya.proxy;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ru.fizteh.fivt.proxy.LoggingProxyFactory;
import ru.fizteh.fivt.storage.structured.TableProvider;
import ru.fizteh.fivt.students.irinapodorozhnaya.storeable.MyTableProvider;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class Tests {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    File f;
    TableProvider provider;
    List<Class<?>> list;
    LoggingProxyFactory proxy = new LoggingProxyFactoryImpl();
    StringWriter writer;

    @Before
    public void setUp() throws Exception {
        f = folder.newFolder();
        provider = new MyTableProvider(f);
        list = new ArrayList<>();
        list.add(Integer.class);
        list.add(String.class);
        writer = new StringWriter();
        provider = (TableProvider) proxy.wrap(writer, provider, TableProvider.class);
    }

    @Test
    public void createTableNormalTest() throws Exception {
        provider.createTable("table", list);
        JSONObject jsonObject = new JSONObject(writer.toString());
        File file = new File(f, "table");
        Assert.assertEquals(jsonObject.get("returnValue"), "MyTable[" + file.getAbsolutePath() + "]");
        Assert.assertEquals(jsonObject.get("name"), "createTable");
        Assert.assertEquals(jsonObject.get("class"),
                "ru.fizteh.fivt.students.irinapodorozhnaya.storeable.MyTableProvider");
        Assert.assertEquals(jsonObject.getJSONArray("arguments").toString(),
                "[\"table\",[\"class java.lang.Integer\",\"class java.lang.String\"]]");
        jsonObject.getLong("timestamp");
    }

    @Test (expected = IllegalArgumentException.class)
    public void createTableIllegalSymbols() throws Exception {
        try {
            provider.createTable("table^#", list);
        } catch (IllegalArgumentException e) {
            JSONObject jsonObject = new JSONObject(writer.toString());
            Assert.assertEquals(jsonObject.get("thrown"), e.toString());
            Assert.assertEquals(jsonObject.get("name"), "createTable");
            Assert.assertEquals(jsonObject.get("class"),
                    "ru.fizteh.fivt.students.irinapodorozhnaya.storeable.MyTableProvider");
            Assert.assertEquals(jsonObject.getJSONArray("arguments").toString(),
                    "[\"table^#\",[\"class java.lang.Integer\",\"class java.lang.String\"]]");
            jsonObject.getLong("timestamp");
            throw e;
        }
    }

    @Test (expected = IllegalArgumentException.class)
    public void createTableWithNull() throws Exception {
        try {
            provider.createTable(null, null);
        } catch (IllegalArgumentException e) {
            JSONObject jsonObject = new JSONObject(writer.toString());
            Assert.assertEquals(jsonObject.get("thrown"), e.toString());
            Assert.assertEquals(jsonObject.get("name"), "createTable");
            Assert.assertEquals(jsonObject.get("class"),
                    "ru.fizteh.fivt.students.irinapodorozhnaya.storeable.MyTableProvider");
            Assert.assertEquals(jsonObject.getJSONArray("arguments").toString(), "[null,null]");
            jsonObject.getLong("timestamp");
            throw e;
        }
    }

    interface ForTest {
        Object list(List list);
    }

    class ForCyclicLink implements ForTest{
        public Object list(List list) {
          return null;
        }
    }

    @Test
    public void cyclicLink() {
        ForTest forCyclicLink = new ForCyclicLink();
        forCyclicLink = (ForTest) proxy.wrap(writer, forCyclicLink, ForTest.class);

        List cyclic = new ArrayList();
        cyclic.add(cyclic);
        forCyclicLink.list(cyclic);

        JSONObject jsonObject = new JSONObject(writer.toString());
        Assert.assertEquals(jsonObject.get("returnValue").toString(), "null");
        Assert.assertEquals(jsonObject.get("name"), "list");
        Assert.assertEquals(jsonObject.get("class"),
                "ru.fizteh.fivt.students.irinapodorozhnaya.proxy.Tests$ForCyclicLink");
        Assert.assertEquals(jsonObject.getJSONArray("arguments").toString(), "[[\"cyclic\"]]");
        jsonObject.getLong("timestamp");
    }

    @Test (expected = JSONException.class)
    public void voidReturnValue() throws Exception {
        provider.createTable("table", list);
        StringWriter writer1 = new StringWriter();
        provider = (TableProvider) proxy.wrap(writer1, provider, TableProvider.class);
        provider.removeTable("table");
        JSONObject jsonObject = new JSONObject(writer1.toString());
        jsonObject.get("returnValue");
    }
}
