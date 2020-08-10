import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {

    private static final Path VIRTUAL_DRIVE = Path.of("T:");
    private static Path TEST_TEMP_FOLDER;

    static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory(TEST_TEMP_FOLDER, "test");
    }

    static void deleteTestFolder() throws IOException {
        Path directory = TEST_TEMP_FOLDER;
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static String readFromStream(InputStream stream) throws IOException {
        BufferedReader output = new BufferedReader(new
                InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String s;
        while ((s = output.readLine()) != null) {
            builder.append(s);
        }
        return builder.toString();
    }

    static void runCmd(String cmd, boolean throwWhenUnsuccessful) throws IOException {
        Process process = Runtime.getRuntime().exec(cmd);
        boolean successful;

        try {
            successful = process.waitFor() == 0;
        } catch (InterruptedException e) {
            successful = false;
        }

        if (!successful && throwWhenUnsuccessful) {
            String output = readFromStream(process.getInputStream()) + readFromStream(process.getErrorStream());
            throw new RuntimeException(String.format("Command failed to run, output:\n %s", output));
        }
    }

    static void subst(Path drive, Path path) throws IOException {
        runCmd(String.format("cmd /c subst %s %s", drive.toString(), path.toString()), true);
    }

    static void substDelete(Path drive) throws IOException {
        runCmd(String.format("cmd /c subst %s /d", drive.toString()), false);
    }


    static void testCreateAndDeleteFile() throws IOException {
        Path tempDirectory = createTempDirectory();
        subst(VIRTUAL_DRIVE, tempDirectory);


        String fileContents = "Hello world!";
        Path p = Path.of(VIRTUAL_DRIVE.toString(), "testFile.txt");
        Files.createFile(p);

        assert Files.exists(p);

        Files.writeString(p, fileContents);
        assert Files.readString(p).equals(fileContents);


        substDelete(VIRTUAL_DRIVE);
    }

    static void testDeleteSubstitutedDrive() throws IOException {
        Path tempDirectory = createTempDirectory();
        subst(VIRTUAL_DRIVE, tempDirectory);


        assert Files.exists(tempDirectory);
        Files.delete(VIRTUAL_DRIVE);
        assert Files.notExists(tempDirectory);


        substDelete(VIRTUAL_DRIVE);
    }


    static void testIsWritable() throws IOException {
        Path tempDirectory = createTempDirectory();
        Path virtualDrive = VIRTUAL_DRIVE;
        subst(VIRTUAL_DRIVE, tempDirectory);

        assert Files.isSameFile(tempDirectory, virtualDrive);

        assert Files.isExecutable(tempDirectory) == Files.isExecutable(virtualDrive);
        assert Files.isReadable(tempDirectory) == Files.isReadable(virtualDrive);
        assert Files.isDirectory(tempDirectory) == Files.isDirectory(virtualDrive);
        assert Files.isHidden(tempDirectory) == Files.isHidden(virtualDrive);
        assert Files.isRegularFile(tempDirectory) == Files.isRegularFile(virtualDrive);
        assert Files.isSymbolicLink(tempDirectory) == Files.isSymbolicLink(virtualDrive);
        assert Files.getOwner(tempDirectory).equals(Files.getOwner(virtualDrive));
        assert Files.isWritable(tempDirectory) == Files.isWritable(virtualDrive);


        substDelete(VIRTUAL_DRIVE);
    }


    static void testGetSetAttributes() throws IOException {
        Path tempDirectory = createTempDirectory();
        subst(VIRTUAL_DRIVE, tempDirectory);

        Files.setAttribute(VIRTUAL_DRIVE, "dos:hidden", true);
        assert Files.isHidden(VIRTUAL_DRIVE);
        assert Files.isHidden(tempDirectory);

        Files.setAttribute(tempDirectory, "dos:hidden", false);
        assert !Files.isHidden(VIRTUAL_DRIVE);
        assert !Files.isHidden(tempDirectory);

        var attr1 = Files.readAttributes(VIRTUAL_DRIVE, "*");
        var attr2 = Files.readAttributes(tempDirectory, "*");
        assert attr1.equals(attr2);


        substDelete(VIRTUAL_DRIVE);
    }

    static void testFileStore() throws IOException {
        Path tempDirectory = createTempDirectory();
        subst(VIRTUAL_DRIVE, tempDirectory);


        var fileStore1 = Files.getFileStore(tempDirectory);
        var fileStore2 = Files.getFileStore(VIRTUAL_DRIVE);

        assert fileStore1.getTotalSpace() == fileStore2.getTotalSpace();
        assert fileStore1.getBlockSize() == fileStore2.getBlockSize();
        assert fileStore1.getUnallocatedSpace() == fileStore2.getUnallocatedSpace();
        assert fileStore1.getUsableSpace() == fileStore2.getUsableSpace();
        assert fileStore1.name().equals(fileStore2.name());
        assert fileStore1.type().equals(fileStore2.type());

        assert VIRTUAL_DRIVE.getFileSystem().getRootDirectories().equals(tempDirectory.getFileSystem().getRootDirectories());


        substDelete(VIRTUAL_DRIVE);
    }

    static void testSymlinkFile() throws IOException {
        String contents = "Hello world!";
        Path tempDirectory = createTempDirectory();
        subst(VIRTUAL_DRIVE, tempDirectory);
        Path tempFile = Path.of(VIRTUAL_DRIVE.toString(), "test.txt");

        Files.writeString(tempFile, contents);
        assert Files.readString(tempFile).equals(contents);

        Path link = Path.of(VIRTUAL_DRIVE.toString(), "link");
        Files.createSymbolicLink(link, tempFile);

        assert Files.readString(link).equals(contents);

        assert Files.isExecutable(link) == Files.isExecutable(tempFile);
        assert Files.isReadable(link) == Files.isReadable(tempFile);
        assert Files.isDirectory(link) == Files.isDirectory(tempFile);
        assert Files.isHidden(link) == Files.isHidden(tempFile);
        assert Files.isRegularFile(link) == Files.isRegularFile(tempFile);
        assert Files.isWritable(link) == Files.isWritable(tempFile);
        assert Files.getOwner(link).equals(Files.getOwner(tempFile));


        substDelete(VIRTUAL_DRIVE);
    }

    static void testSubstAndSymlink() throws IOException {
        Path tempDirectory = createTempDirectory();
        Path tempLink = Path.of(tempDirectory.toString() + "_link");
        Files.createSymbolicLink(tempLink, tempDirectory);

        subst(VIRTUAL_DRIVE, tempLink);

        assert Files.readAttributes(VIRTUAL_DRIVE, "*").equals(Files.readAttributes(tempDirectory, "*"));
        assert Files.isWritable(VIRTUAL_DRIVE);

        Path tempFile = Files.createTempFile(VIRTUAL_DRIVE, "prefix", "suffix");
        String contents = "Hello world!";
        Files.writeString(tempFile, contents);
        assert Files.readString(tempFile).equals(contents);

        Path tempDirectory2 = createTempDirectory();
        Path copy = Path.of(tempDirectory2.toString(), "copied");
        Files.copy(tempFile, copy);

        assert Files.exists(copy);
        assert Files.readString(copy).equals(contents);

        Path cut = Path.of(tempDirectory2.toString(), "cut");
        Files.move(tempFile, cut);
        assert Files.notExists(tempFile);
        assert Files.exists(cut);
        assert Files.readString(cut).equals(contents);


        substDelete(VIRTUAL_DRIVE);
    }


    // Subst on a symlinked folder
    static void testMoveAndCopyFiles() throws IOException {
        Path tempDirectory = createTempDirectory();
        Path tempLink = Path.of(tempDirectory.toString() + "_link");
        Files.createSymbolicLink(tempLink, tempDirectory);

        subst(VIRTUAL_DRIVE, tempLink);

        assert Files.readAttributes(VIRTUAL_DRIVE, "*").equals(Files.readAttributes(tempDirectory, "*"));
        assert Files.isWritable(VIRTUAL_DRIVE);


        substDelete(VIRTUAL_DRIVE);
    }


    static void testMoveAndCopySubstDrive() throws IOException {
        Path tempDirectory = createTempDirectory();
        Path tempDirectoryCopy = Path.of(tempDirectory.toString() + "_copy");

        subst(VIRTUAL_DRIVE, tempDirectory);

        Files.copy(VIRTUAL_DRIVE, tempDirectoryCopy);

        assert Files.isExecutable(VIRTUAL_DRIVE) == Files.isExecutable(tempDirectoryCopy);
        assert Files.isReadable(VIRTUAL_DRIVE) == Files.isReadable(tempDirectoryCopy);
        assert Files.isDirectory(VIRTUAL_DRIVE) == Files.isDirectory(tempDirectoryCopy);
        assert Files.isHidden(VIRTUAL_DRIVE) == Files.isHidden(tempDirectoryCopy);
        assert Files.isRegularFile(VIRTUAL_DRIVE) == Files.isRegularFile(tempDirectoryCopy);
        assert Files.isWritable(VIRTUAL_DRIVE) == Files.isWritable(tempDirectoryCopy);
        assert Files.getOwner(VIRTUAL_DRIVE).equals(Files.getOwner(tempDirectoryCopy));


        substDelete(VIRTUAL_DRIVE);
    }

    public static void main(String[] args) throws IOException {

        TEST_TEMP_FOLDER = Files.createTempDirectory("virtual-drive-test");
        System.out.printf("Test folder is at %s\n", TEST_TEMP_FOLDER);

        try {
            testCreateAndDeleteFile();
            testDeleteSubstitutedDrive();
            testIsWritable();
            testFileStore();
            testGetSetAttributes();
            testSymlinkFile();
            testSubstAndSymlink();
            testMoveAndCopyFiles();
            testMoveAndCopySubstDrive();
            System.out.println("Tests succeeded");
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            substDelete(VIRTUAL_DRIVE);
            deleteTestFolder();
        }
    }
}
