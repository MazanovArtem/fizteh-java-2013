package ru.fizteh.fivt.students.lizaignatyeva.database;

import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.zip.DataFormatException;


public class MyTable implements Table {
    private boolean isValid;
    private Path globalDirectory;
    private String name;
    private HashMap<String, Storeable> data;
    private HashMap<String, Storeable> uncommitedData;
    private int currentSize;
    public final StoreableSignature columnTypes;
    private MyTableProvider tableProvider;
    private static final String CONFIG_FILE = "signature.tsv";

    private static final int base = 16;


    public MyTable(Path globalDirectory, String name, StoreableSignature columnTypes, MyTableProvider tableProvider) {
        this.isValid = true;
        this.globalDirectory = globalDirectory;
        this.name = name;
        this.currentSize = 0;
        this.columnTypes = columnTypes;
        this.tableProvider = tableProvider;
        this.data = new HashMap<>();
        this.uncommitedData = new HashMap<>();
    }

    private void checkValidness() {
        if (!isValid) {
            throw new IllegalStateException("This table has been deleted");
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getColumnsCount() {
        return columnTypes.getColumnsCount();
    }

    @Override
    public Class<?> getColumnType(int columnIndex) throws IndexOutOfBoundsException {
        return columnTypes.getColumnClass(columnIndex);
    }

    @Override
    public Storeable get(String key) {
        checkValidness();
        if (key == null) {
            throw new IllegalArgumentException("Table.get: null key provided");
        }
        if (uncommitedData.containsKey(key)) {
            return uncommitedData.get(key);
        }
        if (data.containsKey(key)) {
            return data.get(key);
        }
        return null;
    }

    @Override
    public Storeable put(String key, Storeable value) {
        checkValidness();
        if (key == null || key.equals("")) {
            throw new IllegalArgumentException("Table.put: null key provided");
        }
        if (value == null) {
            throw new IllegalArgumentException("Table.put: null value provided");
        }
        MyStoreable myStoreable;
        try {
            myStoreable = (MyStoreable) value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Table.put: inconsistent Storeable provided");
        }
        if (!myStoreable.storeableSignature.equals(columnTypes)) {
            throw new IllegalArgumentException("Table.put: inconsistent Storeable provided");
        }
        Storeable result = null;
        if (uncommitedData.containsKey(key)) {
            result = uncommitedData.get(key);
        } else if (data.containsKey(key)) {
            result = data.get(key);
        }
        if (result == null) {
            currentSize++;
        }
        uncommitedData.put(key, value);
        return result;
    }

    @Override
    public Storeable remove(String key) {
        checkValidness();
        if (key == null) {
            throw new IllegalArgumentException("Table.remove: null key provided");
        }
        Storeable result = null;
        if (uncommitedData.containsKey(key)) {
            result = uncommitedData.get(key);
            if (data.containsKey(key)) {
                uncommitedData.put(key, null);
            } else {
                uncommitedData.remove(key);
            }
        } else {
            if (data.containsKey(key)) {
                result = data.get(key);
                uncommitedData.put(key, null);
            }
        }
        if (result != null) {
            currentSize--;
        }
        return result;
    }

    @Override
    public int size() {
        checkValidness();
        return currentSize;
    }

    @Override
    public int commit() {
        checkValidness();
        int result = keysToCommit();
        for (String key : uncommitedData.keySet()) {
            Storeable value = uncommitedData.get(key);
            if (value == null) {
                if (data.containsKey(key)) {
                    data.remove(key);
                }
            } else {
                data.put(key, value);
            }
        }
        try {
            write();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write changes in table '" + name + "' to disk");
        }
        uncommitedData = new HashMap<>();
        return result;
    }

    @Override
    public int rollback() {
        checkValidness();
        int result = keysToCommit();
        uncommitedData = new HashMap<>();
        currentSize = data.size();
        return result;
    }

    private void recalcSize() {
        HashSet<String> keys = new HashSet<>();
        for (String key : data.keySet()) {
            keys.add(key);
        }
        for (String key : uncommitedData.keySet()) {
            Storeable value = uncommitedData.get(key);
            if (value == null) {
                keys.remove(key);
            } else {
                keys.add(key);
            }
        }
        currentSize = keys.size();
    }

    public static boolean exists(Path globalDirectory, String name) {
        try {
            File path = globalDirectory.resolve(name).toFile();
            return path.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    private final static HashMap<String, Class> supportedClasses = new HashMap<>();

    static {
        supportedClasses.put("int", Integer.class);
        supportedClasses.put("long", Long.class);
        supportedClasses.put("byte", Byte.class);
        supportedClasses.put("float", Float.class);
        supportedClasses.put("double", Double.class);
        supportedClasses.put("boolean", Boolean.class);
        supportedClasses.put("string", String.class);
    }

    public static List<Class<?>> convert(List<String> classNames) {
        ArrayList<Class<?>> result = new ArrayList<>();
        for (String className : classNames) {
            if (!supportedClasses.containsKey(className)) {
                throw new IllegalArgumentException("Class " + className + " is not supported");
            }
            result.add(supportedClasses.get(className));
        }
        return result;
    }

    public static List<Class<?>> convert(String[] classNames) {
        return convert(Arrays.asList(classNames));
    }

    public static MyTable read(Path globalDirectory, String name, MyTableProvider tableProvider)
                throws IOException, DataFormatException
    {
        StoreableSignature columnTypes = readStoreableSignature(globalDirectory.resolve(name));
        MyTable table = new MyTable(globalDirectory, name, columnTypes, tableProvider);
        File path = globalDirectory.resolve(name).toFile();
        File[] subDirs = path.listFiles();
        if (subDirs == null) {
            return table;
        }
        for (File dir: subDirs) {
            if (dir.getName().equals(CONFIG_FILE)) {
                continue;
            }
            if (!dir.isDirectory() || !isValidDirectoryName(dir.getName())) {
                throw new DataFormatException("Table '" + name + "' contains strange file: '" + dir.getName() + "'");
            }
            table.readDirectory(dir);
        }
        table.recalcSize();
        return table;
    }

    private static StoreableSignature readStoreableSignature(Path directory) throws IOException, DataFormatException {
        File file = directory.resolve(CONFIG_FILE).toFile();
        if (!file.exists() || !file.isFile()) {
            throw new DataFormatException(CONFIG_FILE + " does not exist or is not a file");
        }
        ArrayList<Class<?>> classes = new ArrayList<>();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNext()) {
                String className = scanner.next();
                if (!supportedClasses.containsKey(className)) {
                    throw new DataFormatException("Class " + className + " is not supported");
                } else {
                    classes.add(supportedClasses.get(className));
                }
            }
        }
        if (classes.size() == 0) {
            throw new DataFormatException("Empty " + CONFIG_FILE + " found");
        }
        return new StoreableSignature(classes);
    }

    private void readDirectory(File dir) throws DataFormatException, IOException {
        File[] filesInDirectory = dir.listFiles();
        if (filesInDirectory == null) {
            return;
        }
        for (File file : filesInDirectory) {
            if (!file.isFile() || !isValidFileName(file.getName())) {
                throw new DataFormatException("Table '" + name + "' contains strange file: '" + file.getName() + "'");
            }
            readFile(file.getCanonicalPath(), dir.getName(), file.getName());
        }
    }

    public void readFile(String filePath, String dirName, String fileName) throws DataFormatException, IOException {
        byte[] data = Files.readAllBytes(Paths.get(filePath));
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            try {
                readEntry(buffer, dirName, fileName);
            } catch (BufferUnderflowException e) {
                throw new DataFormatException("Table '" + name + "' contains corrupted file " + filePath);
            }
        }
    }

    private void readEntry(ByteBuffer buffer, String dirName, String fileName)
            throws BufferUnderflowException,
                   DataFormatException
    {
        int keyLength = buffer.getInt();
        if (keyLength > buffer.remaining() || keyLength < 0) {
            throw new DataFormatException("too long key buffer");
        }
        int valueLength = buffer.getInt();
        if (valueLength > buffer.remaining() || valueLength < 0) {
            throw new DataFormatException("too long value buffer");
        }
        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);
        byte[] valueBytes = new byte[valueLength];
        buffer.get(valueBytes);
        String key = new String(keyBytes, StandardCharsets.UTF_8);
        if (!isValidKey(key, dirName, fileName)) {
            throw new DataFormatException("entry in a wrong file, key: " + key + ", file: "
                    + fileName + ", expected file: " + getFileName(key) + ", directory: " + dirName
                    + ", expected directory: " + getDirName(key));
        }
        String value = new String(valueBytes, StandardCharsets.UTF_8);
        if (data.containsKey(key)) {
            throw new DataFormatException("duplicating keys: " + key);
        }
        Storeable storeable;
        try {
            storeable = tableProvider.deserialize(this, value);
        } catch (ParseException e) {
            throw new DataFormatException("Incorrect data: failed to deserialize json");
        }
        data.put(key, storeable);
    }

    private static boolean isValidKey(String key, String dirName, String fileName) {
        return getDirName(key).equals(dirName) && getFileName(key).equals(fileName);
    }

    private static boolean isValidDirectoryName(String name) {
        for (int i = 0; i < base; ++i) {
            if (name.equals(Integer.toString(i) + ".dir")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidFileName(String name) {
        for (int i = 0; i < base; ++i) {
            if (name.equals(Integer.toString(i) + ".dat")) {
                return true;
            }
        }
        return false;
    }

    public void write() throws IOException {
        checkValidness();
        File path = globalDirectory.resolve(name).toFile();
        try {
            FileUtils.remove(path);
        } catch (Exception e) {
            //System.err.println("Error while updating database files: " + e.getMessage());
            //System.exit(1);
        }
        FileUtils.mkDir(path.getAbsolutePath());
        for (String key: data.keySet()) {
            String value = tableProvider.serialize(this, data.get(key));
            File directory = FileUtils.mkDir(path.getAbsolutePath()
                    + File.separator + getDirName(key));
            File file = FileUtils.mkFile(directory, getFileName(key));
            try (BufferedOutputStream outputStream = new BufferedOutputStream(
                    new FileOutputStream(file.getCanonicalPath(), true))) {
                writeEntry(key, value, outputStream);
            }
        }
    }

    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private void writeEntry(String key, String value, BufferedOutputStream outputStream) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        outputStream.write(intToBytes(keyBytes.length));
        outputStream.write(intToBytes(valueBytes.length));
        outputStream.write(keyBytes);
        outputStream.write(valueBytes);
    }

    public int keysToCommit() {
        int result = 0;
        for (String key: uncommitedData.keySet()) {
            if (!data.containsKey(key)) {
                result ++;
            } else {
                Storeable oldValue = data.get(key);
                Storeable newValue = uncommitedData.get(key);
                if (!oldValue.equals(newValue)) {
                    result++;
                }
            }
        }
        return result;
    }

    private static int getDirNumber(String key) {
        int number = key.getBytes()[0];
        number = Math.abs(number);
        return number % base;
    }

    private static int getFileNumber(String key) {
        int number = key.getBytes()[0];
        number = Math.abs(number);
        return number / base % base;
    }

    private static String getDirName(String key) {
        return String.format("%d.dir", getDirNumber(key));
    }

    private static String getFileName(String key) {
        return String.format("%d.dat", getFileNumber(key));
    }

    public void markAsDeleted() {
        isValid = false;
    }
}
