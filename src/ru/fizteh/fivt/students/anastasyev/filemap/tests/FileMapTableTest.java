package ru.fizteh.fivt.students.anastasyev.filemap.tests;

import org.junit.*;
import ru.fizteh.fivt.storage.strings.*;

import ru.fizteh.fivt.students.anastasyev.filemap.FileMapTableProviderFactory;

import static junit.framework.Assert.*;

public class FileMapTableTest {
    static TableProviderFactory factory;
    static TableProvider tableProvider;
    Table currTable;
    String currTableName;

    @BeforeClass
    public static void setUp() {
        //System.getProperties().setProperty("fizteh.db.dir", "C:\\Users\\qBic\\Documents
        // \\GitHub\\fizteh-java-2013\\src\\ru\\fizteh\\fivt\\students\\anastasyev\\filemap\\db.dir");
        factory = new FileMapTableProviderFactory();
        tableProvider = factory.create(System.getProperty("fizteh.db.dir"));
    }

    @Before
    public void setCurrTable() {
        currTable = tableProvider.createTable("TestTable");
        currTableName = "TestTable";
    }

    @After
    public void delete() {
        tableProvider.removeTable(currTableName);
    }

    @Test
    public void testGetName() throws Exception {
        assertEquals(currTableName, currTable.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutNullKey() {
        currTable.put(null, "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutNullValue() {
        currTable.put("key", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutEmptyKey() {
        currTable.put("    ", "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutEmptyValue() {
        currTable.put("key", "  ");
    }

    @Test
    public void testPutNewKey() {
        assertNull(currTable.put("new", "value"));
    }

    @Test
    public void testPutOldKey() {
        assertNull(currTable.put("key", "valueOld"));
        assertEquals(currTable.put("key", "valueNew"), "valueOld");
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeNullKey() {
        currTable.remove(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveEmptyKey() {
        currTable.remove(" ");
    }

    @Test
    public void testRemoveNotExistsKey() {
        assertNull(currTable.put("key", "value"));
        assertNotNull(currTable.remove("key"));
        assertNull(currTable.remove("key"));
    }

    @Test
    public void testRemoveExistsKey() {
        currTable.put("key", "value");
        assertEquals(currTable.remove("key"), "value");
    }

    @Test
    public void testGetNotExistsKey() throws Exception {
        assertNull(currTable.get("notExistsKey"));
    }

    @Test
    public void testGetExistsKey() throws Exception {
        assertNull(currTable.put("newKey", "value"));
        assertNotNull(currTable.get("newKey"));
        assertNotNull(currTable.remove("newKey"));
    }

    @Test
    public void testCommit() throws Exception {
        int sizeBefore = currTable.size();
        assertNull(currTable.put("key1", "value"));
        assertEquals(currTable.put("key1", "value"), "value");
        assertNull(currTable.put("key2", "value"));
        assertEquals(currTable.put("key2", "value"), "value");
        assertNull(currTable.put("key3", "value"));
        assertEquals(currTable.put("key3", "value"), "value");
        assertEquals(currTable.remove("key1"), "value");
        assertEquals(currTable.put("key2", "value1"), "value");
        assertEquals(currTable.commit(), 2);
        int sizeAfter = currTable.size();
        assertEquals(2, sizeAfter - sizeBefore);
    }

    @Test
    public void testRollback() throws Exception {
        assertNull(currTable.put("key1", "value"));
        assertEquals(currTable.put("key1", "value"), "value");
        assertNull(currTable.put("key2", "value"));
        assertEquals(currTable.put("key2", "value"), "value");
        assertNull(currTable.put("key3", "value"));
        assertEquals(currTable.put("key3", "value"), "value");
        assertEquals(currTable.commit(), 3);
        assertEquals(currTable.remove("key1"), "value");
        assertNull(currTable.put("key4", "value"));
        assertEquals(currTable.put("key2", "value1"), "value");
        assertEquals(currTable.remove("key4"), "value");
        assertNull(currTable.get("key1"));
        assertEquals(currTable.rollback(), 2);
        assertNotNull(currTable.get("key1"));
    }

    @Test
    public void testSize() throws Exception {
        assertNull(currTable.put("key1", "value"));
        assertEquals(currTable.put("key1", "value"), "value");
        assertNull(currTable.put("key2", "value"));
        assertEquals(currTable.put("key2", "value"), "value");
        assertNull(currTable.put("key3", "value"));
        assertEquals(currTable.put("key3", "value"), "value");
        assertEquals(currTable.commit(), 3);
        assertEquals(currTable.remove("key1"), "value");
        assertNull(currTable.put("key4", "value"));
        assertEquals(currTable.put("key2", "value"), "value");
        assertEquals(currTable.remove("key4"), "value");
        assertEquals(currTable.size(), 2);
    }

    @Test
    public void testCommitRollback() {
        Assert.assertNull(currTable.put("commit", "rollback"));
        Assert.assertEquals(currTable.get("commit"), "rollback");
        Assert.assertEquals(currTable.rollback(), 1);
        Assert.assertNull(currTable.get("commit"));
        Assert.assertNull(currTable.put("commit", "rollback"));
        Assert.assertEquals(currTable.get("commit"), "rollback");
        Assert.assertEquals(currTable.commit(), 1);
        Assert.assertEquals(currTable.remove("commit"), "rollback");
        Assert.assertNull(currTable.put("commit", "rollback1"));
        Assert.assertEquals(currTable.commit(), 1);
        Assert.assertEquals(currTable.get("commit"), "rollback1");
    }
}
