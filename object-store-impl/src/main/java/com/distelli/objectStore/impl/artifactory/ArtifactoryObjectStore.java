package com.distelli.objectStore.impl.artifactory;

import com.distelli.utils.CompactUUID;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.AbstractObjectStore;
import com.distelli.objectStore.impl.ObjectStoreBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.ConnectionPool;
import okio.Okio;
import okio.BufferedSink;
import com.distelli.cred.CredProvider;
import java.net.URI;
import java.security.AccessControlException;
import javax.persistence.EntityNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.google.inject.assistedinject.Assisted;
import com.distelli.persistence.PageIterator;
import javax.inject.Inject;
import com.distelli.utils.ResettableInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import static java.nio.charset.StandardCharsets.UTF_8;
import static com.distelli.utils.IsEmpty.isEmpty;
import static com.distelli.utils.LongSortKey.longToSortKey;

public class ArtifactoryObjectStore extends AbstractObjectStore
{
    private static MediaType DEFAULT_BINARY =
        MediaType.parse("application/octet-stream");
    private static MediaType APPLICATION_JSON =
        MediaType.parse("application/json; charset=utf-8");
    private static MediaType TEXT_PLAIN =
        MediaType.parse("text/plain; charset=utf-8");

    public interface Factory {
        public ArtifactoryObjectStore create(ObjectStoreBuilder builder);
    }

    private OkHttpClient _client;
    private URI _endpoint;
    @Inject
    private ExecutorService _executor;

    private HttpUrl.Builder url() {
        return HttpUrl.parse(_endpoint.toString())
            .newBuilder();
    }

    private void checkBucket(ObjectKey objectKey) {
        boolean exists = true;
        try {
            exists = null != head(ObjectKey.builder().bucket(objectKey.getBucket()).key(".").build());
        } catch ( Exception ex ) {}
        if ( ! exists ) {
            throw new EntityNotFoundException(
                "NotFound: "+objectKey+" endpoint="+_endpoint);
        }
    }

    private void handleErrors(Response res, ObjectKey objectKey, boolean check403) throws IOException {
        int code = res.code();
        if ( code/100 <= 2 ) return;
        switch ( code ) {
        case 404: throw new EntityNotFoundException(
            "NotFound: "+objectKey+" endpoint="+_endpoint);
        case 401:
            throw new AccessControlException(
                "Access denied to "+objectKey+" endpoint="+_endpoint);
        case 403:
            if ( check403 ) {
                checkBucket(objectKey);
            }
            throw new AccessControlException(
                "Access denied to "+objectKey+" endpoint="+_endpoint);
        }
        if ( code/100 == 4 ) {
            throw new IllegalStateException(
                "Unexpected client error '"+code+"' returned from "+res.request().url()+
                " "+res.body().string());
        }
        // TODO: Come up with better exceptions so this can be retried...
        throw new RuntimeException(
            "Unexpected server error '"+code+"' returned from "+res.request().url()+
            " "+res.body().string());
    }

    @Inject
    public ArtifactoryObjectStore(@Assisted ObjectStoreBuilder builder, ConnectionPool pool)
    {
        if ( ObjectStoreType.ARTIFACTORY != builder.getObjectStoreProvider() ) {
            throw new IllegalArgumentException(
                "Expected ARTIFACTORY store provider, got "+builder.getObjectStoreProvider());
        }
        _endpoint = builder.getEndpoint();
        CredProvider credProvider = builder.getCredProvider();
        _client = new OkHttpClient.Builder()
            .addInterceptor((chain) -> {
                    Request req = chain.request();
                    if ( null != credProvider &&
                         null != credProvider.getCredPair() &&
                         null != credProvider.getCredPair().getSecret() )
                    {
                        req = req.newBuilder()
                            .header("X-JFrog-Art-Api", credProvider.getCredPair().getSecret())
                            .build();
                    }
                    return chain.proceed(req);
                })
            .connectionPool(pool)
            .build();
    }

    @Override
    public void createBucket(String bucketName) {
        byte[] content = Json.createObjectBuilder()
            .add("type", "localRepoConfig")
            .add("general", Json.createObjectBuilder()
                 .add("repoKey", bucketName))
            .add("basic", Json.createObjectBuilder()
                 .add("layout", "simple-default"))
            .add("advanced", Json.createObjectBuilder())
            .add("typeSpecific", Json.createObjectBuilder()
                 .add("repoType", "Generic"))
            .build()
            .toString()
            .getBytes(UTF_8);
        Request req = new Request.Builder()
            .post(RequestBody.create(APPLICATION_JSON, content))
            .url(url().addPathSegments("artifactory/api/admin/repositories")
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            // Bucket already exists, assume:
            //    {"error":"Repository distelli-unit-test already exists"}
            if ( 400 == res.code() ) return;
            handleErrors(res, ObjectKey.builder().bucket(bucketName).build(), false);
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws AccessControlException {
        Request req = new Request.Builder()
            .delete()
            .url(url()
                 .addPathSegments("artifactory/api/admin/repositories")
                 .addPathSegment(bucketName)
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            if ( 404 != res.code() ) {
                handleErrors(res, ObjectKey.builder().bucket(bucketName).build(), false);
            }
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    private static InputStream toResettableInputStream(InputStream in) {
        if ( in instanceof ResettableInputStream ) return in;
        try {
            return new ResettableInputStream(in);
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void put(ObjectKey objectKey, long contentLength, InputStream in) {
        InputStream resettableIn = toResettableInputStream(in);
        in.mark(-1);
        Request req = new Request.Builder()
            .put(new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return DEFAULT_BINARY;
                    }
                    @Override
                    public long contentLength() {
                        return contentLength;
                    }
                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        resettableIn.reset();
                        sink.writeAll(Okio.source(resettableIn));
                    }
                })
            .url(url()
                 .addPathSegment("artifactory")
                 .addPathSegment(objectKey.getBucket())
                 .addPathSegments(objectKey.getKey())
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            handleErrors(res, objectKey, true);
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    // Returns null if entity does not exist.
    @Override
    public ObjectMetadata head(ObjectKey objectKey) {
        Request req = new Request.Builder()
            .get()
            .url(url()
                 .addPathSegments("artifactory/api/storage")
                 .addPathSegment(objectKey.getBucket())
                 .addPathSegment(objectKey.getKey())
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            if ( 404 == res.code() ) return null;
            handleErrors(res, objectKey, false);

            JsonObject obj;
            try ( JsonReader reader = Json.createReader(
                      new ByteArrayInputStream(res.body().bytes())) )
            {
                obj = reader.readObject();
            }

            return ObjectMetadata.builder()
                .contentLength(getLong(obj, "size"))
                .key(objectKey.getKey())
                .bucket(objectKey.getBucket())
                .build();
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader, Long start, Long end)
        throws EntityNotFoundException, IOException
    {
        Request.Builder reqBuilder = new Request.Builder()
            .get()
            .url(url()
                 .addPathSegment("artifactory")
                 .addPathSegment(objectKey.getBucket())
                 .addPathSegment(objectKey.getKey())
                 .build());
        if ( null != start ) {
            if ( null != end ) {
                reqBuilder.header("Range", "bytes="+start+"-"+end);
            } else {
                reqBuilder.header("Range", "bytes="+start+"-");
            }
        }
        try ( Response res = _client.newCall(reqBuilder.build()).execute() ) {
            handleErrors(res, objectKey, false);
            ObjectMetadata meta = ObjectMetadata.builder()
                .contentLength(res.body().contentLength())
                .key(objectKey.getKey())
                .bucket(objectKey.getBucket())
                .build();
            return objectReader.read(meta, res.body().byteStream());
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    private Long parseLong(String str) {
        if ( null == str ) return null;
        try {
            return Long.parseLong(str);
        } catch ( IllegalArgumentException ex ) {
            return null;
        }
    }

    @Override
    public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator) {
        ByteArrayOutputStream bodyOS = new ByteArrayOutputStream();
        Long offset = parseLong(iterator.getMarker());
        try {
            bodyOS.write("items.find(".getBytes(UTF_8));
            try ( JsonWriter writer = Json.createWriter(bodyOS) ) {
                int slash = objectKey.getKey().lastIndexOf('/');
                JsonObject content = Json.createObjectBuilder()
                    .add("repo", Json.createObjectBuilder()
                         .add("$eq", objectKey.getBucket()))
                    .add("$or", Json.createArrayBuilder()
                         .add(Json.createObjectBuilder()
                              // Full key:
                              .add("path", Json.createObjectBuilder()
                                   .add("$match", objectKey.getKey() + "*")))
                         .add(Json.createObjectBuilder()
                              .add("$and", Json.createArrayBuilder()
                                   .add(Json.createObjectBuilder()
                                        // Key + name:
                                        .add("path", Json.createObjectBuilder()
                                             .add("$eq", slash > 0 ? objectKey.getKey().substring(0, slash) : "."))
                                        .add("name", Json.createObjectBuilder()
                                             .add("$match",
                                                  (slash > 0
                                                   ? objectKey.getKey().substring(slash+1)
                                                   : objectKey.getKey()) + "*"))))))
                    .build();
                writer.writeObject(content);
            }
            bodyOS.write(").include(\"repo\",\"path\",\"name\")"
                         .getBytes(UTF_8));
            // Not supported in OS edition of Artifactory:
            // ".sort({\"$asc\":[\"path\", \"name\"]})"
            if ( null != offset ) {
                bodyOS.write((".offset(" + offset + ")"
                                 ).getBytes(UTF_8));
            }
            bodyOS.write((".limit("+ iterator.getPageSize()+")"
                             ).getBytes(UTF_8));
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
        Request req = new Request.Builder()
            .post(RequestBody.create(TEXT_PLAIN, bodyOS.toByteArray()))
            .url(url()
                 .addPathSegments("artifactory/api/search/aql")
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            handleErrors(res, objectKey, false);
            JsonObject obj;
            try ( JsonReader reader = Json.createReader(
                      new ByteArrayInputStream(res.body().bytes())) )
            {
                obj = reader.readObject();
            }
            JsonArray results = getArray(obj, "results");
            List<ObjectKey> result = ( null == results ) ?
                Collections.emptyList() :
                new ArrayList<>();
            for ( int i=0; i < results.size(); i++ ) {
                JsonObject info = getObject(results, i);
                if ( null == info ) continue;
                String path = getString(info, "path");
                String name = getString(info, "name");
                if ( null == name ) continue;
                result.add(ObjectKey.builder()
                           .key(isEmpty(path) || ".".equals(path) ? name : path + "/" + name)
                           .bucket(objectKey.getBucket())
                           .build());
            }

            long total = getLong(getObject(obj, "range"), "total");
            if ( total >= iterator.getPageSize() ) {
                if ( null != offset ) {
                    total += offset.longValue();
                }
                iterator.setMarker(""+total);
            } else {
                iterator.setMarker(null);
            }

            if ( result.isEmpty() ) {
                checkBucket(ObjectKey.builder().bucket(objectKey.getBucket()).build());
            }

            return Collections.unmodifiableList(result);
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Long getLong(JsonObject obj, String field) {
        if ( null == obj || ! obj.containsKey(field) ) return null;
        JsonNumber num;
        try {
            num = obj.getJsonNumber(field);
        } catch ( ClassCastException ex ) {
            String str = getString(obj, field);
            if ( null == str ) return null;
            try {
                return Long.parseLong(str);
            } catch ( NumberFormatException innerEx ) {
                return null;
            }
        }
        if ( null == num ) return null;
        return num.longValue();
    }

    private static String getString(JsonObject obj, String field) {
        if ( null == obj || ! obj.containsKey(field) ) return null;
        try {
            return obj.getString(field);
        } catch ( ClassCastException ex ) {
            return null;
        }
    }

    private static String getString(JsonArray arr, int idx) {
        if ( null == arr ) return null;
        try {
            return arr.getString(idx);
        } catch ( IndexOutOfBoundsException|ClassCastException ex ) {
            return null;
        }
    }

    private static JsonObject getObject(JsonArray arr, int idx) {
        if ( null == arr ) return null;
        try {
            return arr.getJsonObject(idx);
        } catch ( IndexOutOfBoundsException|ClassCastException ex ) {
            return null;
        }
    }

    private static JsonObject getObject(JsonObject obj, String field) {
        if ( null == obj ) return null;
        try {
            return obj.getJsonObject(field);
        } catch ( ClassCastException ex ) {
            return null;
        }
    }

    private static JsonArray getArray(JsonObject obj, String field) {
        if ( null == obj ) return null;
        try {
            return obj.getJsonArray(field);
        } catch ( ClassCastException ex ) {
            return null;
        }
    }

    @Override
    public void delete(ObjectKey objectKey)
        throws EntityNotFoundException
    {
        Request req = new Request.Builder()
            .delete()
            .url(url()
                 .addPathSegment("artifactory")
                 .addPathSegment(objectKey.getBucket())
                 .addPathSegment(objectKey.getKey())
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            if ( 404 != res.code() ) {
                handleErrors(res, objectKey, true);
            }
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit)
        throws EntityNotFoundException
    {
        // TODO: Use "Create Token" API, generate a URL with the
        // username & password of the URI set.
        return url()
            .addPathSegment("artifactory")
            .addPathSegment(objectKey.getBucket())
            .addPathSegment(objectKey.getKey())
            .build()
            .uri();
    }

    @Override
    public ObjectPartKey newMultipartPut(ObjectKey objectKey) {
        ObjectPartKey partKey = ObjectPartKey.builder()
            .bucket(objectKey.getBucket())
            .key(objectKey.getKey())
            .uploadId(CompactUUID.randomUUID()+"")
            .build();

        ObjectKey dirKey = getMultipartKey(partKey, null);
        put(ObjectKey.builder()
            .bucket(dirKey.getBucket())
            .key(dirKey.getKey())
            .build(),
            objectKey.getKey().getBytes(UTF_8));
        return partKey;
    }

    private ObjectKey getMultipartKey(ObjectPartKey partKey, Integer partNum) {
        String suffix = ( null == partNum ) ?
            ".KEY" : String.format("%03d.part", partNum);
        return ObjectKey.builder()
            .bucket(partKey.getBucket())
            .key(".MULTIPARTPUT/"+partKey.getUploadId() + suffix)
            .build();
    }

    private void validate(ObjectPartKey partKey) {
        ObjectKey dirKey = getMultipartKey(partKey, null);
        byte[] content;
        try {
            content = get(dirKey);
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
        String key = new String(content, UTF_8);
        if ( ! key.equals(partKey.getKey()) ) {
            throw new EntityNotFoundException(
                "NotFound: expected partKey="+key+", but got="+partKey.getKey());
        }
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in) {
        validate(partKey);
        put(getMultipartKey(partKey, partNum),
            contentLength,
            in);
        return ObjectPartId.builder()
            .partNum(partNum)
            .build();
    }

    @Override
    public void abortPut(ObjectPartKey partKey) {
        try {
            validate(partKey);
        } catch ( EntityNotFoundException ex ) {
            return;
        }
        delete(getMultipartKey(partKey, null));
    }

    @Override
    public void completePut(ObjectPartKey partKey, List<ObjectPartId> partIds) {
        validate(partKey);

        // [1] calculate the content length (and validate part ids):
        long contentLength = 0;
        for ( ObjectPartId partId : partIds ) {
            if ( null == partId.getPartNum() ) {
                throw new IllegalArgumentException(
                    "Null partNum is not allowed in completePut("+partKey+")");
            }
            ObjectMetadata meta = head(getMultipartKey(partKey, partId.getPartNum()));
            contentLength += meta.getContentLength();
        }

        ObjectKey objectKey = ObjectKey.builder()
            .bucket(partKey.getBucket())
            .key(partKey.getKey())
            .build();

        // [2] Open upload stream:
        try ( PipedInputStream in = new PipedInputStream();
              PipedOutputStream out = new PipedOutputStream() )
        {
            in.connect(out);

            long finalContentLength = contentLength;
            Future<?> future = _executor.submit(() -> put(objectKey, finalContentLength, in));

            for ( ObjectPartId partId : partIds ) {
                get(getMultipartKey(partKey, partId.getPartNum()),
                    (meta, is) -> transferTo(is, out));
            }
            out.close();
            try {
                future.get();
            } catch ( InterruptedException|ExecutionException ex ) {
                // TODO: Handle these differently?
                throw new RuntimeException(ex);
            }
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }

        // [3] Iterate over the parts and push to the upload stream.
        abortPut(partKey);
    }

    private long transferTo(InputStream is, OutputStream out) throws IOException {
        long total = 0L;
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
            if ( Thread.interrupted() ) {
                InterruptedIOException ex = new InterruptedIOException();
                ex.bytesTransferred = (int)total;
                throw ex;
            }
        }
        return total;
    }
}
