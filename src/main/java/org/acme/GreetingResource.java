package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.MemorySize;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.engine.jdbc.BlobProxy;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/hello")
public class GreetingResource {

    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    MemorySize maxPostValue;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public String hello(@MultipartForm MultipartFormDataInput input) {

        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        List<String> fileNames = new ArrayList<>();
        for (String key : uploadForm.keySet()) {
            List<InputPart> inputParts = uploadForm.get(key);
            Log.infof("inputParts size: %s", inputParts.size());
            String fileName = null;
            for (InputPart inputPart : inputParts) {
                try {

                    MultivaluedMap<String, String> header = inputPart.getHeaders();

                    fileName = inputPart.getFileName();
                    fileNames.add(fileName);

                    Log.infof("File Name: %s", fileName);
                    InputStream inputStream = inputPart.getBody(InputStream.class, null);
                    MyEntity myEntity = new MyEntity();

                    // Configure the proxy to read up to the max body post size. The proxy behaves well if the input
                    // stream size is less than that size
                    myEntity.blob = BlobProxy.generateProxy(inputStream, maxPostValue.asLongValue());
                    myEntity.persist();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return "ok";
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public Response getblob(@PathParam("id") int id) throws Exception {
        MyEntity entity = MyEntity.findById(id);
        /**
         * Since we need to load all the data of the blob in a transaction, we write it to a temp file first, then
         * stream the content of the file in the response. Finally we delete the temp file for cleanup
         *
         * Strangely, we cannot stream the blob's input stream to the outputstream since the transaction is dead at this
         * point and the streaming from db to quarkus is dead. We instead have to save it into a temp file as an
         * intermediate
         */
        java.nio.file.Path path = Files.createTempFile("temp-upload-file", "out");
        FileOutputStream outputStream = new FileOutputStream(path.toFile());

        // copy the data of the blob to the temp file
        entity.blob.getBinaryStream().transferTo(outputStream);

        return Response.ok().entity((StreamingOutput) output -> {
            try {
                Files.copy(path, output);
            } finally {
                Files.delete(path);
            }
        }).build();
    }

    @GET
    @Path("/md5/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public String getMd5blob(@PathParam("id") int id) throws Exception {
        MyEntity entity = MyEntity.findById(id);
        return DigestUtils.md5Hex(entity.blob.getBinaryStream());
    }
}
