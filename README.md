# Proof of Concept to upload / download files in Quarkus

This is a proof of concept to show that we can upload / download *huge*
files in Quarkus and storing it in a database without causing any
OutOfMemoryException. This is done by using InputStream and OutputStream.

## Why?
It's easy to upload huge files into Quarkus and blowing out the memory as
the whole content of the file is loaded into memory. Using streams avoid
this problem.

## Database
To be able to stream data to the database, we need to use the `Blob` datatype. This is translated into a Postgres large object.

## Run
We intentionally set the max heap size to 200MB to trigger an OutOfMemoryException if we accidentally load the whole content into memory.
```
$ mvn clean install -DskipTests=true

#### Options

# Prod: assumes postgres is running with db help created
$ java -jar -Xmx200m -jar target/upload-1.0.0-SNAPSHOT-runner.jar

# Devel: run for quick and dirty, needs podman/docker to be setup
$ quarkus dev
```

Let's generate some files to upload.
```
# 10MB file
$ base64 /dev/urandom | head -c 10000000 > file-10m.txt
# 100MB file
$ base64 /dev/urandom | head -c 100000000 > file-100m.txt
# 1GB file
$ base64 /dev/urandom | head -c 1000000000 > file-1000m.txt
```

Let's upload!
```
$ curl -v -F file=@file-10m.txt http://localhost:8080/hello
$ curl -v -F file=@file-100m.txt http://localhost:8080/hello
$ curl -v -F file=@file-1000m.txt http://localhost:8080/hello
```

Let's now read the file:
```
$ curl http://localhost:8080/hello/1 -o test
$ curl http://localhost:8080/hello/2 -o test
$ curl http://localhost:8080/hello/3 -o test
```

Let's get the md5 of the file:
```
# Compare with the md5 of file-10m.txt
$ curl http://localhost:8080/hello/md5/1

# Compare with the md5 of file-100m.txt
$ curl http://localhost:8080/hello/md5/2

# Compare with the md5 of file-1000m.txt
$ curl http://localhost:8080/hello/md5/3
```

There's no OutOfMemoryException! :)

# Caveats
1. For some weird reason, I cannot increase the max-body-size to higher than
   2GB. We use that value to determine how much data the `BlobProxy` should
   theoretically read, but `BlobProxy` doesn't like it when the value is more
   than 2GB (in bytes), even though it accepts a long. It's strange.
2. We'll have to run `vacuumlo` periodically in the database to remove the
   leftover blob/big objects whenever we delete the entities that references to
   the oid.
