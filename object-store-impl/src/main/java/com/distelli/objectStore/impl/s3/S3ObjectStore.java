package com.distelli.objectStore.impl.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.distelli.aws.AWSCredentialsProviderFactory;
import com.distelli.aws.AmazonWebServiceClients;
import com.distelli.aws.ClientConfigurations;
import com.distelli.cred.CredProvider;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.AbstractObjectStore;
import com.distelli.objectStore.impl.ObjectStoreBuilder;
import com.distelli.objectStore.impl.ResettableInputStream;
import com.distelli.persistence.PageIterator;
import com.google.inject.assistedinject.Assisted;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.AccessControlException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import static javax.xml.bind.DatatypeConverter.parseHexBinary;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class S3ObjectStore extends AbstractObjectStore {
    private AmazonS3 amazonS3 = null;
    private boolean serverSideEncryption = false;
    private URI endpoint = null;

    public static interface Factory {
        public S3ObjectStore create(ObjectStoreBuilder builder);
    }

    @Inject
    protected S3ObjectStore(@Assisted ObjectStoreBuilder builder,
                            AWSCredentialsProviderFactory credProviderFactory,
                            ClientConfigurations clientConfigs,
                            AmazonWebServiceClients amazonClients)
    {
        endpoint = builder.getEndpoint();
        if ( null == builder.getCredProvider() ) {
            throw new NullPointerException("null CredProvider");
        }
        Boolean serverSideEncryption = builder.getServerSideEncryption();
        Boolean forceV4Signature = builder.getForceV4Signature();
        ClientConfiguration config = new ClientConfiguration();
        clientConfigs.withProxy(config, builder.getProxy());
        if ( null != forceV4Signature && forceV4Signature ) {
            config.setSignerOverride("AWSS3V4SignerType");
        }
        if ( null != serverSideEncryption && serverSideEncryption ) {
            this.serverSideEncryption = true;
        }
        amazonS3 = amazonClients.withEndpoint(
            new AmazonS3Client(
                credProviderFactory.create(builder.getCredProvider()),
                config),
            builder.getEndpoint());
    }

    @Override
    public void createBucket(String bucketName) throws AccessControlException {
        try {
            amazonS3.createBucket(bucketName);
        } catch ( AmazonS3Exception ex ) {
            ObjectKey key = ObjectKey.builder()
                .bucket(bucketName)
                .build();
            handleAmazonS3Exception(ex, key);
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws AccessControlException {
        try {
            amazonS3.deleteBucket(bucketName);
        } catch ( AmazonS3Exception ex ) {
            ObjectKey key = ObjectKey.builder()
                .bucket(bucketName)
                .build();
            handleAmazonS3Exception(ex, key);
        }
    }

    @Override
    public void put(ObjectKey objectKey, long contentLength, InputStream in) {
        com.amazonaws.services.s3.model.ObjectMetadata meta = new com.amazonaws.services.s3.model.ObjectMetadata();
        meta.setContentLength(contentLength);
        if ( serverSideEncryption ) {
            meta.setSSEAlgorithm(com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        }
        try {
            amazonS3.putObject(objectKey.getBucket(), objectKey.getKey(), new ResettableInputStream(in), meta);
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, objectKey);
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public ObjectMetadata head(ObjectKey objectKey) {
        com.amazonaws.services.s3.model.ObjectMetadata meta = null;
        try {
            meta = amazonS3.getObjectMetadata(
                objectKey.getBucket(),
                objectKey.getKey());
        } catch ( AmazonS3Exception ex ) {
            if ( ex.getStatusCode() == 404 ) return null;
            handleAmazonS3Exception(ex, objectKey);
        }
        return ObjectMetadata.builder()
            .bucket(objectKey.getBucket())
            .key(objectKey.getKey())
            .contentLength(meta.getContentLength())
            .build();
    }

    @Override
    public <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader, Long start, Long end)
        throws EntityNotFoundException, IOException
    {
        GetObjectRequest req = new GetObjectRequest(objectKey.getBucket(), objectKey.getKey());
        if ( null != start ) {
            if ( null != end ) {
                req.setRange(start, end);
            } else {
                req.setRange(start);
            }
        }
        S3Object res = null;
        try {
            res = amazonS3.getObject(req);
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, objectKey);
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
        InputStream is = new DigestInputStream(
            new BufferedInputStream(res.getObjectContent(), 1024*1024),
            md);
        byte[] expectMD5 = null;
        try {
            String etag = res.getObjectMetadata().getETag();
            if ( null != etag ) {
                expectMD5 = parseHexBinary(etag);
            }
        } catch ( IllegalArgumentException ex ) {}
        try {
            return objectReader.read(toMetadata(objectKey, res.getObjectMetadata()), is);
        } finally {
            boolean isEof = false;
            try {
                isEof = is.read() < 0;
                is.close();
            } catch ( Exception ex ) {}
            if ( null != expectMD5 && isEof && null == start && null == end ) {
                if ( ! isEqual(expectMD5, md.digest()) ) {
                    throw new StreamCorruptedException(
                        "Check sum failed on get for key: "+objectKey+". Checksum: "+
                        printHexBinary(md.digest())+" Expected: "+printHexBinary(expectMD5));
                }
            }
        }
    }

    @Override
    public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator) {
        if ( ! iterator.isForward() ) {
            throw new UnsupportedOperationException("Only forward iteration is supported");
        }
        ListObjectsV2Request req = new ListObjectsV2Request()
            .withBucketName(objectKey.getBucket())
            .withPrefix(objectKey.getKey())
            .withContinuationToken(iterator.getMarker())
            .withMaxKeys(iterator.getPageSize());
        ListObjectsV2Result res = null;
        try {
            res = amazonS3.listObjectsV2(req);
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, objectKey);
        }
        iterator.setMarker(res.getNextContinuationToken());
        return Collections.unmodifiableList(
            res.getObjectSummaries()
            .stream()
            .map(this::toObjectKey)
            .collect(Collectors.toList()));
    }

    @Override
    public void delete(ObjectKey objectKey) {
        try {
            amazonS3.deleteObject(objectKey.getBucket(), objectKey.getKey());
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, objectKey);
        }
    }

    @Override
    public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit) {
        long millis = unit.toMillis(timeout);
        if (millis <= 1000){
            throw(new IllegalArgumentException("The timeout must be at least 1 second"));
        }

        Date expiryTime = new Date(System.currentTimeMillis() + millis);
        URL url = amazonS3.generatePresignedUrl(
            objectKey.getBucket(),
            objectKey.getKey(),
            expiryTime);
        try {
            return url.toURI();
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public ObjectPartKey newMultipartPut(ObjectKey objectKey) {
        InitiateMultipartUploadRequest req = new InitiateMultipartUploadRequest(
            objectKey.getBucket(), objectKey.getKey());
        if ( serverSideEncryption ) {
            com.amazonaws.services.s3.model.ObjectMetadata meta =
                new com.amazonaws.services.s3.model.ObjectMetadata();
            meta.setSSEAlgorithm(com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            req.setObjectMetadata(meta);
        }
        String uploadId = null;
        try {
            uploadId = amazonS3.initiateMultipartUpload(req).getUploadId();
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, objectKey);
        }
        return ObjectPartKey.builder()
            .key(objectKey.getKey())
            .bucket(objectKey.getBucket())
            .uploadId(uploadId)
            .build();
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in) {
        UploadPartResult result = null;
        try {
            result =
                amazonS3.uploadPart(
                    new UploadPartRequest()
                    .withBucketName(partKey.getBucket())
                    .withInputStream(in)
                    .withKey(partKey.getKey())
                    .withPartNumber(partNum)
                    .withPartSize(contentLength)
                    .withUploadId(partKey.getUploadId()));
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, partKey);
        }
        return ObjectPartId.builder()
            .partNum(partNum)
            .partId(result.getETag())
            .build();
    }

    @Override
    public void abortPut(ObjectPartKey partKey) {
        try {
            amazonS3.abortMultipartUpload(
                new AbortMultipartUploadRequest(
                    partKey.getBucket(),
                    partKey.getKey(),
                    partKey.getUploadId()));
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, partKey);
        }
    }

    @Override
    public void completePut(ObjectPartKey partKey, List<ObjectPartId> partIds) {
        List<PartETag> partETags = new ArrayList<>(partIds.size());
        for ( ObjectPartId partId : partIds ) {
            partETags.add(new PartETag(partId.getPartNum(), partId.getPartId()));
        }
        try {
            amazonS3.completeMultipartUpload(
                new CompleteMultipartUploadRequest()
                .withBucketName(partKey.getBucket())
                .withKey(partKey.getKey())
                .withUploadId(partKey.getUploadId())
                .withPartETags(partETags));
        } catch ( AmazonS3Exception ex ) {
            handleAmazonS3Exception(ex, partKey);
        }
    }

    private void handleAmazonS3Exception(AmazonS3Exception ex, ObjectKey objectKey) {
        switch ( ex.getStatusCode() ) {
        case 404: throw new EntityNotFoundException("NotFound: "+objectKey+" endpoint="+endpoint);
        case 401:
        case 403:
            throw new AccessControlException("Access denied to "+objectKey+" endpoint="+endpoint);
        }
        throw ex;
    }

    private void handleAmazonS3Exception(AmazonS3Exception ex, ObjectPartKey objectPartKey) {
        switch ( ex.getStatusCode() ) {
        case 400:
            if ( "EntityTooSmall".equals(ex.getErrorCode()) ) {
                throw new ChunkToSmallException("ChunkToSmall: "+objectPartKey+" endpoint="+endpoint, ex);
            }
            break;
        case 404: throw new EntityNotFoundException("NotFound: "+objectPartKey+" endpoint="+endpoint);
        case 401:
        case 403:
            throw new AccessControlException("AccessDenied: "+objectPartKey+" endpoint="+endpoint);
        }
        throw ex;
    }


    private ObjectKey toObjectKey(S3ObjectSummary summary) {
        return ObjectKey.builder()
            .bucket(summary.getBucketName())
            .key(summary.getKey())
            .build();
    }

    private ObjectMetadata toMetadata(ObjectKey objectKey, com.amazonaws.services.s3.model.ObjectMetadata meta) {
        return ObjectMetadata.builder()
            .bucket(objectKey.getBucket())
            .key(objectKey.getKey())
            .contentLength(meta.getContentLength())
            .build();
    }

    // See https://codahale.com/a-lesson-in-timing-attacks/
    private static boolean isEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
