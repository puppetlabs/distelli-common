package com.distelli.objectStore.impl.artifactory;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonArray;
import javax.json.JsonReader;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.google.inject.assistedinject.Assisted;
import com.distelli.persistence.PageIterator;
import javax.inject.Inject;
import com.distelli.utils.ResettableInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ArtifactoryObjectStore extends AbstractObjectStore
{
    private static MediaType DEFAULT_BINARY =
        MediaType.parse("application/octet-stream");
    private static MediaType APPLICATION_JSON =
        MediaType.parse("application/json; charset=utf-8");

    public interface Factory {
        public ArtifactoryObjectStore create(ObjectStoreBuilder builder);
    }

    private OkHttpClient _client;
    private URI _endpoint;

    private HttpUrl.Builder url() {
        return HttpUrl.parse(_endpoint.toString())
            .newBuilder();
    }

    private void handleErrors(Response res, ObjectKey objectKey) throws IOException {
        int code = res.code();
        if ( code/100 <= 2 ) return;
        switch ( code ) {
        case 404: throw new EntityNotFoundException(
            "NotFound: "+objectKey+" endpoint="+_endpoint);
        case 401: case 403:
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
            handleErrors(res, ObjectKey.builder().bucket(bucketName).build());
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
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
                handleErrors(res, ObjectKey.builder().bucket(bucketName).build());
            }
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    private static InputStream toResettableInputStream(InputStream in) {
        if ( in instanceof ResettableInputStream ) return in;
        try {
            return new ResettableInputStream(in);
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
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
                 .addPathSegment(objectKey.getKey())
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            handleErrors(res, objectKey);
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
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
            handleErrors(res, objectKey);

            JsonObject obj;
            try ( JsonReader reader = Json.createReader(
                      new ByteArrayInputStream(res.body().bytes())) )
            {
                obj = reader.readObject();
            }

            System.err.println("Got obj="+obj+" size="+getLong(obj, "size"));

            return ObjectMetadata.builder()
                .contentLength(getLong(obj, "size"))
                .key(objectKey.getKey())
                .bucket(objectKey.getBucket())
                .build();
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
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
            handleErrors(res, objectKey);
            ObjectMetadata meta = ObjectMetadata.builder()
                .contentLength(res.body().contentLength())
                .key(objectKey.getKey())
                .bucket(objectKey.getBucket())
                .build();
            return objectReader.read(meta, res.body().byteStream());
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    private static String stripEndSlash(String key) {
        if ( key.endsWith("/") ) return key.substring(0, key.length()-1);
        return key;
    }

    @Override
    public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator) {
        String base = stripEndSlash(objectKey.getKey());
        Request req = new Request.Builder()
            .get()
            .url(url()
                 .addPathSegments("artifactory/api/storage")
                 .addPathSegment(objectKey.getBucket())
                 .addPathSegment(base)
                 .build())
            .build();
        try ( Response res = _client.newCall(req).execute() ) {
            handleErrors(res, objectKey);
            JsonObject obj;
            try ( JsonReader reader = Json.createReader(
                      new ByteArrayInputStream(res.body().bytes())) )
            {
                obj = reader.readObject();
            }
            // TODO: How to support pagination!?
            iterator.setMarker(null);

            List<ObjectKey> result = new ArrayList<>();
            JsonArray children = getArray(obj, "children");
            if ( null == children ) return Collections.emptyList();
            for ( int i=0; i < children.size(); i++ ) {
                JsonObject info = getObject(children, i);
                if ( null == info ) continue;
                String uri = getString(info, "uri");
                if ( null == uri ) continue;
                result.add(ObjectKey.builder()
                           .key(base + uri)
                           .bucket(objectKey.getBucket())
                           .build());
            }
            return Collections.unmodifiableList(result);
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    private static Long getLong(JsonObject obj, String field) {
        if ( null == obj ) return null;
        JsonNumber num;
        try {
            num = obj.getJsonNumber(field);
        } catch ( ClassCastException ex ) {
            String str = obj.getString(field);
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
        if ( null == obj ) return null;
        try {
            return obj.getString(field);
        } catch ( ClassCastException ex ) {
            return null;
        }
    }

    private static JsonObject getObject(JsonArray arr, int idx) {
        if ( null == arr ) return null;
        try {
            return arr.getJsonObject(idx);
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
                handleErrors(res, objectKey);
            }
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit)
        throws EntityNotFoundException
    {
        return url()
            .addPathSegment("artifactory")
            .addPathSegment(objectKey.getBucket())
            .addPathSegment(objectKey.getKey())
            .build()
            .uri();
    }

    @Override
    public ObjectPartKey newMultipartPut(ObjectKey objectKey) {
        throw new UnsupportedOperationException();
    }

    private ObjectPartKey newMultipartPutThrows(ObjectKey objectKey) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortPut(ObjectPartKey partKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void completePut(ObjectPartKey partKey, List<ObjectPartId> partIds) {
        throw new UnsupportedOperationException();
    }
}
