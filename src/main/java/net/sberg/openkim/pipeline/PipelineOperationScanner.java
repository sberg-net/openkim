package net.sberg.openkim.pipeline;

import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class PipelineOperationScanner {

    private final List<String> annotatedClasses = new ArrayList<>();

    private List<URL> getRootUrls() throws Exception {
        List<URL> result = new ArrayList<>();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            if (cl instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) cl).getURLs();
                result.addAll(Arrays.asList(urls));
            }
            cl = cl.getParent();
        }

        return result;
    }

    private void visitFile(File f, List<String> scanPackages, DaoDescriptorClassScanner daoDescriptorClassVisitor) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            scanPackages = scanPackages.stream().map(scanPackage -> scanPackage.replaceAll("/", Matcher.quoteReplacement(File.separator))).collect(Collectors.toList());
        }

        File finalFile = f;
        if (finalFile.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    visitFile(child, scanPackages, daoDescriptorClassVisitor);
                }
            }
        }
        else if (!scanPackages.stream().filter(scanPackage -> finalFile.getAbsolutePath().contains(scanPackage)).collect(Collectors.toList()).isEmpty()) {
            try (FileInputStream in = new FileInputStream(f)) {
                daoDescriptorClassVisitor.reset();
                new ClassReader(in).accept(daoDescriptorClassVisitor, 0);
                if (daoDescriptorClassVisitor.hasAnnotation && !annotatedClasses.contains(daoDescriptorClassVisitor.className)) {
                    annotatedClasses.add(daoDescriptorClassVisitor.className);
                }
            }
        }
    }

    private void visitJar(URL url, List<String> scanPackages, DaoDescriptorClassScanner daoDescriptorClassVisitor) throws IOException {
        try (InputStream urlIn = url.openStream();
             JarInputStream jarIn = new JarInputStream(urlIn)) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                final JarEntry finalEntry = entry;
                List<String> filteredScanPackages = scanPackages.stream().filter(scanPackage -> finalEntry.toString().contains(scanPackage)).collect(Collectors.toList());
                if (!filteredScanPackages.isEmpty() && entry.toString().endsWith(".class")) {
                    daoDescriptorClassVisitor.reset();
                    new ClassReader(jarIn).accept(daoDescriptorClassVisitor, 0);
                    if (daoDescriptorClassVisitor.hasAnnotation && !annotatedClasses.contains(daoDescriptorClassVisitor.className)) {
                        annotatedClasses.add(daoDescriptorClassVisitor.className);
                    }
                }
            }
        }
    }

    public Map<String, IPipelineOperation> createOperationMap(List<String> scanPackages) throws Exception {

        DaoDescriptorClassScanner daoDescriptorClassVisitor = new DaoDescriptorClassScanner();

        for (URL url : getRootUrls()) {
            File f = new File(url.getPath());
            if (f.isDirectory()) {
                visitFile(f, scanPackages, daoDescriptorClassVisitor);
            } else {
                visitJar(url, scanPackages, daoDescriptorClassVisitor);
            }
        }

        Map<String, IPipelineOperation> result = new HashMap<>();

        for (String annotatedClass : annotatedClasses) {
            Class<?> aClass = Class.forName(annotatedClass);
            IPipelineOperation pipelineOperation = (IPipelineOperation) aClass.getDeclaredConstructor().newInstance();
            result.put(pipelineOperation.getOperationKey(), pipelineOperation);
        }

        return result;
    }

    private class DaoDescriptorClassScanner extends ClassVisitor {
        private boolean hasAnnotation;
        private String className;

        DaoDescriptorClassScanner() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version,
                          int access, String name, String signature,
                          String superName, String[] interfaces) {
            className = name.replaceAll("/", ".");
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.contains("pipeline/PipelineOperation;")) {
                hasAnnotation = true;
            }
            return null;
        }

        private void reset() {
            hasAnnotation = false;
            className = null;
        }
    }

}
