    package ru.fizteh.fivt.students.mikhaylova_daria.db;

    import java.io.File;
    import java.io.FileNotFoundException;
    import java.io.RandomAccessFile;
    import java.util.HashMap;

    public class DbMain {
        private static String workingDirectoryName;

        public static void main(String[] arg) {
            workingDirectoryName = System.getProperty("fizteh.db.dir");
            try {
                Parser.parser(arg, FileMap.class);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }

        static void writerDateBase() throws Exception {
            File workingDirectory = new File(workingDirectoryName);
            RandomAccessFile dateBase = null;
            try {
                dateBase = new RandomAccessFile(workingDirectory.toPath().resolve("db.dat").toFile(), "rw");
            } catch (Exception e) {
                throw new Exception("Creating" + workingDirectory.toPath().resolve("db.dat").toFile()
                        + "is not possible");
            }
            HashMap<String, Long> offsets = new HashMap<String, Long>();
            long lastOffset;
            for (String key: FileMap.fileMap.keySet()) {
                dateBase.write(key.getBytes());
                dateBase.writeInt(0);
                long offset = dateBase.getFilePointer();
                dateBase.seek(dateBase.getFilePointer() + 1);
                lastOffset = offset;
                offsets.put(key, offset);
            }

            for (String key: FileMap.fileMap.keySet()) {
                dateBase.write(FileMap.fileMap.get(key).getBytes());
                lastOffset = dateBase.getFilePointer();
                dateBase.seek(offsets.get(key));
                Integer lastOffsetInt = new Long(lastOffset).intValue();
                dateBase.write(lastOffsetInt.byteValue());
                dateBase.seek(lastOffset);
            }
            dateBase.close();
        }

//        static void readerDateBase() throws Exception {
//            File workingDirectory = new File(workingDirectoryName);
//            RandomAccessFile dateBase = null;
//            try {
//                dateBase = new RandomAccessFile(workingDirectory.toPath().resolve("db.dat").toFile(), "r");
//            } catch (FileNotFoundException e) {
//                throw new Exception("File is not found " + e.getMessage());
//            } catch (Exception e) {
//                throw new Exception("Opening isn't possible");
//            }
//            String key;
//            String value;
//            key = dateBase.readUTF();
//            long offset;
//            final long firstOffset;
//            int offsetOfValue;
//            offsetOfValue = dateBase.readInt();
//            offset = dateBase.getFilePointer();
//            firstOffset = offsetOfValue;
//            dateBase.seek(offsetOfValue);
//            value = dateBase.readUTF();
//            FileMap.fileMap.put(key, value);
//            while (firstOffset > offset) {
//                key = dateBase.readUTF();
//                dateBase.readChar();
//                offsetOfValue = dateBase.readInt();
//                offset = dateBase.getFilePointer();
//                dateBase.seek(offsetOfValue);
//                value = dateBase.readUTF();
//                FileMap.fileMap.put(key, value);
//            }
//            dateBase.close();
//        }
     }



