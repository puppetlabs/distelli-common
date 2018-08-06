/*
  $Id: $
  @file DiskObjectStore.java
  @brief Contains the DiskObjectStore.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.objectStore.impl.disk;

import java.io.OutputStream;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import java.nio.file.SimpleFileVisitor;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.AbstractObjectStore;
import com.distelli.objectStore.impl.ObjectStoreBuilder;
import com.distelli.persistence.PageIterator;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DiskObjectStore extends AbstractObjectStore
{
    private File _root = null;
    private File _bucketsRoot = null;
    private File _partsRoot = null;

    private static final String KEY_POSTFIX = ".obj";

    public interface Factory {
        public DiskObjectStore create(ObjectStoreBuilder builder);
    }

    public DiskObjectStore(File rootDir) {
        if(rootDir == null)
            throw(new IllegalArgumentException("Invalid Disk Storage Root: "+rootDir));
        //if it exists ensure that its a directory
        if(rootDir.exists())
        {
            //check to make sure that the specified root is a dir
            if(!rootDir.isDirectory())
                throw(new IllegalArgumentException("Invalid Disk Storage Root: "+rootDir+" is not a directory"));
        }
        else
        {
            //create the dir
            rootDir.mkdirs();
        }

        //create the buckets subdir
        File bucketsRoot = new File(rootDir.getAbsolutePath(), "buckets");
        if(!bucketsRoot.exists())
            bucketsRoot.mkdirs();
        File partsRoot = new File(rootDir.getAbsolutePath(), "parts");
        if(!partsRoot.exists())
            partsRoot.mkdirs();

        _bucketsRoot = bucketsRoot;
        _partsRoot = partsRoot;
        _root = rootDir;
    }

    @Inject
    public DiskObjectStore(@Assisted ObjectStoreBuilder builder)
    {
        this(builder.getDiskStorageRoot());
        ObjectStoreType type = builder.getObjectStoreProvider();
        if(type == null || type != ObjectStoreType.DISK)
            throw(new IllegalArgumentException("Invalid ObjectStoreType: "+type));
    }

    @Override
    public void createBucket(String bucketName) {
        if ( ! validFileName(bucketName) ) {
            throw new IllegalArgumentException("BucketName is invalid "+bucketName);
        }
        File bucketDir = new File(_bucketsRoot.getAbsolutePath(), bucketName);
        if(bucketDir.exists())
            return;
        bucketDir.mkdirs();
    }

    @Override
    public void deleteBucket(String bucketName) throws AccessControlException {
        if ( ! validFileName(bucketName) ) {
            throw new IllegalArgumentException("BucketName is invalid "+bucketName);
        }
        File bucketDir = new File(_bucketsRoot.getAbsolutePath(), bucketName);
        if(!bucketDir.exists())
            return;
        try {
            try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(bucketDir.toPath())) {
                if(dirStream.iterator().hasNext())
                    throw(new IllegalStateException("Bucket "+bucketName+" is not empty"));
            }

            Files.delete(bucketDir.toPath());
        } catch(Throwable t) {
            throw(new AccessControlException(t.getMessage()));
        }
    }

    @Override
    public void put(ObjectKey objectKey, long contentLength, InputStream in) {
        validate(objectKey);
        File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
        if(!bucketRoot.exists())
            throw(new EntityNotFoundException("Bucket "+objectKey.getBucket()+" does not exist"));
        File objFile = new File(bucketRoot, toKeyId(objectKey.getKey()));
        File parentDir = objFile.getParentFile();
        if(parentDir != null && !parentDir.exists())
            parentDir.mkdirs();

        try {
            FileOutputStream out = new FileOutputStream(objFile);
            byte[] buf = new byte[1024*1024];
            int bytesRead = 0;
            while((bytesRead = in.read(buf)) != -1)
                out.write(buf, 0, bytesRead);
            out.close();
            in.close();
        } catch(Throwable t) {
            throw(new RuntimeException(t));
        }
    }

    // Returns null if entity does not exist.
    @Override
    public ObjectMetadata head(ObjectKey objectKey) {
        validate(objectKey);
        File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
        if(!bucketRoot.exists())
            return null;
        File objFile = new File(bucketRoot, toKeyId(objectKey.getKey()));
        if(!objFile.exists())
            return null;
        long len = objFile.length();
        return ObjectMetadata
        .builder()
        .bucket(objectKey.getBucket())
        .key(objectKey.getKey())
        .contentLength(len)
        .build();
    }

    @Override
    public <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader, Long start, Long end)
        throws EntityNotFoundException, IOException
    {
        validate(objectKey);
        File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
        if(!bucketRoot.exists())
            throw(new EntityNotFoundException("NotFound: "+objectKey+" bucketsRoot="+_bucketsRoot));
        File objFile = new File(bucketRoot, toKeyId(objectKey.getKey()));
        if(!objFile.exists())
            throw(new EntityNotFoundException("NotFound: "+objectKey+" bucketsRoot="+_bucketsRoot));
        FileInputStream fileIn = new FileInputStream(objFile);
        if(start != null)
            fileIn.skip(start.longValue());
        InputStream in = null;
        if(end != null)
        {
            if(start != null && end < start)
                throw(new IllegalArgumentException("end ["+end+"] cannot be less than start ["+start+"]"));
            in = new LimitingInputStream(fileIn, end-start);
        }
        if(in == null)
            in = fileIn;
        ObjectMetadata objectMetadata = ObjectMetadata
        .builder()
        .bucket(objectKey.getBucket())
        .key(objectKey.getKey())
        .contentLength(objFile.length())
        .build();

        return objectReader.read(objectMetadata, in);
    }

    @Override
    public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator)
    {
        validate(objectKey);
        final List<ObjectKey> keys = new ArrayList<ObjectKey>();
        File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
        if(!bucketRoot.exists()) {
            throw new EntityNotFoundException("NotFound: "+objectKey+" bucketsRoot="+_bucketsRoot);
        }

        final File objFile = new File(bucketRoot, objectKey.getKey());

        String beginsWith;
        File parentDir;
        File afterFile = null;
        if ( objectKey.getKey().endsWith("/") || "".equals(objectKey.getKey()) ) {
            parentDir = objFile;
            beginsWith = null;
            if ( null != iterator.getMarker() ) {
                afterFile = new File(bucketRoot, iterator.getMarker());
            }
        } else {
            parentDir = objFile.getParentFile();
            beginsWith = objFile.getName();
            if ( null != iterator.getMarker() ) {
                afterFile = new File(new File(bucketRoot, beginsWith), iterator.getMarker());
            }
        }
        if(parentDir == null || !parentDir.exists()) {
            iterator.setMarker(null);
            return Collections.emptyList();
        }
        if ( null != afterFile ) {
            afterFile = bucketRoot.toPath().relativize(afterFile.toPath()).toFile();
        }

        int pageSize = iterator.getPageSize();
        AtomicInteger remaining = new AtomicInteger(pageSize);
        if ( walk(parentDir, beginsWith, 0, afterFile, (file) -> {
                    ObjectKey elm = toObjectkey(file);
                    if ( remaining.getAndDecrement() <= 0 ) {
                        iterator.setMarker(elm.getKey());
                        return false;
                    }
                    keys.add(elm);
                    return true;
                }) )
        {
            iterator.setMarker(null);
        }
        return Collections.unmodifiableList(keys);
    }

    @Override
    public void delete(ObjectKey objectKey)
        throws EntityNotFoundException
    {
        validate(objectKey);
        try {
            File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
            if(!bucketRoot.exists())
                throw(new EntityNotFoundException("NotFound: "+objectKey+" bucketsRoot="+_bucketsRoot));
            File objFile = new File(bucketRoot, toKeyId(objectKey.getKey()));
            // May have already been deleted concurrently, so we ignore this which
            // is consistent with S3 behavior.
            if(!objFile.exists()) return;
            Files.delete(objFile.toPath());
        } catch(IOException ioe) {
            throw(new RuntimeException(ioe));
        }
    }

    @Override
    public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit)
        throws EntityNotFoundException
    {
        validate(objectKey);
        // S3 never checks if these paths exist, it simply returns a string with
        // credentials:
        File bucketRoot = new File(_bucketsRoot, objectKey.getBucket());
        File objFile = new File(bucketRoot, toKeyId(objectKey.getKey()));
        return URI.create("file://"+objFile.getAbsoluteFile().getAbsolutePath());
    }

    @Override
    public ObjectPartKey newMultipartPut(ObjectKey objectKey) {
        try {
            return newMultipartPutThrows(objectKey);
        } catch ( RuntimeException ex) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    private ObjectPartKey newMultipartPutThrows(ObjectKey objectKey) throws IOException {
        validate(objectKey);
        Path uploadDir = null;
        try {
            uploadDir = Files.createTempDirectory(_partsRoot.toPath(), null);
            Files.write(
                Paths.get(uploadDir.toString(), ".KEY"),
                (objectKey.getBucket()+"/"+objectKey.getKey()).getBytes(UTF_8));
        } catch ( RuntimeException ex) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
        return ObjectPartKey.builder()
            .bucket(objectKey.getBucket())
            .key(objectKey.getKey())
            .uploadId(uploadDir.getFileName().toString())
            .build();
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in) {
        try {
            return multipartPutThrows(partKey, partNum, contentLength, in);
        } catch ( RuntimeException ex) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    private ObjectPartId multipartPutThrows(ObjectPartKey partKey, int partNum, long contentLength, InputStream in) throws IOException {
        if ( partNum < 1 || partNum > 10000 ) {
            throw new IllegalArgumentException("partNum must be between 1-10000 got="+partNum);
        }
        Path uploadDir = getUploadDir(partKey);
        Path uploadFile = Files.createTempFile(uploadDir, null, String.format(".part%04d", partNum));
        boolean success = false;
        try {
            Files.copy(in, uploadFile, StandardCopyOption.REPLACE_EXISTING);
            success = true;
        } finally {
            if ( ! success ) Files.deleteIfExists(uploadFile);
        }
        String partId = uploadFile.getFileName().toString();
        partId = partId.substring(0, partId.lastIndexOf('.'));
        return ObjectPartId.builder()
            .partNum(partNum)
            .partId(partId)
            .build();
    }

    @Override
    public void abortPut(ObjectPartKey partKey) {
        try {
            abortPutThrows(partKey);
        } catch ( RuntimeException ex) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    private void abortPutThrows(ObjectPartKey partKey) throws IOException {
        Path uploadDir = getUploadDir(partKey);
        deleteDir(uploadDir);
    }

    @Override
    public void completePut(ObjectPartKey partKey, List<ObjectPartId> partIds) {
        try {
            completePutThrows(partKey, partIds);
        } catch ( RuntimeException ex) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    private void completePutThrows(ObjectPartKey partKey, List<ObjectPartId> partIds) throws IOException {
        Path uploadDir = getUploadDir(partKey);

        Path bucketRoot = Paths.get(_bucketsRoot.toString(), partKey.getBucket());
        if(!Files.exists(bucketRoot))
            throw(new EntityNotFoundException("Bucket "+partKey.getBucket()+" does not exist"));
        Path objFile = Paths.get(bucketRoot.toString(), toKeyId(partKey.getKey()));
        Path parentDir = objFile.getParent();
        if(parentDir != null && !Files.exists(parentDir))
            parentDir.toFile().mkdirs();
        try ( OutputStream out = Files.newOutputStream(objFile) ) {
            for ( ObjectPartId partId : partIds ) {
                Path partPath = Paths.get(
                    uploadDir.toString(),
                    String.format("%s.part%04d", partId.getPartId(), partId.getPartNum()));
                Files.copy(partPath, out);
            }
        }
        // Delete the uploadDir:
        deleteDir(uploadDir);
    }

    private void deleteDir(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
                    if ( null != ex ) Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    private Path getUploadDir(ObjectPartKey partKey) throws IOException {
        // Validation:
        String uploadId = partKey.getUploadId();
        if ( null == uploadId || uploadId.startsWith(".") || uploadId.contains("/") ) {
            throw new IllegalArgumentException("Invalid partKey.uploadId");
        }
        Path uploadDir = Paths.get(_partsRoot.toString(), uploadId);
        Path uploadDirMeta = Paths.get(_partsRoot.toString(), uploadId, ".KEY");
        if ( ! Files.exists(uploadDirMeta) ) {
            throw new EntityNotFoundException("NotFound: "+partKey+" partsRoot="+_partsRoot);
        }
        String bucketKey = new String(Files.readAllBytes(uploadDirMeta), UTF_8);
        String expectBucketKey = partKey.getBucket()+"/"+partKey.getKey();
        if ( ! bucketKey.equals(expectBucketKey) ) {
            throw new EntityNotFoundException(
                "NotFound: expected bucketKey="+expectBucketKey+" partKey="+partKey+" partsRoot="+_partsRoot);
        }
        return uploadDir;
    }

    private void validate(ObjectKey objectKey) {
        if ( ! validFileName(objectKey.getBucket()) ) {
            throw new IllegalArgumentException("objectKey.bucket is invalid "+objectKey);
        }
        if ( ! validCanonicalRelativePath(objectKey.getKey()) ) {
            throw new IllegalArgumentException("objectKey.key is invalid "+objectKey);
        }
    }

    private static boolean validFileName(String name) {
        if ( null == name || name.isEmpty() ) return false;
        if ( ".".equals(name) ) return false;
        if ( "..".equals(name) ) return false;
        if ( name.contains("/") ) return false;
        if ( name.contains(File.separator) ) return false;
        return true;
    }

    private static boolean validCanonicalRelativePath(String name) {
        if ( null == name || name.isEmpty() ) return false;
        for ( String part : name.split("/") ) {
            if ( ! validFileName(part) ) return false;
        }
        return true;
    }

    /**
     * NOTE: This method does NOT check for symlink loops, therefore it could go into infinite recursion!
     *
     * @param root is the place to begin the walk.
     *
     * @param startsWith if non-null, the first files must begin with this string.
     *
     * @param depth is how many directories deep we have walked. Initially this should be zero.
     *
     * @param afterFile is the path relative to the root of where to begin the
     *     file walking.
     *
     * @param visitor is called when visiting each file. The walk is stopped if false is returned.
     *
     * @return false to indicate the walk was prematurely terminated by visitor returning false.
     */
    private static boolean walk(File root, String startsWith, int depth, File afterFile, Function<File, Boolean> visitor) {
        File[] list;
        String afterFileName = null;
        if ( null != afterFile ) {
            int afterFileDepth = 0;
            for ( File cur=afterFile; cur != null; cur = cur.getParentFile() ) {
                afterFileDepth++;
            }
            afterFileDepth -= depth;
            if ( afterFileDepth > 1 ) {
                File afterFileForDepth = afterFile;
                while ( --afterFileDepth > 1 ) {
                    afterFileForDepth = afterFileForDepth.getParentFile();
                }
                afterFileName = afterFileForDepth.getName();
            }
        }
        if ( null != afterFileName || null != startsWith ) {
            final String finalFileName = afterFileName;
            list = root.listFiles((dir, name) -> {
                    return
                    ( null == finalFileName || name.compareTo(finalFileName) >= 0 ) &&
                    ( null == startsWith || name.startsWith(startsWith) );
                });
        } else {
            list = root.listFiles();
        }
        if ( null == list ) return true;
        Arrays.sort(list, (file1, file2) -> file1.getName().compareTo(file2.getName()));

        for ( File file : list ) {
            if ( file.isDirectory() ) {
                if ( ! walk(new File(root, file.getName()), null, depth+1, afterFile, visitor) ) {
                    return false;
                }
            } else if ( Boolean.FALSE == visitor.apply(file) ) {
                return false;
            }
        }
        return true;
    }
    
    // TODO: Use Path.relativize():
    // https://docs.oracle.com/javase/8/docs/api/java/nio/file/Path.html#relativize-java.nio.file.Path-
    private ObjectKey toObjectkey(File file)
    {
        if(file.isDirectory())
            throw(new IllegalArgumentException("Invalid file: "+file));
        String rootPath = _bucketsRoot.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        Path objectKeyPath = Paths.get(filePath.substring(rootPath.length()));
        String bucket = objectKeyPath.iterator().next().toString();
        //add +2 to the substring to account for the 2 slashes before and after the bucket name
        String key = objectKeyPath.toString().substring(bucket.length()+2);
        if(bucket.startsWith("/"))
            bucket = bucket.substring(1);
        if(key.startsWith("/"))
            key = key.substring(1);
        if(key.endsWith(KEY_POSTFIX))
            key = key.substring(0, key.length() - KEY_POSTFIX.length());
        return ObjectKey
        .builder()
        .bucket(bucket)
        .key(key)
        .build();
    }

    /**
       A Key Id is the canonical Id of the key as stored on disk. It
       is the name of the actual file on disk and is generated by
       appending the KEY_POSTFIX to the key name.

       If the key ends with a trailing slash then the trailing slash
       is removed before the KEY_POSTFIX is appended

       if key is null then a NullPointerException is thrown
    */
    private String toKeyId(String key)
    {
        key = key.trim();
        if(key.endsWith("/"))
            key = key.substring(0, key.length() -1);
        if(key.isEmpty())
            throw(new IllegalArgumentException("Invalid key: "+key));
        return String.format("%s%s", key, KEY_POSTFIX);
    }
}
