/*
 * Copyright 2023 sberg it-systeme GmbH
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package net.sberg.openkim.common;

import javax.mail.Message;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public class FileUtils {

    public static byte[] getFileChecksum(MessageDigest digest, File file) throws Exception {
        //Get file input stream for reading the file content
        FileInputStream fis = new FileInputStream(file);

        //Create byte array to read data in chunks
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        //Read file data and update in message digest
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }

        //close the stream; We don't need it now.
        fis.close();

        //Get the hash's bytes
        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString().getBytes();
    }

    public static final File writeToFile(String str, String fileName) throws Exception {
        File file = new File(fileName);
        BufferedWriter output = new BufferedWriter(new FileWriter(file));
        output.write(str);
        output.flush();
        output.close();
        return file;
    }

    public static final String readFileContent(String fileName) throws Exception {
        return readFileContent(new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8)));
    }

    private static final String readFileContent(BufferedReader reader) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        String ls = System.getProperty("line.separator");
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(ls);
            }
            stringBuilder.append(line);
        }
        reader.close();
        return stringBuilder.toString();
    }

    public static final Set<String> getFiles(String dir) throws IOException {
        File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }

        Set<String> fileList = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir))) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    fileList.add(path.getFileName()
                        .toString());
                }
            }
        }
        return fileList;
    }

    public static final File writeToFileDirectory(Message msg, String prefix, String messageId, String storageFolder) throws Exception {
        File f = new File(storageFolder);
        if (!f.exists()) {
            f.mkdirs();
        }
        String whereToSave = f.getAbsolutePath() + File.separator + prefix + sanitizeMailFilename(messageId) + ".eml";
        f = new File(whereToSave);
        f.delete();
        OutputStream out = new FileOutputStream(new File(whereToSave));
        msg.writeTo(out);
        out.flush();
        out.close();
        return f;
    }

    public static final File writeToFileDirectory(byte[] bytes, String prefix, String storageFolder) throws Exception {
        File f = new File(storageFolder);
        if (!f.exists()) {
            f.mkdirs();
        }
        String whereToSave = f.getAbsolutePath() + File.separator + prefix + System.nanoTime() + ".eml";
        f = new File(whereToSave);
        f.delete();
        OutputStream out = new FileOutputStream(new File(whereToSave));
        out.write(bytes);
        out.flush();
        out.close();
        return f;
    }

    public static final String sanitizeMailFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<> \"]", "_");
    }
}
